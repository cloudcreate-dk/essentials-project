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

package dk.trustworks.essentials.shared;

import java.io.*;
import java.util.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

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

    /**
     * Rethrows a {@link Throwable} (or anything in its causal chain), using {@link #sneakyThrow(Throwable)},
     * if the exception represents a critical, unrecoverable error that should not be caught/handled
     * by normal application logic.
     * <p>
     * This includes:
     * <ul>
     *   <li>{@link VirtualMachineError} (e.g., {@link OutOfMemoryError}, {@link StackOverflowError})</li>
     *   <li>{@link ThreadDeath}</li>
     *   <li>{@link LinkageError}</li>
     * </ul>
     * <p>
     * In addition, if such an error appears as a suppressed exception or as the
     * cause of another exception, it is also treated as critical.
     *
     * @param throwable the {@link Throwable} to check
     * @see #isCriticalError(Throwable)
     */
    public static <T extends Throwable> void rethrowIfCriticalError(T throwable) {
        requireNonNull(throwable, "You must supply an exception");
        if (isCriticalError(throwable)) {
            Exceptions.sneakyThrow(throwable);
        }
    }

    /**
     * Determines whether a {@link Throwable} (or anything in its causal chain)
     * represents a critical, unrecoverable error that should not be caught/handled
     * by normal application logic.
     * <p>
     * This includes:
     * <ul>
     *   <li>{@link VirtualMachineError} (e.g., {@link OutOfMemoryError}, {@link StackOverflowError})</li>
     *   <li>{@link ThreadDeath}</li>
     *   <li>{@link LinkageError}</li>
     * </ul>
     * <p>
     * In addition, if such an error appears as a suppressed exception or as the
     * cause of another exception, it is also treated as critical.
     *
     * @param t the {@link Throwable} to check
     * @return {@code true} if the throwable (or any suppressed/caused throwable)
     * is considered critical; {@code false} otherwise
     */
    public static boolean isCriticalError(Throwable t) {
        return isCriticalErrorRecursive(t, new HashSet<>());
    }

    /**
     * Recursive helper method that traverses causes and suppressed exceptions,
     * keeping track of already visited {@code Throwable} instances to avoid
     * infinite loops if there is a cyclical cause/suppressed chain.
     */
    private static boolean isCriticalErrorRecursive(Throwable t, Set<Throwable> visited) {
        if (t == null || visited.contains(t)) {
            return false;
        }
        visited.add(t);

        // Is this exception one of the critical types
        if (t instanceof VirtualMachineError ||
                t instanceof ThreadDeath ||
                t instanceof LinkageError) {
            return true;
        }

        // Check suppressed exceptions
        for (var suppressed : t.getSuppressed()) {
            if (isCriticalErrorRecursive(suppressed, visited)) {
                return true;
            }
        }

        // Check the cause of this exception
        return isCriticalErrorRecursive(t.getCause(), visited);
    }
}