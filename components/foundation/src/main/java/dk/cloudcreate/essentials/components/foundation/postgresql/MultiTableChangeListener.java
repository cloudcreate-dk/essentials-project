/*
 * Copyright 2021-2023 the original author or authors.
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

import dk.cloudcreate.essentials.components.foundation.json.JSONSerializer;
import dk.cloudcreate.essentials.components.foundation.postgresql.ListenNotify.SqlOperation;
import dk.cloudcreate.essentials.reactive.EventBus;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.jdbi.v3.core.*;
import org.postgresql.PGConnection;
import org.slf4j.*;

import java.io.Closeable;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.components.foundation.postgresql.ListenNotify.resolveTableChangeChannelName;
import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Variant of {@link ListenNotify#listen(Jdbi, String, Duration)} that allows you to listen for notifications from multiple tables using a single polling thread
 */
public class MultiTableChangeListener<T extends TableChangeNotification> implements Closeable {
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

    public MultiTableChangeListener(Jdbi jdbi,
                                    Duration pollingInterval,
                                    JSONSerializer jsonSerializer,
                                    EventBus eventBus) {
        this.jdbi = requireNonNull(jdbi, "No jdbi provided");
        this.pollingInterval = requireNonNull(pollingInterval, "No pollingInterval provided");
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer provided");
        this.eventBus = requireNonNull(eventBus, "No localEventBus instance provided");
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
                unlisten(tableName);
            }
            scheduledFuture = null;
        } catch (Exception e) {
            // Do nothing
        }
        log.info("Closed");
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Start listening for notifications related to changes to the given table<br>
     * <b>Note: Remember to install the notification support, using {@link ListenNotify#addChangeNotificationTriggerToTable(Handle, String, List, String...)},
     * prior to using this method</b>
     *
     * @param tableName             the name of the table to listen to {@link TableChangeNotification}'s
     * @param tableNotificationType the concrete type of {@link TableChangeNotification} that each {@link SqlOperation} related change will result in
     * @return this listener instance
     */
    public MultiTableChangeListener listenToNotificationsFor(String tableName, Class<? extends T> tableNotificationType) {
        requireNonBlank(tableName, "No tableName was provided");
        requireNonNull(tableNotificationType, "No tableNotificationType was provided");
        if (listenForNotificationsRelatedToTables.put(tableName, tableNotificationType) == null) {
            listen(tableName);
        }
        return this;
    }

    /**
     * Stop listening for notifications related to changes to the given table
     *
     * @param tableName the name of the table to stop listen for {@link TableChangeNotification}'s to
     * @return this listener instance
     */
    public MultiTableChangeListener unlistenToNotificationsFor(String tableName) {
        requireNonBlank(tableName, "No tableName was provided");
        if (listenForNotificationsRelatedToTables.remove(tableName) != null) {
            unlisten(tableName);
        }
        return this;
    }

    private void listen(String tableName) {
        requireNonBlank(tableName, "No tableName provided");
        log.info("Setting up Table change LISTENER for '{}'", tableName);
        getHandle(null).execute("LISTEN " + resolveTableChangeChannelName(tableName));
    }

    private void unlisten(String tableName) {
        requireNonBlank(tableName, "No tableName provided");
        log.info("Removing table change LISTENER for '{}'", tableName);
        getHandle(null).execute("UNLISTEN " + resolveTableChangeChannelName(tableName));
    }

    private void pollForNotifications() {
        log.trace("Polling for notifications related to {} tables: {}",
                  listenForNotificationsRelatedToTables.size(),
                  listenForNotificationsRelatedToTables.keySet());

        if (listenForNotificationsRelatedToTables.isEmpty()) return;

        PGConnection connection;
        Handle handle = getHandle(handleCreated -> {
            for (String tableName : listenForNotificationsRelatedToTables.keySet()) {
                listen(tableName);
            }
        });
        try {
            connection = handle.getConnection().unwrap(PGConnection.class);
            var notifications = connection.getNotifications();
            if (notifications.length > 0) {
                log.debug("Received {} Notification(s)", notifications.length);
                Arrays.stream(notifications)
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
                      .forEach(eventBus::publish);
            } else {
                log.trace("Didn't receive any Notifications");
            }
        } catch (ConnectionException | SQLException e) {
            log.error(msg("Failed to listen for notifications"),
                      e);
            // This may be due to Connection issue, so let's close the handle and reset the reference
            try {
                handle.close();
            } catch (Exception ex) {
                log.error(msg("Failed to close the listener Handle"),
                          e);
            }
            handleReference.set(null);
        }
    }

    private Handle getHandle(Consumer<Handle> onHandleCreated) {
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
            throw new RuntimeException("Failed to acquire Handle", e);
        }
    }

}
