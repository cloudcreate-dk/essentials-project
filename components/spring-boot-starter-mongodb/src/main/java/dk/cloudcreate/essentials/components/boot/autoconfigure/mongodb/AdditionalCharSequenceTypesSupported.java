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

package dk.cloudcreate.essentials.components.boot.autoconfigure.mongodb;

import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link AdditionalCharSequenceTypesSupported} class represents a collection of additional {@link CharSequenceType} classes
 * that will be supported by the {@link dk.cloudcreate.essentials.types.springdata.mongo.SingleValueTypeConverter}
 * that is registered in {@link EssentialsComponentsConfiguration#mongoCustomConversions(Optional, Optional)}
 */
public final class AdditionalCharSequenceTypesSupported {
    public final List<Class<? extends CharSequenceType<?>>> charSequenceTypes;

    public AdditionalCharSequenceTypesSupported() {
        this(List.of());
    }

    /**
     * Constructs an instance of AdditionalCharSequenceTypesSupported with the given charSequenceTypes.
     *
     * @param charSequenceTypes an array of Class objects representing the additional CharSequenceType classes
     *                          to be supported
     */
    public AdditionalCharSequenceTypesSupported(Class<? extends CharSequenceType<?>>... charSequenceTypes) {
        this(List.of(charSequenceTypes));
    }


    /**
     * Constructs an instance of AdditionalCharSequenceTypesSupported with the given charSequenceTypes.
     *
     * @param charSequenceTypes a list of Class objects representing the additional CharSequenceType classes
     *                          to be supported (must not be null)
     */
    public AdditionalCharSequenceTypesSupported(List<Class<? extends CharSequenceType<?>>> charSequenceTypes) {
        this.charSequenceTypes = requireNonNull(charSequenceTypes, "charSequenceTypes is null");
    }
}
