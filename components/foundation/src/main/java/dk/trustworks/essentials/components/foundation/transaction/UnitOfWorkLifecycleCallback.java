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

package dk.trustworks.essentials.components.foundation.transaction;

import java.util.List;

/**
 * Callback that can be registered with a {@link UnitOfWork} in relation to
 * one of more Resources (can e.g. be an Aggregate). When the {@link UnitOfWork} is committed
 * or rolledback the {@link UnitOfWorkLifecycleCallback} will be called with all the Resources that have been associated with it through
 * the {@link UnitOfWork}
 */
public interface UnitOfWorkLifecycleCallback<RESOURCE_TYPE> {
    enum BeforeCommitProcessingStatus {
        REQUIRED,
        COMPLETED
    }

    BeforeCommitProcessingStatus beforeCommit(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources);

    void afterCommit(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources);

    void beforeRollback(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources, Throwable causeOfTheRollback);

    void afterRollback(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources, Throwable causeOfTheRollback);
}
