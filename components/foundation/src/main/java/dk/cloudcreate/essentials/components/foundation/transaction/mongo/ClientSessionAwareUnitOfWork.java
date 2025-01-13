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

package dk.cloudcreate.essentials.components.foundation.transaction.mongo;

import com.mongodb.client.ClientSession;
import dk.cloudcreate.essentials.components.foundation.transaction.*;

/**
 * Version of UnitOfWork that's aware of the {@link ClientSession} associated with the current {@link UnitOfWork}<br>
 * Will likely be removed in the future as having access to the ClientSession doesn't seem to be needed
 */
public interface ClientSessionAwareUnitOfWork extends UnitOfWork {
    /**
     * Get the {@link ClientSession} handle associated with the current {@link UnitOfWork}<br>
     *
     * @return the {@link ClientSession} handle associated with the current {@link UnitOfWork}
     * @throws UnitOfWorkException If the transaction isn't active
     */
//    ClientSession clientSession();
}
