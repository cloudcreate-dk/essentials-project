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

/**
 * The status of a {@link UnitOfWork}
 */
public enum UnitOfWorkStatus {
    /**
     * The {@link UnitOfWork} has just been created, but not yet {@link #Started}
     */
    Ready(false),
    /**
     * The {@link UnitOfWork} has been started (i.e. any underlying transaction has begun)
     */
    Started(false),
    /**
     * The {@link UnitOfWork} has been committed<br>
     */
    Committed(true),
    /**
     * The {@link UnitOfWork} has been rolled back<br>
     */
    RolledBack(true),
    /**
     * The {@link UnitOfWork} marked as it MUST be rolled back at the end of the transaction
     */
    MarkedForRollbackOnly(false);

    public final boolean isCompleted;

    UnitOfWorkStatus(boolean isCompleted) {
        this.isCompleted = isCompleted;
    }

    public boolean isCompleted() {
        return isCompleted;
    }
}
