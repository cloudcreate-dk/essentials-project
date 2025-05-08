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

package dk.trustworks.essentials.kotlin.types

import java.io.Serializable
import java.time.LocalDate

/**
 * Semantic [LocalDate] value type interface that allows infrastructure code to easily access the [value] of the value type
 */
interface LocalDateValueType<SELF: LocalDateValueType<SELF>> : Serializable, Comparable<SELF> {
    val value: LocalDate

    override fun compareTo(other: SELF): Int {
        return this.value.compareTo(other.value)
    }
}