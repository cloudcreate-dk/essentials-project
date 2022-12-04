package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkException;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.GenericHandleAwareUnitOfWorkFactory;
import org.slf4j.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

class EventStoreManagedUnitOfWork extends GenericHandleAwareUnitOfWorkFactory.GenericHandleAwareUnitOfWork implements EventStoreUnitOfWork {
    private final Logger                                       log = LoggerFactory.getLogger(EventStoreManagedUnitOfWork.class);
    /**
     * The list is maintained by the {@link EventStoreManagedUnitOfWorkFactory} and provided in the constructor
     */
    private final List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks;
    private final List<PersistedEvent>                         eventsPersisted;

    public EventStoreManagedUnitOfWork(GenericHandleAwareUnitOfWorkFactory<?> unitOfWorkFactory, List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks) {
        super(unitOfWorkFactory);
        this.lifecycleCallbacks = requireNonNull(lifecycleCallbacks, "No lifecycleCallbacks provided");
        this.eventsPersisted = new ArrayList<>();
    }

    @Override
    public void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork) {
        requireNonNull(eventsPersistedInThisUnitOfWork, "No eventsPersistedInThisUnitOfWork provided");
        this.eventsPersisted.addAll(eventsPersistedInThisUnitOfWork);
    }

    @Override
    protected void beforeCommitting() {
        for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
            try {
                log.trace("BeforeCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                callback.beforeCommit(this, eventsPersisted);
            } catch (RuntimeException e) {
                UnitOfWorkException unitOfWorkException = new UnitOfWorkException(msg("{} failed during beforeCommit PersistedEvents", callback.getClass().getName()), e);
                unitOfWorkException.fillInStackTrace();
                rollback(unitOfWorkException);
                throw unitOfWorkException;
            }
        }
    }


    @Override
    protected void afterCommitting() {
        for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
            try {
                log.trace("AfterCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                callback.afterCommit(this, eventsPersisted);
            } catch (RuntimeException e) {
                log.error(msg("Failed {} failed during afterCommit PersistedEvents", callback.getClass().getName()), e);
            }
        }
    }

    @Override
    protected void afterRollback(Exception cause) {
        for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
            try {
                log.trace("AfterCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                callback.afterCommit(this, eventsPersisted);
            } catch (RuntimeException e) {
                log.error(msg("Failed {} failed during afterCommit PersistedEvents", callback.getClass().getName()), e);
            }
        }
    }
}
