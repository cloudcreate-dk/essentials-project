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

package dk.cloudcreate.essentials.types.springdata.jpa.model;

import dk.cloudcreate.essentials.types.LocalTimeType;

import java.time.LocalTime;

public class TimeOfDay extends LocalTimeType<TimeOfDay> {
    public TimeOfDay(LocalTime value) {
        super(value);
    }

    public static TimeOfDay of(LocalTime value) {
        // Setting nanoSeconds to 0 because LocalTime is stored as hours, minutes and seconds (see https://docs.jboss.org/hibernate/orm/6.2/userguide/html_single/Hibernate_User_Guide.html#basic-temporal)
        return new TimeOfDay(value.withNano(0));
    }

    public static TimeOfDay now() {
        // Setting nanoSeconds to 0 because LocalTime is stored as hours, minutes and seconds
        return new TimeOfDay(LocalTime.now().withNano(0));
    }
}
