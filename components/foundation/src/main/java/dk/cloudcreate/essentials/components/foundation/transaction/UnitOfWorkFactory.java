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

package dk.cloudcreate.essentials.components.foundation.transaction;

import dk.cloudcreate.essentials.shared.functional.*;
import org.slf4j.*;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * This interface creates a {@link UnitOfWork}
 *
 * @param <UOW> the {@link UnitOfWork} sub-type returned by the {@link UnitOfWorkFactory}
 */
public interface UnitOfWorkFactory<UOW extends UnitOfWork> {
    Logger unitOfWorkLog = LoggerFactory.getLogger(UnitOfWorkFactory.class);

    /**
     * Get a required active {@link UnitOfWork}
     *
     * @return the active {@link UnitOfWork}
     * @throws NoActiveUnitOfWorkException if the is no active {@link UnitOfWork}
     */
    UOW getRequiredUnitOfWork();

    /**
     * Get the currently active {@link UnitOfWork} or create a new {@link UnitOfWork}
     * if one is missing
     *
     * @return a {@link UnitOfWork}
     */
    UOW getOrCreateNewUnitOfWork();

    /**
     * Works just like {@link #usingUnitOfWork(CheckedConsumer)} except the action isn't provided an instance of the {@link UnitOfWork}<br>
     * The code in the action can always access the {@link UnitOfWork} by calling {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     *
     * @param action the action that's performed in a {@link UnitOfWork}
     */
    default void usingUnitOfWork(CheckedRunnable action) {
        usingUnitOfWork(uow -> action.run());
    }

    /**
     * Works just like {@link #withUnitOfWork(CheckedFunction)} except the action isn't provided an instance of the {@link UnitOfWork}<br>
     * The code in the action can always access the {@link UnitOfWork} by calling {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     *
     * @param action the action that's performed in a {@link UnitOfWork}
     * @param <R>    the return type from the action
     * @return the result of running the action in a {@link UnitOfWork}
     */
    default <R> R withUnitOfWork(CheckedSupplier<R> action) {
        return withUnitOfWork(uow -> action.get());
    }

    /**
     * Run the <code>unitOfWorkConsumer</code> in a {@link UnitOfWork}<br>
     * It works in two different ways:<br>
     * 1.<br>
     * If no existing {@link UnitOfWork} then a new {@link UnitOfWork} is created and started, the <code>unitOfWorkConsumer</code> is called with this {@link UnitOfWork}<br>
     * When the <code>unitOfWorkConsumer</code> has completed, without throwing an exception, then the created {@link UnitOfWork} is committed.<br>
     * In case an exception was thrown then the created {@link UnitOfWork} is rolledback.<br>
     * <br>
     * 2.<br>
     * If there's already an existing {@link UnitOfWork} then the <code>unitOfWorkConsumer</code> joins in with
     * existing {@link UnitOfWork} and the <code>unitOfWorkConsumer</code> is called with the existing {@link UnitOfWork}<br>
     * When the <code>unitOfWorkConsumer</code> has completed, without throwing an exception, then the existing {@link UnitOfWork} is NOT committed, this is instead left to the original creator of the {@link UnitOfWork} to do<br>
     * In case an exception was thrown then the existing {@link UnitOfWork} is marked as rollback only.<br>
     *
     * @param unitOfWorkConsumer the consumer that's called with a {@link UnitOfWork}
     */
    default void usingUnitOfWork(CheckedConsumer<UOW> unitOfWorkConsumer) {
        requireNonNull(unitOfWorkConsumer, "No unitOfWorkConsumer provided");

        var existingUnitOfWork = getCurrentUnitOfWork();
        var unitOfWork = existingUnitOfWork.orElseGet(() -> {
            unitOfWorkLog.debug("Creating a new UnitOfWork for this usingUnitOfWork(CheckedConsumer) method call as there wasn't an existing UnitOfWork");
            return getOrCreateNewUnitOfWork();
        });
        existingUnitOfWork.ifPresent(uow -> unitOfWorkLog.debug("NestedUnitOfWork: Reusing existing UnitOfWork for this usingUnitOfWork(CheckedConsumer) method call"));
        try {
            unitOfWorkConsumer.accept(unitOfWork);
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Committing the UnitOfWork created by this usingUnitOfWork(CheckedConsumer) method call");
                unitOfWork.commit();
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Won't commit the UnitOfWork as it wasn't created by this usingUnitOfWork(CheckedConsumer) method call");
            }
        } catch (Exception e) {
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Rolling back the UnitOfWork created by this usingUnitOfWork(CheckedConsumer) method call");
                unitOfWork.rollback(e);
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Marking UnitOfWork as rollback only as it wasn't created by this usingUnitOfWork(CheckedConsumer) method call");
                unitOfWork.markAsRollbackOnly(e);
            }
            throw new UnitOfWorkException(e);
        }
    }

    /**
     * Run the <code>unitOfWorkFunction</code> in a {@link UnitOfWork}<br>
     * It works in two different ways:<br>
     * 1.<br>
     * If no existing {@link UnitOfWork} then a new {@link UnitOfWork} is created and started, the <code>unitOfWorkFunction</code> is called with this {@link UnitOfWork}<br>
     * When the <code>unitOfWorkFunction</code> has completed, without throwing an exception, then the created {@link UnitOfWork} is committed.<br>
     * In case an exception was thrown then the created {@link UnitOfWork} is rolledback.<br>
     * <br>
     * 2.<br>
     * If there's already an existing {@link UnitOfWork} then the <code>unitOfWorkFunction</code> joins in with
     * existing {@link UnitOfWork} and the <code>unitOfWorkFunction</code> is called with the existing {@link UnitOfWork}<br>
     * When the <code>unitOfWorkFunction</code> has completed, without throwing an exception, then the existing {@link UnitOfWork} is NOT committed, this is instead left to the original creator of the {@link UnitOfWork} to do<br>
     * In case an exception was thrown then the existing {@link UnitOfWork} is marked as rollback only.<br>
     *
     * @param unitOfWorkFunction the consumer that's called with a {@link UnitOfWork}
     */
    default <R> R withUnitOfWork(CheckedFunction<UOW, R> unitOfWorkFunction) {
        requireNonNull(unitOfWorkFunction, "No unitOfWorkFunction provided");
        var existingUnitOfWork = getCurrentUnitOfWork();
        var unitOfWork = existingUnitOfWork.orElseGet(() -> {
            unitOfWorkLog.debug("Creating a new UnitOfWork for this withUnitOfWork(CheckedFunction) method call as there wasn't an existing UnitOfWork");
            return getOrCreateNewUnitOfWork();
        });
        existingUnitOfWork.ifPresent(uow -> unitOfWorkLog.debug("NestedUnitOfWork: Reusing existing UnitOfWork for this withUnitOfWork(CheckedFunction) method call"));
        try {
            var result = unitOfWorkFunction.apply(unitOfWork);
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Committing the UnitOfWork created by this withUnitOfWork(CheckedFunction) method call");
                unitOfWork.commit();
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Won't commit the UnitOfWork as it wasn't created by this withUnitOfWork(CheckedFunction) method call");
            }

            return result;
        } catch (Exception e) {
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Rolling back the UnitOfWork created by this withUnitOfWork(CheckedFunction) method call");
                unitOfWork.rollback(e);
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Marking UnitOfWork as rollback only as it wasn't created by this withUnitOfWork(CheckedFunction) method call");
                unitOfWork.markAsRollbackOnly(e);
            }
            throw new UnitOfWorkException(e);
        }
    }

    /**
     * Get the currently active {@link UnitOfWork}
     *
     * @return the currently active {@link UnitOfWork} wrapped in an {@link Optional} or {@link Optional#empty()} in case there isn't a currently active {@link UnitOfWork}
     */
    Optional<UOW> getCurrentUnitOfWork();
}
