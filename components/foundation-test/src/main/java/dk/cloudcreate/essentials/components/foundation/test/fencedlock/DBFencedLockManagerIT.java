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

public abstract class DBFencedLockManagerIT<LOCK_MANAGER extends DBFencedLockManager<?, ?>> {
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
        deleteAllLocksInDBWithRetry(lockManagerNode1);
        lockManagerNode1.start();

        lockManagerNode2 = createLockManagerNode2();
        assertThat(lockManagerNode1).isNotNull();
        lockManagerNode2.start();
    }

    protected abstract LOCK_MANAGER createLockManagerNode2();

    protected abstract LOCK_MANAGER createLockManagerNode1();

    protected abstract void disruptDatabaseConnection();

    protected abstract void restoreDatabaseConnection();

    public static void deleteAllLocksInDBWithRetry(DBFencedLockManager<?, ?> lockManager) {
        int maxRetries = 5;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                lockManager.deleteAllLocksInDB();
                return; // Success
            } catch (RuntimeException e) {
                if (e.getMessage().contains("WriteConflict")) {
                    retryCount++;
                    try {
                        Thread.sleep(100 * retryCount); // Exponential backoff
                    } catch (InterruptedException ignored) {}
                } else {
                    throw e; // Not a transient error, rethrow
                }
            }
        }
        throw new RuntimeException("Failed to deleteAllLocksInDB after " + maxRetries + " retries.");
    }

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
    void verify_that_we_can_perform_tryAcquire_on_a_lock_and_release_it_again() {
        var lockName = LockName.of("testLock");
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isFalse();

        // When
        var lockOptionNode1 = lockManagerNode1.tryAcquireLock(lockName);
        var lockOptionNode2 = lockManagerNode2.tryAcquireLock(lockName);

        // Then
        assertThat(lockOptionNode1).isNotNull();
        assertThat(lockOptionNode1).isPresent();
        assertThat(lockOptionNode1.get().isLocked()).isTrue();
        assertThat(lockOptionNode1.get().getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockOptionNode1.get().isLockedByThisLockManagerInstance()).isTrue();
        var actualLock = lockManagerNode1.lookupLock(lockName);
        assertThat(actualLock).isEqualTo(lockOptionNode1);
        assertThat(actualLock.get().getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();

        // Node 2 must not be able to acquire the same lock
        assertThat(lockOptionNode2).isNotNull();
        assertThat(lockOptionNode2).isEmpty();

        // When
        lockOptionNode1.get().release();

        // Then
        assertThat(lockOptionNode1.get().isLocked()).isFalse();
        assertThat(lockOptionNode1.get().getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockOptionNode1.get().isLockedByThisLockManagerInstance()).isFalse();
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isFalse();
    }

    @Test
    void verify_that_we_can_acquire_a_lock_and_release_it_again() {
        // Given
        var lockName = LockName.of("testLock");
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isFalse();

        // When
        var lockNode1       = lockManagerNode1.acquireLock(lockName);
        var lockOptionNode2 = lockManagerNode2.tryAcquireLock(lockName);

        // Then
        assertThat(lockNode1).isNotNull();
        assertThat(lockNode1.isLocked()).isTrue();
        assertThat(lockNode1.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockNode1.isLockedByThisLockManagerInstance()).isTrue();

        // Node 2 must not be able to acquire the same lock
        assertThat(lockOptionNode2).isNotNull();
        assertThat(lockOptionNode2).isEmpty();

        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();

        // When
        lockNode1.release();

        // Then
        assertThat(lockNode1.isLocked()).isFalse();
        assertThat(lockNode1.isLockedByThisLockManagerInstance()).isFalse();
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isFalse();
    }

    @Test
    void stopping_a_lockManager_releases_all_acquired_locks() {
        // Given
        var lock1 = LockName.of("lock1");
        var lock2 = LockName.of("lock2");
        assertThat(lockManagerNode1.isLockAcquired(lock1)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lock1)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lock2)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lock2)).isFalse();

        // When
        var lock1Node1       = lockManagerNode1.acquireLock(lock1);
        var lock2Node1       = lockManagerNode1.acquireLock(lock2);
        var lock1OptionNode2 = lockManagerNode2.tryAcquireLock(lock1);
        var lock2OptionNode2 = lockManagerNode2.tryAcquireLock(lock2);


        // Then
        assertThat(lock1Node1.isLocked()).isTrue();
        assertThat(lock1Node1.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lock2Node1.isLocked()).isTrue();
        assertThat(lock2Node1.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lock1OptionNode2).isEmpty();
        assertThat(lock2OptionNode2).isEmpty();

        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lock1)).isTrue();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lock1)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lock1)).isTrue();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lock1)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lock1)).isTrue();
        assertThat(lockManagerNode2.isLockAcquired(lock1)).isTrue();
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lock2)).isTrue();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lock2)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lock2)).isTrue();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lock2)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lock2)).isTrue();
        assertThat(lockManagerNode2.isLockAcquired(lock2)).isTrue();

        // When
        lockManagerNode1.stop();

        // Then
        assertThat(lock1Node1.isLocked()).isFalse();
        assertThat(lock2Node1.isLocked()).isFalse();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lock1)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lock1)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lock1)).isFalse();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lock2)).isFalse();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lock2)).isFalse();
        assertThat(lockManagerNode2.isLockAcquired(lock2)).isFalse();

        // Test that node 2 can acquire the locks after they were release by lockManager1 shutdown
        // When
        lock1OptionNode2 = lockManagerNode2.tryAcquireLock(lock1);
        lock2OptionNode2 = lockManagerNode2.tryAcquireLock(lock2);

        // Then
        assertThat(lock1OptionNode2).isPresent();
        assertThat(lock1OptionNode2.get().isLocked()).isTrue();
        assertThat(lock1OptionNode2.get().getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue() + 1L);
        assertThat(lock1OptionNode2.get().isLockedByThisLockManagerInstance()).isTrue();
        assertThat(lock2OptionNode2).isPresent();
        assertThat(lock2OptionNode2).isPresent();
        assertThat(lock2OptionNode2.get().isLocked()).isTrue();
        assertThat(lock2OptionNode2.get().getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue() + 1L);
        assertThat(lock2OptionNode2.get().isLockedByThisLockManagerInstance()).isTrue();
    }

    @Test
    void verify_that_acquireLockAsync_allows_us_to_acquire_locks_asynchronously() throws InterruptedException {
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
        Awaitility.waitAtMost(Duration.ofSeconds(2))
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
        assertThat(lockNode1Callback.lockAcquired.getCurrentToken()).isGreaterThanOrEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockNode1Callback.lockAcquired.isLockedByThisLockManagerInstance());

        // Node 2 must not be able to acquire the same lock
        assertThat(lockNode2Callback.lockAcquired).isNull();
        assertThat(lockNode2Callback.lockReleased).isNull();

        // And lock confirmation increases the lock token
        var lastLockConfirmedTimestamp = lockManagerNode1.lookupLock(lockName).get().getLockLastConfirmedTimestamp();
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> {
                      var lock = lockManagerNode1.lookupLock(lockName);
                      assertThat(lock).isPresent();
                      assertThat(lock.get().getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
                      assertThat(lock.get().getLockLastConfirmedTimestamp()).isAfter(lastLockConfirmedTimestamp);
                  });
        // And both Lock Managers can see this changed lock state
        var lock = lockManagerNode2.lookupLock(lockName);
        assertThat(lock).isPresent();
        assertThat(lock.get().getLockLastConfirmedTimestamp()).isAfter(lastLockConfirmedTimestamp);

        // When
        lockNode1Callback.lockAcquired.release();
        lockManagerNode1.pause();
        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(lockNode1Callback.lockReleased).isNotNull());

        // Then
        assertThat(lockNode1Callback.lockReleased).isNotNull();
        assertThat(lockNode1Callback.lockReleased.isLocked()).isFalse();
        assertThat(lockNode1Callback.lockReleased.isLockedByThisLockManagerInstance()).isFalse();

        // Node 2 should have acquired the lock
        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(lockNode2Callback.lockAcquired).isNotNull());
        lockManagerNode1.resume();
        assertThat(lockNode2Callback.lockAcquired.isLocked()).isTrue();
        assertThat((CharSequence) lockNode2Callback.lockAcquired.getName()).isEqualTo(lockName);
        assertThat(lockNode2Callback.lockAcquired.isLockedByThisLockManagerInstance()).isTrue();
        assertThat(lockNode2Callback.lockAcquired.getLockAcquiredTimestamp()).isAfter(lastLockConfirmedTimestamp);
        assertThat(lockNode2Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue() + 1L);

        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
    }

    @Test
    void verify_that_acquireLockAsync_allows_us_to_acquire_a_timedout_lock_asynchronously() throws InterruptedException {
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

        // When we pause lock confirmation (e.g. simulating a long GC pause)
        lockManagerNode1.pause();

        // Then Node 2 should have acquired the lock
        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(lockNode2Callback.lockAcquired).isNotNull());
        lockManagerNode1.resume();
        assertThat(lockNode2Callback.lockAcquired.isLocked()).isTrue();
        assertThat((CharSequence) lockNode2Callback.lockAcquired.getName()).isEqualTo(lockName);
        assertThat(lockNode2Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue() + 1L);
        assertThat(lockNode2Callback.lockAcquired.isLockedByThisLockManagerInstance()).isTrue();

        // Then we should be notified that the lock was released on node 1
        lockManagerNode1.resume();
        Awaitility.waitAtMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertThat(lockNode1Callback.lockReleased).isNotNull());
        assertThat(lockNode1Callback.lockReleased.isLocked()).isFalse();
        assertThat(lockNode1Callback.lockReleased.isLockedByThisLockManagerInstance()).isFalse();

        // And
        assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
        assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
        assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isTrue();
        assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
    }

    @Test
    void verify_loosing_db_connection_no_locks_are_released() throws InterruptedException {
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

        // Then Node 1 should NOT have released the lock and node 2 should not have acquired the lock
        Thread.sleep(5000);
        System.out.println("------ Checking the node 1 didn't release the lock ------");
        assertThat(lockNode1Callback.lockReleased).isNull();
        assertThat(lockNode1Callback.lockAcquired.isLocked()).isTrue();
        assertThat(lockNode1Callback.lockAcquired.isLockedByThisLockManagerInstance()).isTrue();
        assertThat(lockNode1Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
        assertThat(lockNode2Callback.lockAcquired).isNull();
        assertThat(lockNode2Callback.lockReleased).isNull();

        // Restoring the connection could potentially result in node 2 overtaking the timed out lock
        System.out.println("------ Restoring the database connection ------");
        restoreDatabaseConnection();

        Thread.sleep(7000);
        System.out.println("------ Checking which node has the lock ------");
        if (lockNode1Callback.lockReleased != null) {
            System.out.println("====== Node 1 lost the lock and node 2 should have acquired it ======");
            assertThat(lockNode2Callback.lockAcquired).isNotNull();
            assertThat(lockNode2Callback.lockReleased).isNull();
            assertThat(lockManagerNode2.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
            assertThat(lockManagerNode2.isLockedByThisLockManagerInstance(lockName)).isTrue();
            assertThat(lockManagerNode2.isLockAcquired(lockName)).isTrue();
            System.out.println("Lock token in DB " + lockManagerNode2.lookupLock(lockName).get().getCurrentToken());
            assertThat(lockNode2Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode2.getInitialTokenValue()+1);
            assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isFalse();
            assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
            assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isTrue();
        } else {
            System.out.println("====== Node 1 kept the lock and node 2 should not have acquired it ======");
            assertThat(lockNode2Callback.lockAcquired).isNull();
            assertThat(lockNode2Callback.lockReleased).isNull();
            assertThat(lockNode1Callback.lockReleased).isNull();
            assertThat(lockManagerNode1.isLockAcquiredByAnotherLockManagerInstance(lockName)).isFalse();
            assertThat(lockManagerNode1.isLockedByThisLockManagerInstance(lockName)).isTrue();
            assertThat(lockManagerNode1.isLockAcquired(lockName)).isTrue();
            assertThat(lockNode1Callback.lockAcquired.getCurrentToken()).isEqualTo(lockManagerNode1.getInitialTokenValue());
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
