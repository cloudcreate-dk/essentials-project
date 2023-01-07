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

package dk.cloudcreate.essentials.shared.time;

import dk.cloudcreate.essentials.shared.FailFast;

import java.time.Duration;

public class Timing {
    /**
     * Description of the timing - typically a description of what was timed/measured
     */
    public final String   description;
    /**
     * How long did the operation performed take
     */
    public final Duration duration;

    /**
     * Create a new {@link Timing}
     *
     * @param description Description of the timing - typically a description of what was timed/measured
     * @param duration    How long did the operation performed take
     * @return the {@link Timing}
     */
    public static Timing of(String description, Duration duration) {
        return new Timing(description, duration);
    }

    /**
     * Create a new {@link Timing}
     *
     * @param duration    How long did the operation performed take
     * @return the {@link Timing}
     */
    public static Timing of(Duration duration) {
        return new Timing(duration);
    }

    /**
     * @param description Description of the timing - typically a description of what was timed/measured
     * @param duration    How long did the operation performed take
     */
    public Timing(String description, Duration duration) {
        this.description = description;
        this.duration = FailFast.requireNonNull(duration, "duration is null");
    }

    /**
     * @param duration    How long did the operation performed take
     */
    public Timing(Duration duration) {
        this.duration = FailFast.requireNonNull(duration, "duration is null");
        this.description = null;
    }

    /**
     * Description of the timing - typically what was timed/measured
     *
     * @return Description of the timing - typically what was timed/measured
     */
    public String getDescription() {
        return description;
    }

    /**
     * How long did the operation performed take
     *
     * @return How long did the operation performed take
     */
    public Duration getDuration() {
        return duration;
    }


    @Override
    public String toString() {
        return "Timing {" +
                "duration=" + (duration.toMillis() < 10000 ? duration.toMillis() + " ms" : duration.toSeconds() + " s") +
                (description != null ? ", description='" + description + "'" : "") +
                '}';
    }
}
