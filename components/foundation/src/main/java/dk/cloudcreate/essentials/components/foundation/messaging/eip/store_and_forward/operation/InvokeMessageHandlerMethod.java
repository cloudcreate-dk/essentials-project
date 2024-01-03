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

package dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.operation;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.Message;

import java.lang.reflect.Method;

public class InvokeMessageHandlerMethod {
    public final  Method   methodToInvoke;
    private final Message  message;
    private final Object   messagPayload;
    public final  Object   invokeMethodOn;
    public final  Class<?> resolvedInvokeMethodWithArgumentOfType;

    public InvokeMessageHandlerMethod(Method methodToInvoke, Message message, Object messagPayload, Object invokeMethodOn, Class<?> resolvedInvokeMethodWithArgumentOfType) {
        this.methodToInvoke = methodToInvoke;
        this.message = message;
        this.messagPayload = messagPayload;
        this.invokeMethodOn = invokeMethodOn;
        this.resolvedInvokeMethodWithArgumentOfType = resolvedInvokeMethodWithArgumentOfType;
    }

    @Override
    public String toString() {
        return "InvokeMessageHandlerMethod{" +
                "methodToInvoke=" + methodToInvoke +
                ", message= {" + message + "}" +
                ", resolvedInvokeMethodWithArgumentOfType=" + resolvedInvokeMethodWithArgumentOfType +
                ", invokeMethodOn=" + invokeMethodOn +
                '}';
    }
}
