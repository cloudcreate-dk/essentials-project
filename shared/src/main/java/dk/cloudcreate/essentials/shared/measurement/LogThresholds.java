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

package dk.cloudcreate.essentials.shared.measurement;

/**
 * Encapsulates logging threshold values (in milliseconds) for different log levels.
 * <p>
 * When a measured operation’s duration exceeds a given threshold, it is logged at the
 * corresponding log level.
 * Specifically:
 * <ul>
 *   <li><strong>error</strong>: If the duration exceeds this threshold, the operation is logged at ERROR level.</li>
 *   <li><strong>warn</strong>: If the duration exceeds this threshold, the operation is logged at WARN level.</li>
 *   <li><strong>info</strong>: If the duration exceeds this threshold, the operation is logged at INFO level.</li>
 *   <li><strong>debug</strong>: If the duration exceeds this threshold, the operation is logged at DEBUG level.</li>
 * </ul>
 * If none of the thresholds are exceeded and metrics collection and logging is enabled, then the statistics are logged using TRACE level.
 */
public class LogThresholds {
    public final long debug;
    public final long info;
    public final long warn;
    public final long error;

    public static LogThresholds defaultThresholds() {
        return new LogThresholds(25, 100, 500, 2000);
    }

    /**
     * Constructs a new LogThresholds instance.
     * <p>
     * When a measured operation’s duration exceeds a given threshold, it is logged at the
     * corresponding log level.
     * Specifically:
     * <ul>
     *   <li><strong>error</strong>: If the duration exceeds this threshold, the operation is logged at ERROR level.</li>
     *   <li><strong>warn</strong>: If the duration exceeds this threshold, the operation is logged at WARN level.</li>
     *   <li><strong>info</strong>: If the duration exceeds this threshold, the operation is logged at INFO level.</li>
     *   <li><strong>debug</strong>: If the duration exceeds this threshold, the operation is logged at DEBUG level.</li>
     * </ul>
     * If none of the thresholds are exceeded and metrics collection and logging is enabled, then the statistics are logged using TRACE level.
     *
     * @param debug threshold for debug level logging in milliseconds
     * @param info  threshold for info level logging in milliseconds
     * @param warn  threshold for warn level logging in milliseconds
     * @param error threshold for error level logging in milliseconds
     */
    public LogThresholds(long debug, long info, long warn, long error) {
        this.debug = debug;
        this.info = info;
        this.warn = warn;
        this.error = error;
    }

    public long getDebug() {
        return debug;
    }

    public long getInfo() {
        return info;
    }

    public long getWarn() {
        return warn;
    }

    public long getError() {
        return error;
    }
}
