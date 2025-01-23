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
