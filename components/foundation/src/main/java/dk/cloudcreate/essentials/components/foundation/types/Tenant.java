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

package dk.cloudcreate.essentials.components.foundation.types;

import dk.cloudcreate.essentials.types.*;

/**
 * Marker interface for types that are used to distinguish between Tenant
 * specific data in a data store.<br>
 * A tenant can be as simple as a {@link SingleValueType} or it can be more
 * complex such as including both Tenant-Id and a {@link CountryCode} denoting
 * the country where the Tenant data is owned.<br>
 * The serialization of the tenant is always the {@link Object#toString()} value,
 * and it is deserialized by using a default single string argument constructor
 */
public interface Tenant {
}
