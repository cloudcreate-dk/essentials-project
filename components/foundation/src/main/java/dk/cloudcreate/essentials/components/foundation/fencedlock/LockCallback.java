/*
 * Copyright 2021-2024 the original author or authors.
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

import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public interface LockCallback {
    /**
     * This method is called when the lock in question is acquired by the {@link FencedLockManager}
     * on this JVM instance
     *
     * @param lock the lock that was acquired
     */
    void lockAcquired(FencedLock lock);

    /**
     * This method is called when the lock in question was owned by the {@link FencedLockManager}
     * instance belonging to this JVM instance and the lock has been released<br>
     *
     * @param lock the lock that was released
     */
    void lockReleased(FencedLock lock);

    static LockCallbackBuilder builder() {
        return new LockCallbackBuilder();
    }

    static LockCallback lockCallback(Consumer<FencedLock> onLockAcquired,
                                     Consumer<FencedLock> onLockReleased) {
        requireNonNull(onLockAcquired, "No lockAcquired consumer provided");
        requireNonNull(onLockReleased, "No onLockReleased consumer provided");
        return new LockCallback() {

            @Override
            public void lockAcquired(FencedLock lock) {
                onLockAcquired.accept(lock);
            }

            @Override
            public void lockReleased(FencedLock lock) {
                onLockReleased.accept(lock);
            }
        };
    }
}

