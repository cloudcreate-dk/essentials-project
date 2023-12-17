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

package dk.cloudcreate.essentials.components.eventsourced.aggregates.decider;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot.AggregateSnapshotRepository;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.shared.functional.tuple.Pair;
import dk.cloudcreate.essentials.types.LongRange;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Command Handler which is responsible for loading any existing Aggregate <code>STATE</code> from the underlying {@link EventStore}
 * (see {@link #deciderBasedCommandHandler(ConfigurableEventStore, AggregateType, Class, AggregateIdResolver, AggregateIdResolver, AggregateSnapshotRepository, Class, Decider)})
 * and coordinate persisting any changes to the Aggregate, in the form of <code>EVENT</code>'s, to the {@link EventStore} as part of an active {@link UnitOfWork} (if one exists)<br>
 * The actual logic is delegated to an instance of a {@link Decider}
 *
 * @param <COMMAND> The type of Commands that the {@link Decider#handle(Object, Object)} can process
 * @param <EVENT>   The type of Events that can be returned by {@link Decider#handle(Object, Object)} and applied in the {@link StateEvolver#applyEvent(Object, Object)}
 * @param <ERROR>   The type of Error that can be returned by the {@link Decider#handle(Object, Object)} method
 */
@FunctionalInterface
public interface CommandHandler<COMMAND, EVENT, ERROR> {
    /**
     * The <code>execute</code> method is responsible for handling a <code>COMMAND</code>, which can either result
     * in an <code>ERROR</code> or a list of <code>EVENT</code>'s.<br>
     * <b>Note: This method is called <code>decide</code> in the decider pattern</b><br>
     * Idempotent handling of a <code>COMMAND</code> will result in an <b>empty</b> list of <code>EVENT</code>'s
     *
     * @param cmd the command to handle
     * @return either an <code>ERROR</code> or a list of <code>EVENT</code>'s.<br>
     * Idempotent handling of a <code>COMMAND</code> will result in an <b>empty</b> list of <code>EVENT</code>'s
     */
    HandlerResult<ERROR, EVENT> handle(COMMAND cmd);

    /**
     * Create an instance of a {@link CommandHandler} that is responsible for loading any existing Aggregate <code>STATE</code> from the underlying {@link EventStore}
     * and coordinate persisting any changes to the Aggregate, in the form of <code>EVENT</code>'s, to the {@link EventStore} as part of an active {@link UnitOfWork} (if one exists)<br>
     * The actual logic is delegated to an instance of a {@link Decider}
     *
     * @param eventStore                     the {@link EventStore} that provides persistence support for aggregate events
     * @param aggregateType                  the aggregate type that this command handler can support <code>COMMAND</code>'s related to and which the {@link Decider} supports <code>EVENT</code>'s related to
     * @param aggregateIdType                the type of aggregate id that is associated with the {@link AggregateType}
     * @param aggregateIdFromCommandResolver resolver that can resolve the <code>aggregate-id</code> from a <code>COMMAND</code> object instance
     * @param aggregateIdFromEventResolver   resolver that can resolve the <code>aggregate-id</code> from an <code>EVENT</code> object instance
     * @param aggregateSnapshotRepository    optional {@link AggregateSnapshotRepository} for storing snapshots of the aggregate <code>STATE</code> for faster loading
     * @param stateType                      The type of aggregate <code>STATE</code> that the {@link Decider} instance works with
     * @param decider                        the {@link Decider} instance responsible for Aggregate logic
     * @param <CONFIG>                       The type of {@link AggregateEventStreamConfiguration} that the {@link EventStore} provided supports
     * @param <ID>                           the type of aggregate id that is associated with the {@link AggregateType}
     * @param <COMMAND>                      the type of <code>COMMAND</code> that the {@link Decider} instance supports
     * @param <EVENT>                        the type of <code>EVENT</code> that the {@link Decider} instance supports
     * @param <ERROR>                        the type of <code>ERROR</code> that the {@link Decider}  instance supports
     * @param <STATE>                        the type of Aggregate <code>STATE</code> that the {@link Decider}  instance supports
     * @return a {@link CommandHandler} that is responsible for loading any existing Aggregate <code>STATE</code> from the underlying {@link EventStore}
     * and coordinate persisting any changes to the Aggregate, in the form of <code>EVENT</code>'s, to the {@link EventStore} as part of an active {@link UnitOfWork} (if one exists)<br>
     * The actual logic is delegated to an instance of a {@link Decider}
     */
    static <CONFIG extends AggregateEventStreamConfiguration,
            ID,
            COMMAND, EVENT, ERROR, STATE> CommandHandler<COMMAND, EVENT, ERROR> deciderBasedCommandHandler(ConfigurableEventStore<CONFIG> eventStore,
                                                                                                           AggregateType aggregateType,
                                                                                                           Class<ID> aggregateIdType,
                                                                                                           AggregateIdResolver<COMMAND, ID> aggregateIdFromCommandResolver,
                                                                                                           AggregateIdResolver<EVENT, ID> aggregateIdFromEventResolver,
                                                                                                           AggregateSnapshotRepository aggregateSnapshotRepository,
                                                                                                           Class<STATE> stateType,
                                                                                                           Decider<COMMAND, EVENT, ERROR, STATE> decider) {
        requireNonNull(eventStore, "No eventStore provided");
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(aggregateIdType, "No aggregateIdType provided");
        requireNonNull(aggregateIdFromCommandResolver, "No aggregateIdFromCommandResolver provided");
        requireNonNull(aggregateIdFromEventResolver, "No aggregateIdFromEventResolver provided");
        requireNonNull(stateType, "No stateType provided");
        requireNonNull(decider, "No decider provided");

        if (eventStore.findAggregateEventStreamConfiguration(aggregateType).isEmpty()) {
            eventStore.addAggregateEventStreamConfiguration(aggregateType,
                                                            aggregateIdType);
        }

        var optionalAggregateSnapshotRepository = Optional.ofNullable(aggregateSnapshotRepository);

        return new CommandHandler<COMMAND, EVENT, ERROR>() {
            private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

            @Override
            public HandlerResult<ERROR, EVENT> handle(COMMAND cmd) {
                var optionalAggregateId = aggregateIdFromCommandResolver.resolveFrom(cmd);

                var eventOrderOfLastRehydratedEvent = new AtomicReference<EventOrder>(EventOrder.NO_EVENTS_PREVIOUSLY_PERSISTED);
                STATE finalState = optionalAggregateId.map(aggregateId -> {
                    // Check for aggregate snapshot
                    var possibleAggregateSnapshot = optionalAggregateSnapshotRepository.flatMap(repository -> repository.loadSnapshot(aggregateType,
                                                                                                                                      optionalAggregateId.get(),
                                                                                                                                      stateType));
                    var initialStateAndEventStream = possibleAggregateSnapshot.map(aggregateSnapshot -> {
                        log.trace("[{}] Preparing to handle command '{}' with associated aggregateId '{}' using '{}' snapshot with eventOrderOfLastIncludedEvent {}",
                                  aggregateType,
                                  cmd.getClass().getName(),
                                  aggregateId,
                                  stateType.getName(),
                                  aggregateSnapshot.eventOrderOfLastIncludedEvent);
                        var eventStream = eventStore.fetchStream(aggregateType,
                                                                 optionalAggregateId.get(),
                                                                 LongRange.from(aggregateSnapshot.eventOrderOfLastIncludedEvent.increment().longValue()));
                        return Pair.of(aggregateSnapshot.aggregateSnapshot, eventStream);
                    }).orElseGet(() -> {
                        log.trace("[{}] Preparing to handle command '{}' with associated aggregateId '{}' and not using a snapshot",
                                  aggregateType,
                                  cmd.getClass().getName(),
                                  aggregateId);

                        return Pair.of(decider.initialState(),
                                       eventStore.fetchStream(aggregateType,
                                                              optionalAggregateId.get()));
                    });

                    var state       = initialStateAndEventStream._1;
                    var applyEvents = initialStateAndEventStream._2.isPresent();
                    if (applyEvents) {
                        log.trace("[{}] ApplyEvents: Preparing state '{}' to handle command '{}', associated aggregateId '{}'",
                                  aggregateType,
                                  stateType.getName(),
                                  cmd.getClass().getName(),
                                  aggregateId);

                        state = initialStateAndEventStream._2.get()
                                                             .events()
                                                             .reduce(initialStateAndEventStream._1,
                                                                     (deltaState, event) -> {
                                                                         eventOrderOfLastRehydratedEvent.set(event.eventOrder());
                                                                         return decider.applyEvent(deltaState, event.event().deserialize());
                                                                     },
                                                                     (deltaState, deltaState2) -> deltaState2);

                    }
                    return state;
                }).orElseGet(() -> {
                    log.trace("[{}] No aggregate-id resolved. Preparing initial-state '{}' to handle command '{}'",
                              aggregateType,
                              stateType.getName(),
                              cmd.getClass().getName());
                    return decider.initialState();
                });

                log.debug("[{}] Handling command '{}' using state '{}'",
                          aggregateType,
                          cmd.getClass().getName(),
                          stateType.getName());
                var result = decider.handle(cmd,
                                            finalState);
                if (result.isSuccess()) {
                    var events = result.asSuccess().events();
                    if (!events.isEmpty()) {
                        log.debug("[{}] Successfully handled command '{}' against '{}' with  associated aggregateId '{}'. Resulted in {} events of type {}",
                                  aggregateType,
                                  cmd.getClass().getName(),
                                  stateType.getName(),
                                  optionalAggregateId,
                                  events.size(),
                                  events.stream().map(event -> event.getClass().getSimpleName()).toList());
                        var firstEvent = events.get(0);
                        var aggregateId = optionalAggregateId.orElseGet(() -> {
                            var resolvesAggregateIdFromFirstEvent = aggregateIdFromEventResolver.resolveFrom(firstEvent)
                                                                                                .orElseThrow(() -> new IllegalStateException(msg("First event didn't an aggregateId. First Event type: '{}'",
                                                                                                                                                 firstEvent.getClass().getName())));
                            log.debug("[{}] Resolved aggregateId '{}' from first-event '{}'",
                                      aggregateType,
                                      resolvesAggregateIdFromFirstEvent,
                                      firstEvent.getClass().getName());

                            return resolvesAggregateIdFromFirstEvent;
                        });
                        eventStore.getUnitOfWorkFactory().getCurrentUnitOfWork()
                                  .ifPresentOrElse(eventStoreUnitOfWork -> {
                                      log.debug("[{}] Registering UnitOfWorkCallback to persist {} events associated with '{}' with aggregateId '{}'",
                                                aggregateType,
                                                events.size(),
                                                stateType.getName(),
                                                aggregateId);
                                      eventStoreUnitOfWork.registerLifecycleCallbackForResource(new EventsToAppendToStream<ID, EVENT, STATE>(finalState,
                                                                                                                                             aggregateType,
                                                                                                                                             aggregateId,
                                                                                                                                             eventOrderOfLastRehydratedEvent.get(),
                                                                                                                                             events),
                                                                                                new DeciderUnitOfWorkLifecycleCallback());
                                  }, () -> {
                                      log.debug("[{}] !!! No active UnitOfWork so will NOT persist {} events associated with '{}' with aggregateId '{}'",
                                                aggregateType,
                                                events.size(),
                                                stateType.getName(),
                                                aggregateId);
                                  });
                    } else {
                        log.debug("[{}] Successfully handled command '{}' against '{}' with associated aggregateId '{}', but it didn't result in any events",
                                  aggregateType,
                                  cmd.getClass().getName(),
                                  stateType.getName(),
                                  optionalAggregateId);

                    }
                } else {
                    log.debug("[{}] Failed to handle command '{}' against '{}' with associated aggregateId '{}'. Resulted in error: {}",
                              aggregateType,
                              cmd.getClass().getName(),
                              stateType.getName(),
                              optionalAggregateId,
                              result.asError().error());

                    eventStore.getUnitOfWorkFactory().getCurrentUnitOfWork()
                              .ifPresent(UnitOfWork::markAsRollbackOnly);
                }
                return result;
            }

            class DeciderUnitOfWorkLifecycleCallback implements UnitOfWorkLifecycleCallback<EventsToAppendToStream<ID, EVENT, STATE>> {
                @Override
                public void beforeCommit(UnitOfWork unitOfWork, List<EventsToAppendToStream<ID, EVENT, STATE>> associatedResources) {
                    log.trace("[{}] beforeCommit processing {} '{}' registered with the UnitOfWork being committed",
                              aggregateType,
                              associatedResources.size(),
                              stateType.getName());
                    associatedResources.forEach(eventsToAppendToStream -> {
                        log.trace("[{}] beforeCommit processing '{}' with id '{}'",
                                  aggregateType,
                                  stateType.getName(),
                                  eventsToAppendToStream.aggregateId());
                        if (log.isTraceEnabled()) {
                            log.trace("[{}] Persisting {} event(s) related to '{}' with id '{}': {}",
                                      aggregateType,
                                      eventsToAppendToStream.events().size(),
                                      stateType.getName(),
                                      eventsToAppendToStream.aggregateId(),
                                      eventsToAppendToStream.events().stream()
                                                            .map(persistableEvent -> persistableEvent.getClass().getName())
                                                            .reduce((s, s2) -> s + ", " + s2));
                        } else {
                            log.debug("[{}] Persisting {} event(s) related to '{}' with id '{}'",
                                      aggregateType,
                                      eventsToAppendToStream.events().size(),
                                      stateType.getName(),
                                      eventsToAppendToStream.aggregateId());
                        }
                        var persistedEvents = eventStore.appendToStream(aggregateType,
                                                                        eventsToAppendToStream.aggregateId(),
                                                                        eventsToAppendToStream.eventOrderOfLastRehydratedEvent(),
                                                                        eventsToAppendToStream.events());
                        optionalAggregateSnapshotRepository.ifPresent(repository -> repository.aggregateUpdated(eventsToAppendToStream.state(), persistedEvents));
                    });
                }

                @Override
                public void afterCommit(UnitOfWork unitOfWork, List<EventsToAppendToStream<ID, EVENT, STATE>> associatedResources) {

                }

                @Override
                public void beforeRollback(UnitOfWork unitOfWork, List<EventsToAppendToStream<ID, EVENT, STATE>> associatedResources, Exception causeOfTheRollback) {

                }

                @Override
                public void afterRollback(UnitOfWork unitOfWork, List<EventsToAppendToStream<ID, EVENT, STATE>> associatedResources, Exception causeOfTheRollback) {

                }
            }


            record EventsToAppendToStream<ID, EVENT, STATE>(
                    STATE state,
                    AggregateType aggregateType,
                    ID aggregateId,
                    EventOrder eventOrderOfLastRehydratedEvent,
                    List<EVENT> events) {
            }
        };
    }
}