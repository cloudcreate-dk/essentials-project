/*
 * Copyright 2021-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.cloudcreate.essentials.components.foundation.postgresql;

import com.fasterxml.jackson.databind.*;
import dk.cloudcreate.essentials.components.foundation.IOExceptionUtil;
import dk.cloudcreate.essentials.components.foundation.json.*;
import dk.cloudcreate.essentials.components.foundation.postgresql.ListenNotify.SqlOperation;
import dk.cloudcreate.essentials.reactive.EventBus;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.jdbi.v3.core.*;
import org.postgresql.*;
import org.postgresql.core.Notification;
import org.slf4j.*;

import java.io.Closeable;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.stream.*;

import static dk.cloudcreate.essentials.components.foundation.postgresql.ListenNotify.resolveTableChangeChannelName;
import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Variant of {@link ListenNotify#listen(Jdbi, String, Duration)} that allows you to listen for notifications from multiple tables using a single polling thread
 *
 * <u>Security</u>
 * It is the responsibility of the user of this component to sanitize any table or column names provided to methods in this class
 * to ensure the security of all the SQL statements generated by this component. The {@link ListenNotify}/{@link MultiTableChangeListener} component will
 * call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table/column names as a first line of defense.<br>
 * The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
 * However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
 * <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
 * Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
 * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
 * It is highly recommended that the {@code tableName} value is only derived from a controlled and trusted source.<br>
 * To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the table/column name values.<br>
 * <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
 * vulnerabilities, compromising the security and integrity of the database.</b>
 */
public final class MultiTableChangeListener<T extends TableChangeNotification> implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(MultiTableChangeListener.class);

    private final Jdbi                                      jdbi;
    private final Duration                                  pollingInterval;
    private final JSONSerializer                            jsonSerializer;
    private final EventBus                                  eventBus;
    /**
     * Key: The table name<br>
     * Value: The {@link TableChangeNotification} subclass that the notification string payload should be mapped to using the {@link #jsonSerializer}
     */
    private final ConcurrentMap<String, Class<? extends T>> listenForNotificationsRelatedToTables;
    private final AtomicReference<Handle>                   handleReference;
    private       ScheduledExecutorService                  executorService;
    private       ScheduledFuture<?>                        scheduledFuture;
    private final boolean                                   filterDuplicateNotifications;
    private final NotificationFilterChain                   notificationFilterChain;

    public MultiTableChangeListener(Jdbi jdbi,
                                    Duration pollingInterval,
                                    JSONSerializer jsonSerializer,
                                    EventBus eventBus,
                                    boolean filterDuplicateNotifications) {
        this.jdbi = requireNonNull(jdbi, "No jdbi provided");
        this.pollingInterval = requireNonNull(pollingInterval, "No pollingInterval provided");
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer provided");
        this.eventBus = requireNonNull(eventBus, "No localEventBus instance provided");
        this.filterDuplicateNotifications = filterDuplicateNotifications;
        if (jsonSerializer instanceof JacksonJSONSerializer jacksonJSONSerializer) {
            this.notificationFilterChain = new NotificationFilterChain(jacksonJSONSerializer.getObjectMapper());
        } else {
            // Fallback
            this.notificationFilterChain = new NotificationFilterChain(new ObjectMapper());
        }
        listenForNotificationsRelatedToTables = new ConcurrentHashMap<>();
        handleReference = new AtomicReference<>();
        executorService = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder.builder()
                                                                                         .nameFormat("MultiTableChangeListener")
                                                                                         .daemon(true)
                                                                                         .build());
        scheduledFuture = executorService.scheduleAtFixedRate(this::pollForNotifications,
                                                              pollingInterval.toMillis(),
                                                              pollingInterval.toMillis(),
                                                              TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        log.info("Closing");
        try {
            scheduledFuture.cancel(true);
            for (String tableName : listenForNotificationsRelatedToTables.keySet()) {
                try {
                    unlisten(tableName);
                } catch (Exception e) {
                    log.error("Failed to unlisten table '{}'", tableName, e);
                }
            }
            scheduledFuture = null;
        } catch (Exception e) {
            // Do nothing
        }
        log.info("Closed");
    }

    public boolean isFilterDuplicateNotifications() {
        return filterDuplicateNotifications;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Removes a custom {@link NotificationDuplicationFilter} from the filter chain.
     *
     * @param filter the filter to be added to the chain
     */
    public void removeDuplicationFilter(NotificationDuplicationFilter filter) {
        notificationFilterChain.removeFilter(filter);
    }

    /**
     * Adds a custom {@link NotificationDuplicationFilter} to the filter chain as the very first (highest priority).<br>
     * The {@link NotificationDuplicationFilter}'s are used to extract unique keys from the {@link Notification#getParameter()}
     * JSON content.<br>
     * The key extracted from {@link NotificationDuplicationFilter#extractDuplicationKey(JsonNode)}
     * will be used inside {@link MultiTableChangeListener} for duplication checks across all {@link Notification}'s
     * returned in one poll.<br>
     * If an empty {@link Optional} is returned then the given notification won't be deduplicated.<br>
     * If two or more {@link Notification}'s in a given poll batch share the same duplication key, <b>AND {@link #isFilterDuplicateNotifications()} is true</b>,
     * then only one of them will be published to the listeners registered with the {@link MultiTableChangeListener}<br>
     * <br>
     * Note: Only a single instance of a particular {@link NotificationDuplicationFilter},
     * determined by calling {@link Object#equals(Object)} on the filters.
     *
     * @param filter the filter to be added to the chain
     * @return true if the filter was added as the first, otherwise false (e.g. the filter was already added)
     */
    public boolean addDuplicationFilterAsFirst(NotificationDuplicationFilter filter) {
        return notificationFilterChain.addFilterAsFirst(filter);
    }

    /**
     * Adds a custom {@link NotificationDuplicationFilter} to the filter chain as the very last (lowest priority).<br>
     * The {@link NotificationDuplicationFilter}'s are used to extract unique keys from the {@link Notification#getParameter()}
     * JSON content.<br>
     * The key extracted from {@link NotificationDuplicationFilter#extractDuplicationKey(JsonNode)}
     * will be used inside {@link MultiTableChangeListener} for duplication checks across all {@link Notification}'s
     * returned in one poll.<br>
     * If an empty {@link Optional} is returned then the given notification won't be deduplicated.<br>
     * If two or more {@link Notification}'s in a given poll batch share the same duplication key, <b>AND {@link #isFilterDuplicateNotifications()} is true</b>,
     * then only one of them will be published to the listeners registered with the {@link MultiTableChangeListener}<br>
     * <br>
     * Note: Only a single instance of a particular {@link NotificationDuplicationFilter},
     * determined by calling {@link Object#equals(Object)} on the filters.
     *
     * @param filter the filter to be added to the chain
     * @return true if the filter was added as the last, otherwise false (e.g. the filter was already added)
     */
    public boolean addDuplicationFilterAsLast(NotificationDuplicationFilter filter) {
        return notificationFilterChain.addFilterAsLast(filter);
    }

    /**
     * Start listening for notifications related to changes to the given table<br>
     * <b>Note: Remember to install the notification support, using {@link ListenNotify#addChangeNotificationTriggerToTable(Handle, String, List, String...)},
     * prior to using this method</b>
     *
     * @param tableName             the name of the table to listen to for {@link TableChangeNotification}'s<br>
     *                              <br>
     *                              <strong>Note:</strong><br>
     *                              The {@code tableName} as well the result of {@link ListenNotify#resolveTableChangeChannelName(String)} will be directly used in constructing SQL statements
     *                              through string concatenation, which exposes the component to SQL injection attacks.<br>
     *                              <br>
     *                              <strong>Security Note:</strong><br>
     *                              It is the responsibility of the user of this component to sanitize the {@code tableName}
     *                              to ensure the security of all the SQL statements generated by this component. The {@link MultiTableChangeListener} component will
     *                              call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
     *                              The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                              However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     *                              <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
     *                              Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     *                              Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     *                              <br>
     *                              It is highly recommended that the {@code tableName} value is only derived from a controlled and trusted source.<br>
     *                              To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code tableName} value.<br>
     *                              <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
     *                              vulnerabilities, compromising the security and integrity of the database.</b>
     * @param tableNotificationType the concrete type of {@link TableChangeNotification} that each {@link SqlOperation} related change will result in
     * @return this listener instance
     */
    public MultiTableChangeListener listenToNotificationsFor(String tableName, Class<? extends T> tableNotificationType) {
        requireNonBlank(tableName, "No tableName was provided");
        requireNonNull(tableNotificationType, "No tableNotificationType was provided");
        PostgresqlUtil.checkIsValidTableOrColumnName(tableName);
        if (listenForNotificationsRelatedToTables.put(tableName, tableNotificationType) == null) {
            listen(tableName);
        }
        return this;
    }

    /**
     * Stop listening for notifications related to changes to the given table
     *
     * @param tableName the name of the table to stop listen for {@link TableChangeNotification}'s to<br>
     *                  <br>
     *                  <strong>Note:</strong><br>
     *                  The {@code tableName} as well the result of {@link ListenNotify#resolveTableChangeChannelName(String)} will be directly used in constructing SQL statements
     *                  through string concatenation, which exposes the component to SQL injection attacks.<br>
     *                  <br>
     *                  <strong>Security Note:</strong><br>
     *                  It is the responsibility of the user of this component to sanitize the {@code tableName}
     *                  to ensure the security of all the SQL statements generated by this component. The {@link MultiTableChangeListener} component will
     *                  call the {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} method to validate the table name as a first line of defense.<br>
     *                  The {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} provides an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     *                  However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     *                  <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.</b><br>
     *                  Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     *                  Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     *                  <br>
     *                  It is highly recommended that the {@code tableName} value is only derived from a controlled and trusted source.<br>
     *                  To mitigate the risk of SQL injection attacks, external or untrusted inputs should never directly provide the {@code tableName} value.<br>
     *                  <b>Failure to adequately sanitize and validate this value could expose the application to SQL injection
     *                  vulnerabilities, compromising the security and integrity of the database.</b>
     * @return this listener instance
     */
    public MultiTableChangeListener unlistenToNotificationsFor(String tableName) {
        requireNonBlank(tableName, "No tableName was provided");
        PostgresqlUtil.checkIsValidTableOrColumnName(tableName);
        if (listenForNotificationsRelatedToTables.remove(tableName) != null) {
            unlisten(tableName);
        }
        return this;
    }

    private void listen(String tableName) {
        requireNonBlank(tableName, "No tableName provided");
        log.info("Setting up Table change LISTENER for '{}'", tableName);
        PostgresqlUtil.checkIsValidTableOrColumnName(tableName);
        try {
            getHandle(null).execute("LISTEN " + resolveTableChangeChannelName(tableName));
        } catch (Exception e) {
            if (IOExceptionUtil.isIOException(e)) {
                log.debug("Failed to add change listener for table '{}'", tableName, e);
            } else {
                throw new RuntimeException(msg("Failed to add change listener for table '{}'", tableName), e);
            }
        }
    }

    private void unlisten(String tableName) {
        requireNonBlank(tableName, "No tableName provided");
        log.info("Removing table change LISTENER for '{}'", tableName);
        PostgresqlUtil.checkIsValidTableOrColumnName(tableName);
        try {
            getHandle(null).execute("UNLISTEN " + resolveTableChangeChannelName(tableName));
        } catch (Exception e) {
            if (IOExceptionUtil.isIOException(e)) {
                log.trace("Failed to unlisten table '{}'", tableName, e);
            } else {
                throw new RuntimeException(msg("Failed to unlisten table '{}'", tableName), e);
            }
        }
    }

    private Stream<PGNotification> filterDuplicateNotifications(PGNotification[] notifications) {
        if (filterDuplicateNotifications && notifications.length > 1 && notificationFilterChain.hasDuplicationFilters()) {
            var duplicationKeys       = new HashSet<String>();
            var filteredNotifications = new ArrayList<PGNotification>();

            for (var notification : notifications) {
                var parameterJson  = notification.getParameter();
                var duplicationKey = notificationFilterChain.extractDuplicationKey(parameterJson);
                if (duplicationKey.isEmpty() || duplicationKeys.add(duplicationKey.get())) {
                    filteredNotifications.add(notification);
                }
            }

            int originalCount = notifications.length;
            int filteredCount = filteredNotifications.size();
            int reducedCount  = originalCount - filteredCount;
            log.debug("Reduced notifications from {} to {} (reduction by {}). UniqueNames: {}", originalCount, filteredCount, reducedCount, duplicationKeys);

            return filteredNotifications.stream();
        }
        return Arrays.stream(notifications);
    }

    private void pollForNotifications() {
        log.trace("Polling for notifications related to {} tables: {}",
                  listenForNotificationsRelatedToTables.size(),
                  listenForNotificationsRelatedToTables.keySet());

        if (listenForNotificationsRelatedToTables.isEmpty()) return;

        Handle handle = null;
        try {
            handle = getHandle(handleCreated -> {
                for (String tableName : listenForNotificationsRelatedToTables.keySet()) {
                    try {
                        listen(tableName);
                    } catch (Exception e) {
                        // Log as error to avoid breaking the scheduler
                        log.error("Failed to add change listener for table '{}' during creation of new Handle", tableName, e);
                    }
                }
            });

            var connection    = handle.getConnection().unwrap(PGConnection.class);
            var notifications = connection.getNotifications();
            if (notifications.length > 0) {
                AtomicLong notificationsPublished = new AtomicLong();
                if (log.isDebugEnabled()) {
                    log.debug("Received '{}' Notification(s) for tables: {}", notifications.length, Arrays.stream(notifications).map(PGNotification::getName).collect(Collectors.toSet()));
                }
                filterDuplicateNotifications(notifications)
                        .map(notification -> {
                            var payloadType = listenForNotificationsRelatedToTables.get(notification.getName());
                            if (payloadType == null) {
                                log.error(msg("Couldn't find a concrete {} type for notifications related to table '{}'",
                                              TableChangeNotification.class.getSimpleName(),
                                              notification.getName()));
                                return null;
                            }
                            try {
                                return jsonSerializer.deserialize(notification.getParameter(), payloadType);
                            } catch (Throwable e) {
                                log.error(msg("Failed to deserialize notification payload '{}' to concrete {} related to table '{}'",
                                              notification.getParameter(),
                                              payloadType.getName(),
                                              notification.getName()),
                                          e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .forEach(event -> {
                            notificationsPublished.getAndIncrement();
                            eventBus.publish(event);
                        });
                log.debug("Finished publishing '{}' Notification(s)", notificationsPublished.get());
            } else {
                log.trace("Didn't receive any Notifications");
            }
        } catch (Exception e) {
            if (IOExceptionUtil.isIOException(e)) {
                log.debug(msg("Failed while polling for notifications"),
                          e);
            } else {
                log.error(msg("Failed while polling for notifications"),
                          e);
            }
            try {
                if (handle != null) {
                    handle.close();
                }
            } catch (Exception ex) {
                if (IOExceptionUtil.isIOException(e)) {
                    log.debug(msg("Failed to close the polling listener Handle"),
                              e);
                } else {
                    log.error(msg("Failed to close the polling listener Handle"),
                              e);
                }
            } finally {
                handleReference.set(null);
            }
        }
    }

    private synchronized Handle getHandle(Consumer<Handle> onHandleCreated) {
        try {
            var handle = handleReference.get();
            if (handle == null) {
                handleReference.set(handle = jdbi.open());
                handle.getConnection().setAutoCommit(true);
                handle.getConnection().setReadOnly(true);
                handle.getConnection().setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                if (onHandleCreated != null) {
                    onHandleCreated.accept(handle);
                }
            }
            return handle;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to acquire a Handle", e);
        }
    }

}
