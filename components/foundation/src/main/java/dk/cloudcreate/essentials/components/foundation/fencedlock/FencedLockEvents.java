package dk.cloudcreate.essentials.components.foundation.fencedlock;

import dk.cloudcreate.essentials.reactive.LocalEventBus;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public interface FencedLockEvents {
    FencedLockManager getLockManager();

    /**
     * Is published on the {@link LocalEventBus} when a {@link FencedLockManager#start()} is called (and the lock manager isn't already started)
     */
    class FencedLockManagerStarted implements FencedLockEvents {
        public final FencedLockManager lockManager;

        public FencedLockManagerStarted(FencedLockManager lockManager) {
            this.lockManager = requireNonNull(lockManager, "No lockManager instance provided");
        }

        @Override
        public FencedLockManager getLockManager() {
            return lockManager;
        }

        @Override
        public String toString() {
            return "FencedLockManagerStarted{" +
                    "lockManager=" + lockManager +
                    '}';
        }
    }

    /**
     * Is published on the {@link LocalEventBus} when a {@link FencedLockManager#stop()} is called (and the lock manager isn't already stopped)
     */
    class FencedLockManagerStopped implements FencedLockEvents {
        public final FencedLockManager lockManager;

        public FencedLockManagerStopped(FencedLockManager lockManager) {
            this.lockManager = requireNonNull(lockManager, "No lockManager instance provided");
        }

        @Override
        public FencedLockManager getLockManager() {
            return lockManager;
        }

        @Override
        public String toString() {
            return "FencedLockManagerStopped{" +
                    "lockManager=" + lockManager +
                    '}';
        }
    }

    class LockConfirmed implements FencedLockEvents {
        public final FencedLock        lock;
        public final FencedLockManager lockManager;

        public LockConfirmed(FencedLock lock, FencedLockManager lockManager) {
            this.lock = lock;
            this.lockManager = requireNonNull(lockManager, "No lockManager instance provided");
        }

        @Override
        public FencedLockManager getLockManager() {
            return lockManager;
        }

        @Override
        public String toString() {
            return "LockConfirmed{" +
                    "lock=" + lock +
                    ", lockManager=" + lockManager +
                    '}';
        }
    }

    class LockReleased implements FencedLockEvents {
        public final FencedLock        lock;
        public final FencedLockManager lockManager;

        public LockReleased(FencedLock lock, FencedLockManager lockManager) {
            this.lock = lock;
            this.lockManager = requireNonNull(lockManager, "No lockManager instance provided");
        }

        @Override
        public FencedLockManager getLockManager() {
            return lockManager;
        }

        @Override
        public String toString() {
            return "LockConfirmed{" +
                    "lock=" + lock +
                    ", lockManager=" + lockManager +
                    '}';
        }
    }

    class LockAcquired implements FencedLockEvents {
        public final FencedLock        lock;
        public final FencedLockManager lockManager;

        public LockAcquired(FencedLock lock, FencedLockManager lockManager) {
            this.lock = lock;
            this.lockManager = requireNonNull(lockManager, "No lockManager instance provided");
        }

        @Override
        public FencedLockManager getLockManager() {
            return lockManager;
        }

        @Override
        public String toString() {
            return "LockAcquired{" +
                    "lock=" + lock +
                    ", lockManager=" + lockManager +
                    '}';
        }
    }

}
