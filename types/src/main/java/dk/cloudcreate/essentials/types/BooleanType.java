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

package dk.cloudcreate.essentials.types;

import dk.cloudcreate.essentials.shared.FailFast;

/**
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link Boolean}.<br>
 * Example concrete implementation of the {@link BooleanType}:
 * <pre>{@code
 * public class FeatureFlagEnabled extends BooleanType<FeatureFlagEnabled> {
 *     public FeatureFlagEnabled(byte value) {
 *         super(value);
 *     }
 *
 *     public static FeatureFlagEnabled of(byte value) {
 *         return new FeatureFlagEnabled(value);
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link BooleanType} implementation
 */
public abstract class BooleanType<CONCRETE_TYPE extends BooleanType<CONCRETE_TYPE>> implements SingleValueType<Boolean, CONCRETE_TYPE> {
    private final Boolean value;

    public BooleanType(Boolean value) {
        this.value = FailFast.requireNonNull(value, "You must provide a value");
    }

    @Override
    public int compareTo(CONCRETE_TYPE o) {
        return value.compareTo(o.value());
    }

    @Override
    public Boolean value() {
        return value;
    }
}
