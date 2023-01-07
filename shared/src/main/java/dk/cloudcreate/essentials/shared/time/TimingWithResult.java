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

import java.time.Duration;
import java.util.function.Supplier;

/**
 * The result of {@link StopWatch#time(Supplier)}
 *
 * @param <R> the type of result
 */
public class TimingWithResult<R> extends Timing {
    /**
     * The result of the operation performed
     */
    public final R result;


    /**
     * Create a new {@link TimingWithResult}
     *
     * @param result      The result of the operation performed
     * @param description Description of the timing - typically a description of what was timed/measured
     * @param duration    How long did the operation performed take
     * @return the {@link TimingWithResult}
     */
    public TimingWithResult(R result, String description, Duration duration) {
        super(description, duration);
        this.result = result;
    }

    /**
     * Create a new {@link TimingWithResult}
     *
     * @param result      The result of the operation performed
     * @param duration    How long did the operation performed take
     * @return the {@link TimingWithResult}
     */
    public TimingWithResult(R result, Duration duration) {
        super(duration);
        this.result = result;

    }

    /**
     * Create a new {@link TimingWithResult}
     *
     * @param result      The result of the operation performed
     * @param description Description of the timing - typically a description of what was timed/measured
     * @param duration    How long did the operation performed take
     * @param <R>         the type of result
     * @return the {@link TimingWithResult}
     */
    public static <R> TimingWithResult<R> of(R result, String description, Duration duration) {
        return new TimingWithResult<>(result, description, duration);
    }

    /**
     * Create a new {@link TimingWithResult}
     *
     * @param result   The result of the operation performed
     * @param duration How long did the operation performed take
     * @param <R>      the type of result
     * @return the {@link TimingWithResult}
     */
    public static <R> TimingWithResult<R> of(R result, Duration duration) {
        return new TimingWithResult<>(result, duration);
    }

    /**
     * The result of the operation performed
     *
     * @return The result of the operation performed
     */
    public R getResult() {
        return result;
    }


    @Override
    public String toString() {
        return "TimingResult {" +
                "duration=" + (duration.toMillis() < 10000 ? duration.toMillis() + " ms" : duration.toSeconds() + " s") +
                (description != null ? ", description='" + description + "'" : "") +
                "result=" + (result != null ? result.toString() : "null") +
                '}';
    }
}
