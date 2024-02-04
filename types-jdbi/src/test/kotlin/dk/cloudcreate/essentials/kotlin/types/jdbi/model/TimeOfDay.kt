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

package dk.cloudcreate.essentials.kotlin.types.jdbi.model

import dk.cloudcreate.essentials.kotlin.types.LocalTimeValueType
import dk.cloudcreate.essentials.kotlin.types.jdbi.LocalTimeValueTypeArgumentFactory
import dk.cloudcreate.essentials.kotlin.types.jdbi.LocalTimeValueTypeColumnMapper
import java.time.LocalTime

@JvmInline
value class TimeOfDay(override val value: LocalTime) : LocalTimeValueType {
    companion object {
        fun of(value: LocalTime): TimeOfDay {
            return TimeOfDay(value)
        }

        fun now(): TimeOfDay {
            return TimeOfDay(LocalTime.now())
        }
    }
}

class TimeOfDayArgumentFactory : LocalTimeValueTypeArgumentFactory<TimeOfDay>() {
}

class TimeOfDayColumnMapper : LocalTimeValueTypeColumnMapper<TimeOfDay>() {
}
