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

package dk.cloudcreate.essentials.components.foundation.test.fencedlock;

import dk.cloudcreate.essentials.components.foundation.fencedlock.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DBFencedLockManager_MultiNode_ReleaseLockIT<LOCK_MANAGER extends DBFencedLockManager<?, ?>> {
    private LOCK_MANAGER lockManagerNode1;
    private LOCK_MANAGER lockManagerNode2;

    protected LOCK_MANAGER getLockManagerNode1() {
        return lockManagerNode1;
    }

    protected LOCK_MANAGER getLockManagerNode2() {
        return lockManagerNode2;
    }

    @BeforeEach
    void setup() {
        if (lockManagerNode1 != null) {
            throw new IllegalStateException("LockManager for node1 is non-null during setup");
        }
        if (lockManagerNode2 != null) {
            throw new IllegalStateException("LockManager for node2 is non-null during setup");
        }


        lockManagerNode1 = createLockManagerNode1();
        assertThat(lockManagerNode1).isNotNull();
        lockManagerNode1.deleteAllLocksInDB();
        lockManagerNode1.start();

        lockManagerNode2 = createLockManagerNode2();
        assertThat(lockManagerNode1).isNotNull();
        lockManagerNode2.start();
    }

    protected abstract LOCK_MANAGER createLockManagerNode2();

    protected abstract LOCK_MANAGER createLockManagerNode1();

    protected abstract void disruptDatabaseConnection();

    protected abstract void restoreDatabaseConnection();

    @AfterEach
    void cleanup() {
        System.out.println("*******  Cleaning up  *******");
        if (lockManagerNode1 != null) {
            lockManagerNode1.stop();
            lockManagerNode1 = null;
        }
        if (lockManagerNode2 != null) {
            lockManagerNode2.stop();
            lockManagerNode2 = null;
        }
    }

    @Test
    void verify_loosing_db_connection_all_locally_acquired_locks_are_released() throws InterruptedException {
        // Given
        var lockName = LockName.of("testLock");
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isFalse();

        var lockNode1Callback = new TestLockCallback();
        var lockNode2Callback = new TestLockCallback();

        // When
        lockManagerNode1.acquireLockAsync(lockName, lockNode1Callback);
        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isTrue());
        lockManagerNode2.acquireLockAsync(lockName, lockNode2Callback);
        Thread.sleep(1000);

        // Then
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();
        // And
        assertThat(lockNode1Callback.lockAcquired).isNotNull();
        assertThat(lockNode1Callback.lockReleased).isNull();
        assertThat(lockNode1Callback.lockAcquired.isLocked());
        assertThat((CharSequence) lockNode1Callback.lockAcquired.getName()).isEqualTo(lockName);
        assertThat(lockNode1Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockNode1Callback.lockAcquired.isLockedByThisLockManagerInstance());

        // Node 2 must not be able to acquire the same lock
        assertThat(lockNode2Callback.lockAcquired).isNull();
        assertThat(lockNode2Callback.lockReleased).isNull();
        assertThat(lockNode1Callback.lockReleased).isNull();

        // When disrupt network connection for both nodes
        System.out.println("------ Disrupting the database connection ------");
        disruptDatabaseConnection();

        // Then Node 1 should have released the lock and node 2 should not have acquired the lock either, since the DB connection is interrupted
        Thread.sleep(5000);
        System.out.println("------ Checking the node 1 released the lock ------");
        assertThat(lockNode1Callback.lockReleased).isNotNull();
        assertThat(lockNode1Callback.lockAcquired.isLocked()).isFalse();
        assertThat(lockNode1Callback.lockAcquired.isLockedByThisLockManagerInstance()).isFalse();
        assertThat(lockNode1Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockNode2Callback.lockAcquired).isNull();
        assertThat(lockNode2Callback.lockReleased).isNull();

        // Restoring the connection could potentially result in node 2 overtaking the timed out lock
        System.out.println("------ Restoring the database connection ------");
        lockNode1Callback.reset();
        lockNode2Callback.reset();
        restoreDatabaseConnection();

        Thread.sleep(7000);
        System.out.println("------ Checking which node has the lock ------");
        if (lockNode2Callback.lockAcquired != null) {
            System.out.println("====== Node 2 should have acquired the lock and node 1 should not have acquired it ======");
            assertThat(lockNode2Callback.lockAcquired).isNotNull();
            assertThat(lockNode2Callback.lockReleased).isNull();
            assertThat(lockNode1Callback.lockAcquired).isNull();
            assertThat(lockNode1Callback.lockReleased).isNull();
            assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
            assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isTrue();
            assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();
            assertThat(lockNode2Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode2.getInitialTokenValue()+1);
            assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
            assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
            assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        } else {
            System.out.println("====== Node 1 acquired the lock and node 2 should not have acquired it ======");
            assertThat(lockNode1Callback.lockAcquired).isNotNull();
            assertThat(lockNode1Callback.lockReleased).isNull();
            assertThat(lockNode2Callback.lockAcquired).isNull();
            assertThat(lockNode2Callback.lockReleased).isNull();
            assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
            assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isTrue();
            assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
            assertThat(lockNode1Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue()+1);
            assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
            assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();
            assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        }
    }

    private static class TestLockCallback implements LockCallback {
        FencedLock lockReleased;
        FencedLock lockAcquired;

        @Override
        public void lockAcquired(FencedLock lock) {
            this.lockAcquired = lock;
        }

        @Override
        public void lockReleased(FencedLock lock) {
            this.lockReleased = lock;
        }

        public void reset() {
            lockReleased = null;
            lockAcquired = null;
        }
    }
}
