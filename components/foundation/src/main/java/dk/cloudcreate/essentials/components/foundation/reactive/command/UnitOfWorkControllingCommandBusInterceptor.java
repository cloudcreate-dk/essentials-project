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

package dk.cloudcreate.essentials.components.foundation.reactive.command;

import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.reactive.command.interceptor.*;
import dk.cloudcreate.essentials.shared.functional.CheckedFunction;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * {@link CommandBusInterceptor} that ensures that each Command is handled within a {@link UnitOfWork}
 * by using the {@link UnitOfWorkFactory#withUnitOfWork(CheckedFunction)}
 */
public class UnitOfWorkControllingCommandBusInterceptor implements CommandBusInterceptor {
    private final UnitOfWorkFactory<? extends UnitOfWork> unitOfWorkFactory;

    public UnitOfWorkControllingCommandBusInterceptor(UnitOfWorkFactory<? extends UnitOfWork> unitOfWorkFactory) {
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory provided");
    }

    @Override
    public Object interceptSend(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        return ensureCommandIsHandledInAUnitOfWork(command, commandBusInterceptorChain);
    }

    @Override
    public Object interceptSendAsync(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        return ensureCommandIsHandledInAUnitOfWork(command, commandBusInterceptorChain);

    }

    @Override
    public void interceptSendAndDontWait(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        ensureCommandIsHandledInAUnitOfWork(command, commandBusInterceptorChain);
    }

    private Object ensureCommandIsHandledInAUnitOfWork(Object command, CommandBusInterceptorChain commandBusInterceptorChain) {
        return unitOfWorkFactory.withUnitOfWork(unitOfWork -> commandBusInterceptorChain.proceed());
    }
}
