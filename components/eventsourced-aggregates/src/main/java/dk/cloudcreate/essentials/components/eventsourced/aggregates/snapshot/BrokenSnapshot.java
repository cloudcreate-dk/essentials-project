package dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot;

/**
 * {@link AggregateSnapshot#aggregateSnapshot} value that's returned from {@link AggregateSnapshotRepository} in case the
 * Aggregate snapshot couldn't be deserialized
 */
public final class BrokenSnapshot {
    public final Exception cause;

    public BrokenSnapshot(Exception cause) {
        this.cause = cause;
    }
}
