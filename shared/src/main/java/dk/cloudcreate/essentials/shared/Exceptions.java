/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.shared;

import java.io.*;
import java.util.HashSet;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public final class Exceptions {
    /**
     * Allows us to sneaky throw a checked exception without having to wrap it<br>
     * Use with caution
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        requireNonNull(t, "You must supply an exception");
        throw (T) t;
    }

    /**
     * Get the stacktrace of the <code>throwable</code> as a String
     *
     * @param throwable the exception
     * @return the <code>throwable</code>'s full stacktrace
     */
    public static String getStackTrace(Throwable throwable) {
        requireNonNull(throwable, "You must specify a throwable");
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Get the root cause (top most parent) of an Exception.
     *
     * @param exception the exception we want the root cause of
     * @return the root cause of the exception - will never be null
     */
    public static Throwable getRootCause(Throwable exception) {
        requireNonNull(exception, "You must supply an exception");

        var visited        = new HashSet<Throwable>();  // Track visited exceptions to prevent infinite loops
        var current        = exception;
        var lastValidCause = current;

        while (current != null) {
            if (!visited.add(current)) {
                break; // Circular reference detected
            }
            lastValidCause = current;
            current = current.getCause();
        }

        return lastValidCause;
    }

    /**
     * Check if the stack trace (including causes) contains an exception of a specified type.
     *
     * @param exception     the root exception
     * @param exceptionType the type of exception to look for
     * @return true if the exception or one of its causes matches the specified type, false otherwise
     */
    public static boolean doesStackTraceContainExceptionOfType(Throwable exception, Class<?> exceptionType) {
        requireNonNull(exception, "You must supply an exception");
        requireNonNull(exceptionType, "You must supply an exception type");

        var visited = new HashSet<Throwable>(); // Track visited exceptions to prevent infinite loops
        var current = exception;

        while (current != null) {
            if (!visited.add(current)) {
                break;  // Circular reference detected
            }
            visited.add(current);

            if (exceptionType.isAssignableFrom(current.getClass())) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }
}