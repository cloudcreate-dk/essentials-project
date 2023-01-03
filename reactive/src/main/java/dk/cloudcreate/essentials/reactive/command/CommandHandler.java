/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.reactive.command;

/**
 * Common interface for all Command message handlers<br>
 * A command handler can choose support to 1 or more command types
 *
 * @see AnnotatedCommandHandler
 */
public interface CommandHandler {
    /**
     * This method is called by the {@link LocalCommandBus} to check if this concrete {@link CommandHandler} supports a given command type
     *
     * @param commandType the type of command being processed by the {@link LocalCommandBus}
     * @return true if this command handler can handle a command of the given type - otherwise it must return false
     */
    boolean canHandle(Class<?> commandType);

    /**
     * This method is called by the {@link LocalCommandBus} after it has determined that only this {@link CommandHandler} instance is capable of handling
     * the command (based on the commands type)
     *
     * @param command the command
     * @return the result of handling the command (if any)
     */
    Object handle(Object command);
}
