/*
 * Copyright 2021-2022 the original author or authors.
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

import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockEvents.*;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.cloudcreate.essentials.shared.network.Network;
import org.slf4j.*;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;


public class DBFencedLockManager<UOW extends UnitOfWork, LOCK extends DBFencedLock> implements FencedLockManager {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final FencedLockStorage<UOW, LOCK>                lockStorage;
    /**
     * Entries only exist if this lock manager instance believes that it has acquired the given lock<br>
     * Key: Lock name<br>
     * Value: The acquired {@link DBFencedLock}
     */
    private final ConcurrentMap<LockName, LOCK>               locksAcquiredByThisLockManager;
    /**
     * Entries only exist if acquiring the lock is being performed asynchronously
     * Key: lock name<br>
     * Value: the {@link ScheduledFuture} returned from {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
     */
    private final ConcurrentMap<LockName, ScheduledFuture<?>> asyncLockAcquirings;
    private final Duration                                    lockTimeOut;
    private final Duration                                    lockConfirmationInterval;
    private final String                                      lockManagerInstanceId;

    protected final UnitOfWorkFactory<? extends UOW> unitOfWorkFactory;
    private final   Optional<EventBus>               eventBus;

    private volatile boolean started;
    private volatile boolean stopping;
    /**
     * Paused is used for testing purposes to pause async lock acquiring and confirmation
     */
    private volatile boolean paused;

    private   ScheduledExecutorService lockConfirmationExecutor;
    private   ScheduledExecutorService asyncLockAcquiringExecutor;
    /**
     * {@link #tryAcquireLock(LockName)}/{@link #tryAcquireLock(LockName, Duration)} and {@link #acquireLock(LockName)} pause interval between retries
     */
    protected int                      syncAcquireLockPauseIntervalMs = 100;

    /**
     * @param lockStorage              the lock storage used for the lock manager
     * @param unitOfWorkFactory        the {@link UnitOfWork} factory
     * @param lockManagerInstanceId    The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param eventBus                 optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    protected DBFencedLockManager(FencedLockStorage<UOW, LOCK> lockStorage,
                                  UnitOfWorkFactory<? extends UOW> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval,
                                  Optional<EventBus> eventBus) {
        requireNonNull(lockManagerInstanceId, "No lockManagerInstanceId option provided");

        this.lockStorage = requireNonNull(lockStorage, "No lockStorage provided");
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory provided");
        this.lockManagerInstanceId = lockManagerInstanceId.orElseGet(Network::hostName);
        this.lockTimeOut = requireNonNull(lockTimeOut, "No lockTimeOut value provided");
        this.lockConfirmationInterval = requireNonNull(lockConfirmationInterval, "No lockConfirmationInterval value provided");
        if (lockConfirmationInterval.compareTo(lockTimeOut) >= 1) {
            throw new IllegalArgumentException(msg("lockConfirmationInterval {} duration MUST not be larger than the lockTimeOut {} duration, because locks will then always timeout",
                                                   lockConfirmationInterval,
                                                   lockTimeOut));
        }
        this.eventBus = requireNonNull(eventBus, "No eventBus option provided");

        locksAcquiredByThisLockManager = new ConcurrentHashMap<>();
        asyncLockAcquirings = new ConcurrentHashMap<>();


        log.info("[{}] Initializing '{}' using storage '{}' and lockConfirmationInterval: {} ms, lockTimeOut: {} ms",
                 this.lockManagerInstanceId,
                 this.getClass().getName(),
                 lockStorage.getClass().getName(),
                 lockConfirmationInterval.toMillis(),
                 lockTimeOut.toMillis());
        unitOfWorkFactory.usingUnitOfWork(uow -> lockStorage.initializeLockStorage(this, uow));
    }

    @Override
    public void start() {
        if (!started) {
            log.info("[{}] Starting lock manager", lockManagerInstanceId);
            stopping = false;
            lockConfirmationExecutor = Executors.newScheduledThreadPool(1,
                                                                        new ThreadFactoryBuilder()
                                                                                .nameFormat(lockManagerInstanceId + "-FencedLock-Confirmation-%d")
                                                                                .daemon(true)
                                                                                .build());

            asyncLockAcquiringExecutor = Executors.newScheduledThreadPool(2,
                                                                          ThreadFactoryBuilder.builder()
                                                                                              .nameFormat(lockManagerInstanceId + "-Lock-Acquiring-%d")
                                                                                              .daemon(true)
                                                                                              .build());

            lockConfirmationExecutor.scheduleAtFixedRate(this::confirmAllLocallyAcquiredLocks,
                                                         lockConfirmationInterval.toMillis(),
                                                         lockConfirmationInterval.toMillis(),
                                                         TimeUnit.MILLISECONDS);


            started = true;
            log.info("[{}] Started lock manager", lockManagerInstanceId);
            notify(new FencedLockManagerStopped(this));
        } else {
            log.debug("[{}] Lock Manager was already started", lockManagerInstanceId);
        }
    }

    protected void notify(FencedLockEvents event) {
        eventBus.ifPresent(localEventBus -> localEventBus.publish(event));
    }

    public void pause() {
        log.info("[{}] Pausing async lock acquiring and lock confirmation", lockManagerInstanceId);
        paused = true;
    }

    public void resume() {
        log.info("[{}] Resuming async lock acquiring and lock confirmation", lockManagerInstanceId);
        paused = false;
    }

    private void confirmAllLocallyAcquiredLocks() {
        if (stopping) {
            log.debug("[{}] Shutting down, skipping confirmAllLocallyAcquiredLocks", lockManagerInstanceId);
            return;
        }
        if (locksAcquiredByThisLockManager.size() == 0) {
            log.debug("[{}] No locks to confirm for this Lock Manager instance", lockManagerInstanceId);
            return;
        }

        if (paused) {
            log.info("[{}] Lock Manager is paused, skipping confirmAllLocallyAcquiredLocks", lockManagerInstanceId);
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("[{}] Confirming {} locks acquired by this Lock Manager Instance: {}", lockManagerInstanceId, locksAcquiredByThisLockManager.size(), locksAcquiredByThisLockManager.keySet());
        } else {
            log.debug("[{}] Confirming {} locks acquired by this Lock Manager Instance", lockManagerInstanceId, locksAcquiredByThisLockManager.size());
        }
        var confirmedTimestamp = OffsetDateTime.now(Clock.systemUTC());
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            locksAcquiredByThisLockManager.forEach((lockName, fencedLock) -> {
                log.trace("[{}] Attempting to confirm lock '{}': {}", lockManagerInstanceId, fencedLock.getName(), fencedLock);
                boolean confirmedWithSuccess = false;
                try {
                    confirmedWithSuccess = lockStorage.confirmLockInDB(this, uow, fencedLock, confirmedTimestamp);
                } catch (Exception e) {
                    log.error(msg("[{}] Attempting to confirm lock '{}': {}", lockManagerInstanceId, fencedLock.getName(), fencedLock), e);
                }
                if (confirmedWithSuccess) {
                    fencedLock.markAsConfirmed(confirmedTimestamp);
                    log.debug("[{}] Confirmed lock '{}': {}", lockManagerInstanceId, fencedLock.getName(), fencedLock);
                    notify(new LockAcquired(fencedLock, this));
                } else {
                    // We failed to confirm this lock, someone must have taken over the lock in the meantime
                    log.info("[{}] Failed to confirm lock '{}', someone has taken over the lock: {}", lockManagerInstanceId, fencedLock.getName(), fencedLock);
                    fencedLock.release();
                }
            });
        });
        if (log.isTraceEnabled()) {
            log.trace("[{}] Completed confirmation of locks acquired by this Lock Manager Instance. Number of Locks acquired locally after confirmation {}: {}", lockManagerInstanceId, locksAcquiredByThisLockManager.size(), locksAcquiredByThisLockManager.keySet());
        } else {
            log.debug("[{}] Completed confirmation of locks acquired by this Lock Manager Instance. Number of Locks acquired locally after confirmation {}", lockManagerInstanceId, locksAcquiredByThisLockManager.size());
        }


    }


    /**
     * Internal method only to be called by subclasses of {@link DBFencedLockManager} and {@link DBFencedLock}
     *
     * @param lock the lock to be released
     */
    protected void releaseLock(LOCK lock) {
        if (!lock.isLockedByThisLockManagerInstance()) {
            throw new IllegalArgumentException(msg("[{}] Cannot release Lock '{}' since it isn't locked by the current Lock Manager Node. Details: {}",
                                                   lockManagerInstanceId, lock.getName(), lock));
        }
        var releaseWithSuccess = unitOfWorkFactory.withUnitOfWork(uow -> lockStorage.releaseLockInDB(this, uow, lock));
        lock.markAsReleased();
        locksAcquiredByThisLockManager.remove(lock.getName());
        notify(new LockReleased(lock, this));

        if (releaseWithSuccess) {
            log.debug("[{}] Released Lock '{}': {}", lockManagerInstanceId, lock.getName(), lock);
        } else {
            // We didn't release the lock after all, someone else acquired the lock in the meantime
            unitOfWorkFactory.usingUnitOfWork(uow -> {
                lockStorage.lookupLockInDB(this, uow, lock.getName()).ifPresent(lockAcquiredByAnotherLockManager -> {
                    log.debug("[{}] Couldn't release Lock '{}' as it was already acquired by another JVM Node: {}", lockManagerInstanceId, lock.getName(), lockAcquiredByAnotherLockManager.getLockedByLockManagerInstanceId());
                });
            });
        }
    }

    @Override
    public Optional<FencedLock> lookupLock(LockName lockName) {
        requireNonNull(lockName, "No lockName provided");

        if (!started) {
            throw new IllegalStateException(msg("The {} isn't started", this.getClass().getSimpleName()));
        }

        var fencedLock = unitOfWorkFactory.withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName).map(FencedLock.class::cast));
        log.trace("[{}] Lookup FencedLock with name '{}' result: {}",
                  lockManagerInstanceId,
                  lockName,
                  fencedLock);
        return fencedLock;
    }

    @Override
    public void stop() {
        if (started) {
            log.debug("[{}] Stopping lock manager", lockManagerInstanceId);
            stopping = true;
            if (asyncLockAcquiringExecutor != null) {
                asyncLockAcquiringExecutor.shutdownNow();
                asyncLockAcquiringExecutor = null;
            }
            if (lockConfirmationExecutor != null) {
                lockConfirmationExecutor.shutdownNow();
                lockConfirmationExecutor = null;
            }
            locksAcquiredByThisLockManager.values().forEach(DBFencedLock::release);
            started = false;
            stopping = false;
            log.debug("[{}] Stopped lock manager", lockManagerInstanceId);
            notify(new FencedLockManagerStopped(this));
        } else {
            log.debug("[{}] Lock Manager was already stopped", lockManagerInstanceId);
        }
    }


    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public Optional<FencedLock> tryAcquireLock(LockName lockName) {
        var result = _tryAcquireLock(lockName).block();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<FencedLock> tryAcquireLock(LockName lockName, Duration timeout) {
        requireNonNull(timeout, "No timeout value provided");
        return Optional.ofNullable(_tryAcquireLock(lockName)
                                           .repeatWhenEmpty(longFlux -> longFlux.doOnNext(aLong -> {
                                               try {
                                                   Thread.sleep(syncAcquireLockPauseIntervalMs);
                                               } catch (InterruptedException e) {
                                                   // Ignore
                                               }
                                           }))
                                           .block(timeout));
    }

    private Mono<LOCK> _tryAcquireLock(LockName lockName) {
        requireNonNull(lockName, "No lockName provided");
        if (!started) {
            throw new IllegalStateException(msg("The {} isn't started", this.getClass().getSimpleName()));
        }

        log.debug("[{}] Attempting to acquire lock '{}'", lockManagerInstanceId, lockName);
        var alreadyAcquiredLock = locksAcquiredByThisLockManager.get(lockName);
        if (alreadyAcquiredLock != null && alreadyAcquiredLock.isLocked() && !isLockTimedOut(alreadyAcquiredLock)) {
            log.debug("[{}] Returned cached locally acquired lock '{}", lockManagerInstanceId, lockName);
            return Mono.just(alreadyAcquiredLock);
        }
        return unitOfWorkFactory.withUnitOfWork(uow -> {
            var lock = lockStorage.lookupLockInDB(this, uow, lockName)
                                  .orElseGet(() -> lockStorage.createUninitializedLock(this, lockName));
            return resolveLock(uow, lock);
        });
    }

    private Mono<LOCK> resolveLock(UOW uow, LOCK existingLock) {
        requireNonNull(uow, "No uow provided");
        requireNonNull(existingLock, "No existingLock provided");

        if (existingLock.isLocked()) {
            if (existingLock.isLockedByThisLockManagerInstance()) {
                // TODO: Should we confirm it to ensure that lack_confirmed is updated??
                log.debug("[{}] lock '{}' was already acquired by this JVM node: {}", lockManagerInstanceId, existingLock.getName(), existingLock);
                locksAcquiredByThisLockManager.put(existingLock.getName(), existingLock);
                return Mono.just(existingLock);
            }
            if (isLockTimedOut(existingLock)) {
                // Timed out - let us acquire the lock
                var now = OffsetDateTime.now(Clock.systemUTC());
                var newLock = lockStorage.createInitializedLock(this,
                                                                existingLock.getName(),
                                                                existingLock.getCurrentToken() + 1L,
                                                                lockManagerInstanceId,
                                                                now,
                                                                now);
                log.debug("[{}] Found a TIMED-OUT lock '{}', that was acquired by Lock Manager '{}'. Will attempt to acquire the lock. Timed-out lock: {} - New lock: {}",
                          lockManagerInstanceId,
                          existingLock.getName(),
                          existingLock.getLockedByLockManagerInstanceId(),
                          existingLock,
                          newLock);
                return updateLock(uow, existingLock, newLock);
            } else {
                return Mono.empty();
            }
        } else {
            if (Objects.equals(existingLock.getCurrentToken(), lockStorage.getUninitializedTokenValue())) {
                return insertLock(uow, existingLock);
            } else {
                var now = OffsetDateTime.now(Clock.systemUTC());
                var newLock = lockStorage.createInitializedLock(this,
                                                                existingLock.getName(),
                                                                existingLock.getCurrentToken() + 1L,
                                                                lockManagerInstanceId,
                                                                now,
                                                                now);
                log.debug("[{}] Found un-acquired lock '{}'. Have Acquired lock. Existing lock: {} - New lock: {}", lockManagerInstanceId, existingLock.getName(), existingLock, newLock);
                return updateLock(uow, existingLock, newLock);
            }
        }
    }

    private Mono<LOCK> insertLock(UOW uow, LOCK initialLock) {
        requireNonNull(uow, "No uow provided");
        requireNonNull(initialLock, "No initialLock provided");
        var now = OffsetDateTime.now(Clock.systemUTC());
        var insertedSuccessfully = lockStorage.insertLockIntoDB(this,
                                                                uow,
                                                                initialLock,
                                                                now);
        if (insertedSuccessfully) {
            initialLock.markAsLocked(now, lockManagerInstanceId, lockStorage.getInitialTokenValue());
            log.debug("[{}] Acquired lock '{}' for the first time (insert): {}", lockManagerInstanceId, initialLock.getName(), initialLock);
            locksAcquiredByThisLockManager.put(initialLock.getName(), initialLock);
            notify(new LockAcquired(initialLock, this));
            return Mono.just(initialLock);
        } else {
            // We didn't acquire the lock after all
            log.debug("[{}] Failed to acquire lock '{}' for the first time (insert)", lockManagerInstanceId, initialLock.getName());
            return Mono.empty();
        }
    }


    private Mono<LOCK> updateLock(UOW uow, LOCK timedOutLock, LOCK newLockReadyToBeAcquiredLocally) {
        requireNonNull(uow, "No uow provided");
        requireNonNull(timedOutLock, "No timedOutLock provided");
        requireNonNull(newLockReadyToBeAcquiredLocally, "No newLockReadyToBeAcquiredLocally provided");

        var updatedSuccessfully = lockStorage.updateLockInDB(this, uow, timedOutLock, newLockReadyToBeAcquiredLocally);

        if (updatedSuccessfully) {
            log.debug("[{}] Acquired lock '{}' (update): {}", lockManagerInstanceId, timedOutLock.getName(), newLockReadyToBeAcquiredLocally);
            locksAcquiredByThisLockManager.put(timedOutLock.getName(), newLockReadyToBeAcquiredLocally);
            newLockReadyToBeAcquiredLocally.markAsLocked(newLockReadyToBeAcquiredLocally.getLockAcquiredTimestamp(),
                                                         newLockReadyToBeAcquiredLocally.getLockedByLockManagerInstanceId(),
                                                         newLockReadyToBeAcquiredLocally.getCurrentToken());
            notify(new LockAcquired(newLockReadyToBeAcquiredLocally, this));
            return Mono.just(newLockReadyToBeAcquiredLocally);
        } else {
            // We didn't acquire the lock after all
            log.debug("[{}] Didn't acquire timed out lock '{}', someone else acquired it in the mean time(update): {}",
                      lockManagerInstanceId,
                      timedOutLock.getName(),
                      lockStorage.lookupLockInDB(this, uow, timedOutLock.getName()));
            return Mono.empty();
        }
    }

    private boolean isLockTimedOut(LOCK lock) {
        requireNonNull(lock, "No lock provided");
        var durationSinceLastConfirmation = lock.getDurationSinceLastConfirmation();
        return durationSinceLastConfirmation.compareTo(lockTimeOut) >= 1;
    }

    @Override
    public FencedLock acquireLock(LockName lockName) {
        return _tryAcquireLock(lockName)
                .repeatWhenEmpty(longFlux -> longFlux.doOnNext(aLong -> {
                    try {
                        Thread.sleep(syncAcquireLockPauseIntervalMs);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }))
                .block();
    }

    @Override
    public boolean isLockAcquired(LockName lockName) {
        var lock = unitOfWorkFactory.withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName));
        if (lock.isEmpty()) {
            return false;
        }
        return lock.get().isLocked();
    }

    @Override
    public boolean isLockedByThisLockManagerInstance(LockName lockName) {
        var lock = unitOfWorkFactory.withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName));
        if (lock.isEmpty()) {
            return false;
        }
        return lock.get().isLockedByThisLockManagerInstance();
    }

    @Override
    public boolean isLockAcquiredByAnotherLockManagerInstance(LockName lockName) {
        var lock = unitOfWorkFactory.withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName));
        if (lock.isEmpty()) {
            return false;
        }
        return lock.get().isLocked() && !lock.get().isLockedByThisLockManagerInstance();
    }

    @Override
    public void acquireLockAsync(LockName lockName, LockCallback lockCallback) {
        requireNonNull(lockName, "You must supply a lockName");
        requireNonNull(lockCallback, "You must supply a lockCallback");
        if (!started) {
            throw new IllegalStateException(msg("The {} isn't started", this.getClass().getSimpleName()));
        }

        asyncLockAcquirings.computeIfAbsent(lockName, _lockName -> {
            log.debug("[{}] Starting async Lock acquiring for lock '{}'", lockManagerInstanceId, lockName);
            return asyncLockAcquiringExecutor.scheduleAtFixedRate(() -> {
                                                                      var existingLock = locksAcquiredByThisLockManager.get(lockName);
                                                                      if (existingLock == null) {
                                                                          if (paused) {
                                                                              log.info("[{}] Lock Manager is paused, skipping async acquiring for lock '{}'", lockManagerInstanceId, lockName);
                                                                              return;
                                                                          }

                                                                          var lock = tryAcquireLock(lockName);
                                                                          if (lock.isPresent()) {
                                                                              log.debug("[{}] Async Acquired lock '{}'", lockManagerInstanceId, lockName);
                                                                              var fencedLock = (LOCK) lock.get();
                                                                              fencedLock.registerCallback(lockCallback);
                                                                              locksAcquiredByThisLockManager.put(lockName, fencedLock);
                                                                              lockCallback.lockAcquired(lock.get());
                                                                          } else {
                                                                              if (log.isTraceEnabled()) {
                                                                                  log.trace("[{}] Couldn't async Acquire lock '{}' as it is acquired by another Lock Manager instance: {}",
                                                                                            lockManagerInstanceId, lockName, lookupLock(lockName));
                                                                              }
                                                                          }
                                                                      } else if (!existingLock.isLockedByThisLockManagerInstance()) {
                                                                          log.debug("[{}] Noticed that lock '{}' isn't locked by this Lock Manager instance anymore. Releasing the lock",
                                                                                    lockManagerInstanceId,
                                                                                    lockName);
                                                                          locksAcquiredByThisLockManager.remove(lockName);
                                                                          lockCallback.lockReleased(existingLock);
                                                                      }
                                                                  },
                                                                  0,
                                                                  lockConfirmationInterval.toMillis(),
                                                                  TimeUnit.MILLISECONDS);
        });
    }

    @Override
    public void cancelAsyncLockAcquiring(LockName lockName) {
        requireNonNull(lockName, "You must supply a lockName");
        var scheduledFuture = asyncLockAcquirings.remove(lockName);
        if (scheduledFuture != null) {
            log.debug("[{}] Canceling async Lock acquiring for lock '{}'", lockManagerInstanceId, lockName);
            scheduledFuture.cancel(true);
            var acquiredLock = locksAcquiredByThisLockManager.remove(lockName);
            if (acquiredLock.isLockedByThisLockManagerInstance()) {
                log.debug("[{}] Releasing Lock due to cancelling the lock acquiring '{}'", lockManagerInstanceId, lockName);
                acquiredLock.release();
            }
        }
    }

    @Override
    public String getLockManagerInstanceId() {
        return lockManagerInstanceId;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "lockManagerInstanceId='" + lockManagerInstanceId + '\'' +
                '}';
    }

    public Long getUninitializedTokenValue() {
        return lockStorage.getUninitializedTokenValue();
    }

    public long getInitialTokenValue() {
        return lockStorage.getInitialTokenValue();
    }

    public void deleteAllLocksInDB() {
        unitOfWorkFactory.usingUnitOfWork(uow -> lockStorage.deleteAllLocksInDB(this, uow));
    }
}
