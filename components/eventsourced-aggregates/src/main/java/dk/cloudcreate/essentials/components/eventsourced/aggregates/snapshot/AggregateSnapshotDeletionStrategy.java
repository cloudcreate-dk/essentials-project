package dk.cloudcreate.essentials.components.eventsourced.aggregates.snapshot;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;

import java.util.*;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.shared.FailFast.requireTrue;

/**
 * Strategy for deleting historic aggregate snapshots (i.e. old aggregate snapshots) when a new aggregate snapshot is persisted
 */
public interface AggregateSnapshotDeletionStrategy {

    /**
     * Strategy that keeps a specific number historic aggregate snapshots
     *
     * @param numberOfHistoricSnapshotsToKeep the number of historic aggregate snapshots to keep
     * @return Strategy that keeps all historic aggregate snapshot events
     */
    static AggregateSnapshotDeletionStrategy keepALimitedNumberOfHistoricSnapshots(long numberOfHistoricSnapshotsToKeep) {
        return KeepHistoricSnapshots.keepALimitedNumberOfHistoricSnapshots(numberOfHistoricSnapshotsToKeep);
    }

    /**
     * Strategy that keeps all historic aggregate snapshots
     *
     * @return Strategy that keeps all historic aggregate snapshots
     */
    static AggregateSnapshotDeletionStrategy keepAllHistoricSnapshots() {
        return KeepHistoricSnapshots.keepAllHistoricSnapshots();
    }

    /**
     * Strategy that deletes any historic aggregate snapshots when a new aggregate snapshot is persisted
     *
     * @return Strategy that deletes any historic aggregate snapshots when a new aggregate snapshot is persisted
     */
    static AggregateSnapshotDeletionStrategy deleteAllHistoricSnapshots() {
        return new DeleteAllHistoricSnapshots();
    }

    /**
     * Guard method that determines if Aggregate Snapshot statistics are required in order
     * to determine which Snapshots to delete
     *
     * @return true if {@link AggregateSnapshotRepository#aggregateUpdated(Object, AggregateEventStream)} should fetch
     * existing {@link AggregateSnapshot}'s (without the payload) as input to {@link #resolveSnapshotsToDelete(List)} or
     * <b>false</b> if all existing snapshots can be deleted (solves {@link DeleteAllHistoricSnapshots} without incurring additional DB queries)
     */
    boolean requiresExistingSnapshotDetailsToDetermineWhichAggregateSnapshotsToDelete();

    /**
     * Out of all the existing snapshots (loaded without the snapshot payload), return the {@link AggregateSnapshot}'s that should be deleted<br>
     * This method is called just prior to adding a new {@link AggregateSnapshot}, so if the purpose it to keep
     * 3 snapshots, and we already have 3 snapshots, then this method should return a {@link Stream} containing
     * the oldest snapshot, which will then be deleted
     *
     * @param existingSnapshots     all the existing snapshots pertaining to a given aggregate instance
     * @param <ID>                  the id type for the aggregate
     * @param <AGGREGATE_IMPL_TYPE> the aggregate type implementation type
     * @return all the {@link AggregateSnapshot}'s that should be deleted
     */
    <ID, AGGREGATE_IMPL_TYPE> Stream<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> resolveSnapshotsToDelete(List<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> existingSnapshots);

    /**
     * Strategy that keeps all or a specific number of historic aggregate snapshots
     */
    class KeepHistoricSnapshots implements AggregateSnapshotDeletionStrategy {
        private final long numberOfHistoricSnapshotsToKeep;

        /**
         * Strategy that keeps all historic aggregate snapshots
         *
         * @return Strategy that keeps all historic aggregate snapshots
         */
        public KeepHistoricSnapshots() {
            numberOfHistoricSnapshotsToKeep = Long.MAX_VALUE;
        }

        /**
         * Strategy that keeps a specific number historic aggregate snapshots
         *
         * @param numberOfHistoricSnapshotsToKeep the number of historic aggregate snapshots to keep (must be >= 0)
         * @return Strategy that keeps all historic snapshot events
         */
        public KeepHistoricSnapshots(long numberOfHistoricSnapshotsToKeep) {
            requireTrue(numberOfHistoricSnapshotsToKeep >= 0, "numberOfHistoricSnapshotsToKeep must be >= 0");
            this.numberOfHistoricSnapshotsToKeep = numberOfHistoricSnapshotsToKeep;
        }

        /**
         * Strategy that keeps all historic aggregate snapshots
         *
         * @return Strategy that keeps all historic aggregate snapshots
         */
        public static KeepHistoricSnapshots keepALimitedNumberOfHistoricSnapshots(long numberOfHistoricSnapshotsToKeep) {
            return new KeepHistoricSnapshots(numberOfHistoricSnapshotsToKeep);
        }

        /**
         * Strategy that keeps all historic aggregate snapshots
         *
         * @return Strategy that keeps all historic aggregate snapshots
         */
        public static KeepHistoricSnapshots keepAllHistoricSnapshots() {
            return new KeepHistoricSnapshots();
        }

        @Override
        public boolean requiresExistingSnapshotDetailsToDetermineWhichAggregateSnapshotsToDelete() {
            return numberOfHistoricSnapshotsToKeep > 0;
        }

        @Override
        public <ID, AGGREGATE_IMPL_TYPE> Stream<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> resolveSnapshotsToDelete(List<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> existingSnapshots) {
            if (existingSnapshots.size() >= numberOfHistoricSnapshotsToKeep) {
                // Delete the oldest snapshots
                return existingSnapshots.stream()
                                        .limit(existingSnapshots.size() - numberOfHistoricSnapshotsToKeep + 1); // (+1 is because right after this method is called we will add a new snapshot)


            } else {
                return Stream.empty();
            }
        }

        public long getNumberOfHistoricSnapshotsToKeep() {
            return numberOfHistoricSnapshotsToKeep;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            KeepHistoricSnapshots that = (KeepHistoricSnapshots) o;
            return numberOfHistoricSnapshotsToKeep == that.numberOfHistoricSnapshotsToKeep;
        }

        @Override
        public int hashCode() {
            return Objects.hash(numberOfHistoricSnapshotsToKeep);
        }

        @Override
        public String toString() {
            if (numberOfHistoricSnapshotsToKeep == Long.MAX_VALUE) {
                return "KeepAllHistoricSnapshots";
            } else {
                return "KeepALimitedNumberOfHistoricSnapshots(" +
                        numberOfHistoricSnapshotsToKeep +
                        ')';
            }
        }

    }

    /**
     * Strategy that deletes any historic aggregate snapshots when a new snapshot is persisted
     */
    class DeleteAllHistoricSnapshots implements AggregateSnapshotDeletionStrategy {
        @Override
        public String toString() {
            return "DeleteAllHistoricSnapshots";
        }

        @Override
        public boolean requiresExistingSnapshotDetailsToDetermineWhichAggregateSnapshotsToDelete() {
            return false;
        }

        @Override
        public <ID, AGGREGATE_IMPL_TYPE> Stream<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> resolveSnapshotsToDelete(List<AggregateSnapshot<ID, AGGREGATE_IMPL_TYPE>> existingSnapshots) {
            return existingSnapshots.stream();
        }
    }
}
