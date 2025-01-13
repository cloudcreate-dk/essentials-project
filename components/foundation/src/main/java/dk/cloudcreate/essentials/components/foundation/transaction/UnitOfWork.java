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

package dk.cloudcreate.essentials.components.foundation.transaction;

public interface UnitOfWork {
    /**
     * Start the {@link UnitOfWork} and any underlying transaction
     */
    void start();

    /**
     * Commit the {@link UnitOfWork} and any underlying transaction - see {@link UnitOfWorkStatus#Committed}
     */
    void commit();

    /**
     * Roll back the {@link UnitOfWork} and any underlying transaction - see {@link UnitOfWorkStatus#RolledBack}
     *
     * @param cause the cause of the rollback
     */
    void rollback(Exception cause);

    /**
     * Get the status of the {@link UnitOfWork}
     */
    UnitOfWorkStatus status();

    /**
     * The cause of a Rollback or a {@link #markAsRollbackOnly(Exception)}
     */
    Exception getCauseOfRollback();

    default void markAsRollbackOnly() {
        markAsRollbackOnly(null);
    }

    void markAsRollbackOnly(Exception cause);

    default String info() {
        return toString();
    }

    /**
     * Roll back the {@link UnitOfWork} and any underlying transaction - see {@link UnitOfWorkStatus#RolledBack}
     */
    default void rollback() {
        // Use any exception saved using #markAsRollbackOnly(Exception)
        rollback(getCauseOfRollback());
    }

    /**
     * TODO: Adjust example to the new API
     * Register a resource (e.g. an Aggregate) that should have its {@link UnitOfWorkLifecycleCallback} called during {@link UnitOfWork} operation.<br>
     * Example:
     * <pre>{@code
     * Aggregate aggregate = unitOfWork.registerLifecycleCallbackForResource(aggregate.loadFromEvents(event),
     *                                                                       new AggregateRootRepositoryUnitOfWorkLifecycleCallback()));
     * }</pre>
     * Where the Aggreg
     * <pre>{@code
     * class AggregateRootRepositoryUnitOfWorkLifecycleCallback implements UnitOfWorkLifecycleCallback<AGGREGATE_TYPE> {
     *     @Override
     *     public void beforeCommit(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources) {
     *         log.trace("beforeCommit processing {} '{}' registered with the UnitOfWork being committed", associatedResources.size(), aggregateType.getName());
     *         associatedResources.forEach(aggregate -> {
     *             log.trace("beforeCommit processing '{}' with id '{}'", aggregateType.getName(), aggregate.aggregateId());
     *             List<Object> persistableEvents = aggregate.uncommittedChanges();
     *             if (persistableEvents.isEmpty()) {
     *                 log.trace("No changes detected for '{}' with id '{}'", aggregateType.getName(), aggregate.aggregateId());
     *             } else {
     *                 if (log.isTraceEnabled()) {
     *                     log.trace("Persisting {} event(s) related to '{}' with id '{}': {}", persistableEvents.size(), aggregateType.getName(), aggregate.aggregateId(), persistableEvents.map(persistableEvent -> persistableEvent.event().getClass().getName()).reduce((s, s2) -> s + ", " + s2));
     *                 } else {
     *                     log.debug("Persisting {} event(s) related to '{}' with id '{}'", persistableEvents.size(), aggregateType.getName(), aggregate.aggregateId());
     *                 }
     *                 eventStore.persist(unitOfWork, persistableEvents);
     *                 aggregate.markChangesAsCommitted();
     *             }
     *         });
     *     }
     *
     *     @Override
     *     public void afterCommit(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources) {
     *
     *     }
     *
     *     @Override
     *     public void beforeRollback(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources, Exception causeOfTheRollback) {
     *
     *     }
     *
     *     @Override
     *     public void afterRollback(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources, Exception causeOfTheRollback) {
     *
     *     }
     * }
     * }
     * </pre>
     *
     * @param resource                     the resource that should be tracked
     * @param associatedUnitOfWorkCallback the callback instance for the given resource
     * @param <T>                          the type of resource
     * @return the <code>resource</code> or a proxy to it
     */
    <T> T registerLifecycleCallbackForResource(T resource, UnitOfWorkLifecycleCallback<T> associatedUnitOfWorkCallback);
}
