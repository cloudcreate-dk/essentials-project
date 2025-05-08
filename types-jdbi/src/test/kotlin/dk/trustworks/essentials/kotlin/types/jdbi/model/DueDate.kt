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

package dk.trustworks.essentials.kotlin.types.jdbi.model

import dk.trustworks.essentials.kotlin.types.LocalDateValueType
import dk.trustworks.essentials.kotlin.types.jdbi.LocalDateValueTypeArgumentFactory
import dk.trustworks.essentials.kotlin.types.jdbi.LocalDateValueTypeColumnMapper
import java.time.LocalDate

@JvmInline
value class DueDate(override val value: LocalDate) : LocalDateValueType<DueDate> {
    companion object {
        fun of(value: LocalDate): DueDate {
            return DueDate(value)
        }

        fun now(): DueDate {
            return DueDate(LocalDate.now())
        }
    }
}

class DueDateArgumentFactory : LocalDateValueTypeArgumentFactory<DueDate>() {
}

class DueDateColumnMapper : LocalDateValueTypeColumnMapper<DueDate>() {
}
