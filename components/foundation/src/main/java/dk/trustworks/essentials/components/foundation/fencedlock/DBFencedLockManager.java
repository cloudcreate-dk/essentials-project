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

package dk.trustworks.essentials.components.foundation.fencedlock;

import dk.trustworks.essentials.components.foundation.IOExceptionUtil;
import dk.trustworks.essentials.components.foundation.fencedlock.FencedLockEvents.*;
import dk.trustworks.essentials.components.foundation.transaction.*;
import dk.trustworks.essentials.reactive.*;
import dk.trustworks.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.trustworks.essentials.shared.functional.*;
import dk.trustworks.essentials.shared.network.Network;
import org.slf4j.*;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static dk.trustworks.essentials.shared.Exceptions.rethrowIfCriticalError;
import static dk.trustworks.essentials.shared.FailFast.requireNonNull;
import static dk.trustworks.essentials.shared.MessageFormatter.msg;


/**
 * Common super base class for implementing persistent/durable {@link FencedLockManager}'s
 *
 * @param <UOW>  the type of {@link UnitOfWork} required
 * @param <LOCK> the concrete type of {@link DBFencedLock} used
 */
public abstract class DBFencedLockManager<UOW extends UnitOfWork, LOCK extends DBFencedLock> implements FencedLockManager {
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

    private final UnitOfWorkFactory<? extends UOW> unitOfWorkFactory;
    private final Optional<EventBus>               eventBus;
    private final ReentrantLock                    reentrantLock = new ReentrantLock(true);
    private final boolean                          releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation;

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
    private   ScheduledFuture<?>       confirmationScheduledFuture;

    /**
     * @param lockStorage                                                    the lock storage used for the lock manager
     * @param unitOfWorkFactory                                              the {@link UnitOfWork} factory
     * @param lockManagerInstanceId                                          The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param lockTimeOut                                                    the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval                                       how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     * @param releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation Should {@link FencedLock}'s acquired by this {@link FencedLockManager} be released in case calls to {@link FencedLockStorage#confirmLockInDB(DBFencedLockManager, UnitOfWork, DBFencedLock, OffsetDateTime)} fails
     *                                                                       with an exception where {@link IOExceptionUtil#isIOException(Throwable)} returns true -
     *                                                                       If releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation is true, then {@link FencedLock}'s will be released locally,
     *                                                                       otherwise we will retain the {@link FencedLock}'s as locked.
     * @param eventBus                                                       optional {@link LocalEventBus} where {@link FencedLockEvents} will be published
     */
    protected DBFencedLockManager(FencedLockStorage<UOW, LOCK> lockStorage,
                                  UnitOfWorkFactory<? extends UOW> unitOfWorkFactory,
                                  Optional<String> lockManagerInstanceId,
                                  Duration lockTimeOut,
                                  Duration lockConfirmationInterval,
                                  boolean releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation,
                                  Optional<EventBus> eventBus) {
        requireNonNull(lockManagerInstanceId, "No lockManagerInstanceId option provided");

        this.lockStorage = requireNonNull(lockStorage, "No lockStorage provided");
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory provided");
        this.lockManagerInstanceId = requireNonNull(lockManagerInstanceId.orElseGet(Network::hostName), "Couldn't resolve a LockManager instanceId");
        this.lockTimeOut = requireNonNull(lockTimeOut, "No lockTimeOut value provided");
        this.lockConfirmationInterval = requireNonNull(lockConfirmationInterval, "No lockConfirmationInterval value provided");
        if (lockConfirmationInterval.compareTo(lockTimeOut) >= 1) {
            throw new IllegalArgumentException(msg("lockConfirmationInterval {} duration MUST not be larger than the lockTimeOut {} duration, because locks will then always timeout",
                                                   lockConfirmationInterval,
                                                   lockTimeOut));
        }
        this.releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation = releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation;
        this.eventBus = requireNonNull(eventBus, "No eventBus option provided");

        locksAcquiredByThisLockManager = new ConcurrentHashMap<>();
        asyncLockAcquirings = new ConcurrentHashMap<>();


        log.info("[{}] Initializing '{}' using storage '{}' and lockConfirmationInterval: {} ms, lockTimeOut: {} ms",
                 this.lockManagerInstanceId,
                 this.getClass().getName(),
                 lockStorage.getClass().getName(),
                 lockConfirmationInterval.toMillis(),
                 lockTimeOut.toMillis());
        usingUnitOfWork(uow -> lockStorage.initializeLockStorage(this, uow),
                        e -> {
                            throw new IllegalStateException(msg("[{}] Failed to initialize lock storage", this.lockManagerInstanceId), e);
                        });
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

            confirmationScheduledFuture = lockConfirmationExecutor.scheduleAtFixedRate(this::confirmAllLocallyAcquiredLocks,
                                                                                       lockConfirmationInterval.toMillis(),
                                                                                       lockConfirmationInterval.toMillis(),
                                                                                       TimeUnit.MILLISECONDS);


            started = true;
            log.info("[{}] Started lock manager", lockManagerInstanceId);
            notify(new FencedLockManagerStarted(this));
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
        if (locksAcquiredByThisLockManager.isEmpty()) {
            log.debug("[{}] No locks to confirm for this Lock Manager instance", lockManagerInstanceId);
            return;
        }

        if (paused) {
            log.info("[{}] Lock Manager is paused, skipping confirmAllLocallyAcquiredLocks", lockManagerInstanceId);
            return;
        }
        try {
            reentrantLock.lock();
            var numberOfLocallyAcquiredLocksBeforeConfirmation = locksAcquiredByThisLockManager.size();
            if (log.isTraceEnabled()) {
                log.trace("[{}] Confirming {} locks acquired by this Lock Manager Instance: {}", lockManagerInstanceId, numberOfLocallyAcquiredLocksBeforeConfirmation, locksAcquiredByThisLockManager.keySet());
            } else {
                log.debug("[{}] Confirming {} locks acquired by this Lock Manager Instance", lockManagerInstanceId, numberOfLocallyAcquiredLocksBeforeConfirmation);
            }
            var confirmedTimestamp = OffsetDateTime.now(Clock.systemUTC());
            usingUnitOfWork(uow -> {
                locksAcquiredByThisLockManager.forEach((lockName, fencedLock) -> {
                    if (fencedLock.getLockedByLockManagerInstanceId() == null) {
                        log.debug("[{}] Skipping confirming lock '{}' since lockedByLockManagerInstanceId is NULL: {}", lockManagerInstanceId, fencedLock.getName(), fencedLock);
                        return;
                    }
                    try {
                        log.trace("[{}] Attempting to confirm lock '{}': {}", lockManagerInstanceId, fencedLock.getName(), fencedLock);
                        boolean confirmedWithSuccess = false;
                        try {
                            confirmedWithSuccess = lockStorage.confirmLockInDB(this, uow, fencedLock, confirmedTimestamp);
                        } catch (Exception e) {
                            if (IOExceptionUtil.isIOException(e)) {
                                if (releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation) {
                                    log.debug(msg("[{}] IO related failure while attempting to perform confirmLockInDB for '{}' - will release lock: {}", lockManagerInstanceId, fencedLock.getName(), fencedLock), e);
                                } else {
                                    log.debug(msg("[{}] IO related failure while attempting to perform confirmLockInDB for '{}' - will retain lock: {}", lockManagerInstanceId, fencedLock.getName(), fencedLock), e);
                                    return;
                                }
                            } else {
                                log.error(msg("[{}] Technical failure while attempting to perform confirmLockInDB for '{}': {}", lockManagerInstanceId, fencedLock.getName(), fencedLock), e);
                            }
                        }
                        if (confirmedWithSuccess) {
                            fencedLock.markAsConfirmed(confirmedTimestamp);
                            log.debug("[{}] Confirmed lock '{}': {}", lockManagerInstanceId, fencedLock.getName(), fencedLock);
                            notify(new LockAcquired(fencedLock, this));
                        } else {
                            // We failed to confirm this lock, someone must have taken over the lock in the meantime
                            log.info("[{}] Failed to confirm lock '{}': {}", lockManagerInstanceId, fencedLock.getName(), fencedLock);
                            try {
                                fencedLock.release();
                            } catch (Exception e) {
                                log.error(msg("[{}] Failed to release lock '{}'", lockManagerInstanceId, fencedLock.getName()), e);
                            }
                        }
                    } catch (Exception e) {
                        log.error(msg("[{}] Technical failure while trying to confirm lock '{}'", lockManagerInstanceId, fencedLock.getName()), e);
                    }
                });
            }, e -> {
                if (IOExceptionUtil.isIOException(e)) {
                    log.debug("[{}] Failed to acknowledge the {} locks acquired by this Lock Manager Instance", lockManagerInstanceId, locksAcquiredByThisLockManager.size(), e);
                } else {
                    log.error("[{}] Failed to acknowledge the {} locks acquired by this Lock Manager Instance", lockManagerInstanceId, locksAcquiredByThisLockManager.size(), e);
                }

                if (IOExceptionUtil.isIOException(e) && releaseAcquiredLocksInCaseOfIOExceptionsDuringLockConfirmation) {
                    log.info("[{}] Releasing all locally acquired locks due to IO Exception", lockManagerInstanceId);
                    locksAcquiredByThisLockManager.forEach((lockName, fencedLock) -> {
                        try {
                            fencedLock.release();
                        } catch (Exception ex) {
                            log.error(msg("[{}] Failed to release lock '{}'", lockManagerInstanceId, fencedLock.getName()), ex);
                        }
                    });
                }
            });
            if (log.isTraceEnabled()) {
                log.trace("[{}] Completed confirmation of {} locks acquired by this Lock Manager Instance. Number of Locks acquired locally after confirmation {}: {}",
                          lockManagerInstanceId, numberOfLocallyAcquiredLocksBeforeConfirmation, locksAcquiredByThisLockManager.size(), locksAcquiredByThisLockManager.keySet());
            } else {
                log.debug("[{}] Completed confirmation of {} locks acquired by this Lock Manager Instance. Number of Locks acquired locally after confirmation {}",
                          lockManagerInstanceId, numberOfLocallyAcquiredLocksBeforeConfirmation, locksAcquiredByThisLockManager.size());
            }
        } finally {
            reentrantLock.unlock();
        }
    }


    /**
     * Internal method only to be called by subclasses of {@link DBFencedLockManager} and {@link DBFencedLock}
     *
     * @param lock the lock to be released
     */
    protected void releaseLock(LOCK lock) {
        requireNonNull(lock, "No lock was provided");
        if (locksAcquiredByThisLockManager.containsKey(lock.getName())) {
            log.debug("[{}] Releasing lock '{}': {}", lockManagerInstanceId, lock.getName(), lock);
            var releaseWithSuccess = withUnitOfWork(uow -> lockStorage.releaseLockInDB(this, uow, lock),
                                                    e -> {
                                                        log.error("[{}] Failed to release lock '{}' in DB", lockManagerInstanceId, lock.getName(), e);
                                                        return false;
                                                    });
            log.trace("[{}] Removing {} DB released lock '{}' locally, marking and notifying it as released: {}",
                      lockManagerInstanceId, releaseWithSuccess ? "successfully" : "failed", lock.getName(), lock);
            locksAcquiredByThisLockManager.remove(lock.getName());
            lock.markAsReleased();
            notify(new LockReleased(lock, this));
            if (releaseWithSuccess) {
                log.debug("[{}] Released Lock '{}': {}", lockManagerInstanceId, lock.getName(), lock);
            } else {
                // We didn't release the lock after all, someone else acquired the lock in the meantime
                log.trace("[{}] Checking '{}' lock status in the DB",
                          lockManagerInstanceId, lock.getName());
                usingUnitOfWork(uow -> {
                    lockStorage.lookupLockInDB(this, uow, lock.getName()).ifPresent(lockAcquiredByAnotherLockManager -> {
                        log.debug("[{}] Post release of Lock '{}' DB reported current-owner status: {}", lockManagerInstanceId, lock.getName(), lockAcquiredByAnotherLockManager);
                    });
                }, e -> log.debug("[{}] Post release of Lock '{}' - failed to look-up in the DB which node has acquired the lock", lockManagerInstanceId, lock.getName(), e));
            }
            log.trace("[{}] Completed releasing lock '{}'",
                      lockManagerInstanceId, lock.getName());
        }

    }

    @Override
    public Optional<FencedLock> lookupLock(LockName lockName) {
        requireNonNull(lockName, "No lockName provided");

        if (!started) {
            throw new IllegalStateException(msg("The {} isn't started", this.getClass().getSimpleName()));
        }

        var fencedLock = withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName).map(FencedLock.class::cast),
                                        e -> {
                                            log.trace("[{}] Failed to lookup lock '{}'", lockManagerInstanceId, lockName, e);
                                            return Optional.<FencedLock>empty();
                                        });
        log.trace("[{}] Lookup FencedLock with name '{}' result: {}",
                  lockManagerInstanceId,
                  lockName,
                  fencedLock);
        return fencedLock;
    }

    @Override
    public void stop() {
        if (started) {
            log.info("[{}] Stopping lock manager", lockManagerInstanceId);
            stopping = true;
            if (confirmationScheduledFuture != null) {
                log.debug("[{}] Stopping confirmationScheduledFuture",
                          lockManagerInstanceId);
                confirmationScheduledFuture.cancel(true);
                confirmationScheduledFuture = null;
                log.debug("[{}] Stopped confirmationScheduledFuture",
                          lockManagerInstanceId);
            }

            locksAcquiredByThisLockManager.values().forEach(lock -> {
                log.debug("[{}] Releasing acquired Lock '{}' due to Stopping",
                          lockManagerInstanceId,
                          lock.getName());
                try {
                    lock.release();
                } catch (Exception e) {
                    if (IOExceptionUtil.isIOException(e)) {
                        log.debug(msg("[{}] Failed to release FencedLock with name '{}'",
                                      lockManagerInstanceId,
                                      lock.getName()), e);
                    } else {
                        log.warn(msg("[{}] Failed to release FencedLock with name '{}'",
                                     lockManagerInstanceId,
                                     lock.getName()), e);
                    }
                }
            });
            locksAcquiredByThisLockManager.clear();

            asyncLockAcquirings.forEach((lockName, scheduledFuture) -> {
                log.debug("[{}] Cancelling acquiring of Lock '{}' due to Stopping",
                          lockManagerInstanceId,
                          lockName);

                try {
                    scheduledFuture.cancel(true);
                } catch (Exception e) {
                    if (IOExceptionUtil.isIOException(e)) {
                        log.debug(msg("[{}] Failed to stop acquiring of FencedLock with name '{}'",
                                      lockManagerInstanceId,
                                      lockName), e);
                    } else {
                        log.warn(msg("[{}] Failed to stop acquiring of FencedLock with name '{}'",
                                     lockManagerInstanceId,
                                     lockName), e);
                    }
                }
            });
            asyncLockAcquirings.clear();

            if (asyncLockAcquiringExecutor != null) {
                log.debug("[{}] Shutting down asyncLockAcquiringExecutor",
                          lockManagerInstanceId);
                asyncLockAcquiringExecutor.shutdownNow();
                asyncLockAcquiringExecutor = null;
                log.debug("[{}] Shutdown asyncLockAcquiringExecutor",
                          lockManagerInstanceId);
            }
            if (lockConfirmationExecutor != null) {
                log.debug("[{}] Shutting down lockConfirmationExecutor",
                          lockManagerInstanceId);

                lockConfirmationExecutor.shutdownNow();
                lockConfirmationExecutor = null;
                log.debug("[{}] Shutdown lockConfirmationExecutor",
                          lockManagerInstanceId);
            }

            started = false;
            stopping = false;
            log.info("[{}] Stopped lock manager", lockManagerInstanceId);
            notify(new FencedLockManagerStopped(this));
        } else {
            log.info("[{}] Lock Manager was already stopped", lockManagerInstanceId);
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
        return Optional.ofNullable(
                _tryAcquireLock(lockName)
                        .repeatWhenEmpty(longFlux -> longFlux.delayElements(Duration.ofMillis(syncAcquireLockPauseIntervalMs)))
                        .onErrorReturn(null)
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
            if (alreadyAcquiredLock.isLockedByThisLockManagerInstance()) {
                log.debug("[{}] Returned cached locally acquired lock '{}", lockManagerInstanceId, lockName);
                return Mono.just(alreadyAcquiredLock);
            } else {
                releaseLock(alreadyAcquiredLock);
            }
        }
        return withUnitOfWork(uow -> {
            var lock = lockStorage.lookupLockInDB(this, uow, lockName)
                                  .orElseGet(() -> lockStorage.createUninitializedLock(this, lockName));
            return resolveLock(uow, lock);
        }, e -> {
            log.debug("[{}] Failed to lookup lock '{}'", lockManagerInstanceId, lockName, e);
            return Mono.empty();
        });
    }

    private Mono<LOCK> resolveLock(UOW uow, LOCK existingLock) {
        requireNonNull(uow, "No uow provided");
        requireNonNull(existingLock, "No existingLock provided");

        if (existingLock.isLocked()) {
            if (existingLock.isLockedByThisLockManagerInstance()) {
                log.debug("[{}] Will try to confirm Lock as the DB reports the lock '{}' was already acquired by this JVM node: '{}'", lockManagerInstanceId, existingLock.getName(), existingLock);
            }
            if (isLockTimedOut(existingLock)) {
                // Timed out - let us acquire the lock and update timestamps/tokens
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
            log.debug("[{}] Didn't acquire timed out lock '{}', someone else acquired it or confirmed it in the mean time (update): {}",
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
                .repeatWhenEmpty(longFlux -> longFlux.delayElements(Duration.ofMillis(syncAcquireLockPauseIntervalMs)))
                .onErrorStop()
                .block();
    }

    @Override
    public boolean isLockAcquired(LockName lockName) {
        var lock = withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName),
                                  e -> {
                                      log.debug("[{}] Failed to lookup lock '{}'", lockManagerInstanceId, lockName, e);
                                      return Optional.<FencedLock>empty();
                                  });
        return lock.map(FencedLock::isLocked).orElse(false);
    }

    @Override
    public boolean isLockedByThisLockManagerInstance(LockName lockName) {
        var lock = withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName),
                                  e -> {
                                      log.debug("[{}] Failed to lookup lock '{}'", lockManagerInstanceId, lockName, e);
                                      return Optional.<FencedLock>empty();
                                  });
        return lock.map(FencedLock::isLockedByThisLockManagerInstance).orElse(false);
    }

    @Override
    public boolean isLockAcquiredByAnotherLockManagerInstance(LockName lockName) {
        var lock = withUnitOfWork(uow -> lockStorage.lookupLockInDB(this, uow, lockName),
                                  e -> {
                                      log.debug("[{}] Failed to lookup lock '{}'", lockManagerInstanceId, lockName, e);
                                      return Optional.<FencedLock>empty();
                                  });
        return lock.map(value -> value.isLocked() && !value.isLockedByThisLockManagerInstance()).orElse(false);
    }

    @Override
    public void acquireLockAsync(LockName lockName, LockCallback lockCallback) {
        requireNonNull(lockName, "You must supply a lockName");
        requireNonNull(lockCallback, "You must supply a lockCallback");

        asyncLockAcquirings.computeIfAbsent(lockName, _lockName -> {
            log.debug("[{}] Starting async Lock acquiring for lock '{}'", lockManagerInstanceId, lockName);
            return asyncLockAcquiringExecutor.scheduleAtFixedRate(() -> {
                                                                      try {
                                                                          reentrantLock.lock();
                                                                          if (!started) {
                                                                              return;
                                                                          }
                                                                          var existingLock = locksAcquiredByThisLockManager.get(lockName);
                                                                          if (existingLock == null) {
                                                                              if (paused) {
                                                                                  log.info("[{}] Lock Manager is paused, skipping async acquiring for lock '{}'", lockManagerInstanceId, lockName);
                                                                                  return;
                                                                              }

                                                                              Optional<FencedLock> lock;
                                                                              try {
                                                                                  lock = tryAcquireLock(lockName);
                                                                              } catch (Exception e) {
                                                                                  log.error(msg("[{}] Technical error while performing tryAcquireLock for lock '{}'", lockManagerInstanceId, lockName), e);
                                                                                  return;
                                                                              }
                                                                              if (lock.isPresent()) {
                                                                                  log.debug("[{}] Async Acquired lock '{}'", lockManagerInstanceId, lockName);
                                                                                  var fencedLock = lock.get();
                                                                                  fencedLock.registerCallback(lockCallback);
                                                                                  locksAcquiredByThisLockManager.put(lockName, (LOCK) fencedLock);
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
                                                                      } catch (Exception e) {
                                                                          log.error(msg("[{}] Technical error while trying to acquire lock '{}'", lockManagerInstanceId, lockName), e);
                                                                      } finally {
                                                                          reentrantLock.unlock();
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
            var acquiredLock = locksAcquiredByThisLockManager.get(lockName);
            if (acquiredLock != null) {
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

    /**
     * Return the DB specific uninitialized value for a {@link FencedLock#getCurrentToken()} that has yet to be persisted for the very first time
     *
     * @return the DB specific uninitialized value for a {@link FencedLock#getCurrentToken()} that has yet to be persisted for the very first time
     */
    public Long getUninitializedTokenValue() {
        return lockStorage.getUninitializedTokenValue();
    }

    /**
     * Return the DB specific value for a {@link FencedLock#getCurrentToken()} that is being persisted the very first time
     *
     * @return the DB specific value for a {@link FencedLock#getCurrentToken()} that is being persisted the very first time
     */
    public long getInitialTokenValue() {
        return lockStorage.getInitialTokenValue();
    }

    /**
     * Force delete all locks in the Database (useful for e.g. integration tests) - use with caution!
     */
    public void deleteAllLocksInDB() {
        usingUnitOfWork(uow -> lockStorage.deleteAllLocksInDB(this, uow),
                        e -> {
                            throw new UnitOfWorkException(msg("[{}] Failed to delete all Locks in the lock storage", lockManagerInstanceId), e);
                        }
                       );
    }

    /**
     * <pre>
     * Retrieves all locks currently stored in the database.
     *
     * This method uses a transactional operation to fetch all locks while handling
     * potential errors that may occur during the database operation.
     * </pre>
     *
     * @return a list of all locks found in the database
     * @throws UnitOfWorkException if an error occurs while performing the unit of work
     */
    public List<LOCK> getAllLocksInDB() {
        return withUnitOfWork(uow -> lockStorage.getAllLocksInDB(this, uow),
                e -> {
                    throw new UnitOfWorkException(msg("[{}] Failed to get all Locks in the lock storage", lockManagerInstanceId), e);
                }
        );
    }

    /**
     * <pre>
     * Releases a lock in the database specified by the provided lock name.
     * This method performs the operation as part of a unit of work and handles exceptions
     * that may occur during the process.
     * </pre>
     * @param lockName the name of the lock to be released in the database
     * @return {@code true} if the lock is successfully released, otherwise {@code false}
     * @throws UnitOfWorkException if an error occurs during the unit of work execution
     */
    public boolean releaseLockInDB(LockName lockName) {
        return withUnitOfWork(uow -> {
                    return lockStorage.lookupLockInDB(this, uow, lockName)
                            .map(lock -> lockStorage.releaseLockInDB(this, uow, lock))
                            .orElse(Boolean.FALSE);
                },
                e -> {
                    throw new UnitOfWorkException(msg("[{}] Failed to release Lock {} the lock storage", lockManagerInstanceId, lockName), e);
                }
        );
    }

    /**
     * Use a {@link UnitOfWork} to perform transactional changes
     *
     * @param unitOfWorkConsumer the consumer of the created {@link UnitOfWork}
     * @param onError            The consumer that consumes any exception caused by calling {@link UnitOfWorkFactory#usingUnitOfWork(CheckedConsumer)}
     */
    protected void usingUnitOfWork(CheckedConsumer<UOW> unitOfWorkConsumer, CheckedConsumer<Throwable> onError) {
        reentrantLock.lock();
        try {
            unitOfWorkFactory.usingUnitOfWork(unitOfWorkConsumer::accept);
        } catch (Throwable e) {
            rethrowIfCriticalError(e);
            log.debug(msg("[{}] Technical error performing usingUnitOfWork", lockManagerInstanceId), e);
            try {
                onError.accept(e);
            } catch (Exception subException) {
                throw new UnitOfWorkException(msg("[{}] Technical error while handling onError related to a usingUnitOfWork call that failed with '{}'", lockManagerInstanceId, e.getMessage()), subException);
            }
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Use a {@link UnitOfWork} to perform transactional changes
     *
     * @param unitOfWorkFunction The function that consumes the created {@link UnitOfWork}
     * @param onError            The function that consumes any exception caused by calling {@link UnitOfWorkFactory#withUnitOfWork(CheckedFunction)}
     * @param <R>                the return value from the <code>unitOfWorkFunction</code>
     * @return the result of the calling the <code>unitOfWorkFunction</code>
     */
    protected <R> R withUnitOfWork(CheckedFunction<UOW, R> unitOfWorkFunction, CheckedFunction<Throwable, R> onError) {
        reentrantLock.lock();
        try {
            return unitOfWorkFactory.withUnitOfWork(unitOfWorkFunction::apply);
        } catch (Throwable e) {
            rethrowIfCriticalError(e);
            log.debug(msg("[{}] Technical error performing withUnitOfWork", lockManagerInstanceId), e);
            try {
                return onError.apply(e);
            } catch (Exception subException) {
                throw new UnitOfWorkException(msg("[{}] Technical error handling onError related to a withUnitOfWork call that failed with '{}'", lockManagerInstanceId, e.getMessage()), subException);
            }
        } finally {
            reentrantLock.unlock();
        }
    }
}
