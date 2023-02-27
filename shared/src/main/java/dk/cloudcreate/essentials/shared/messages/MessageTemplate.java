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

package dk.cloudcreate.essentials.shared.messages;

/**
 * Marker interface for a Message Template
 */
public interface MessageTemplate {
    /**
     * The message key for this {@link MessageTemplate}. Message key's can be used for multiple purposes:
     * <ul>
     *     <li>A Message key is the identifier for a message or error (e.g. INVENTORY_ITEM.OUT_OF_STOCK)</li>
     *     <li>A Message key can be used to lookup translations (e.g. in a Resource bundle)</li>
     * </ul>
     *
     * @return the message key for this {@link MessageTemplate}
     */
    String getKey();

    /**
     * The default Message for the given message key<br>
     * E.g. say the {@link #getKey()} is <code>SALES.INVENTORY_ITEM.OUT_OF_STOCK</code> and the concrete {@link MessageTemplate} is a {@link MessageTemplate1}
     * then a default english message for this key could be: <code>Inventory item {0} is out of Stock</code>, which would be defined as:
     * <pre>{@code
     * MessageTemplate1<ProductName> INVENTORY_OUT_OF_STOCK = MessageTemplates.key1("INVENTORY_ITEM.OUT_OF_STOCK", "Inventory item {0} is out of Stock");
     * }</pre>
     *
     * @return the default message for the given message key
     */
    String getDefaultMessage();


}
