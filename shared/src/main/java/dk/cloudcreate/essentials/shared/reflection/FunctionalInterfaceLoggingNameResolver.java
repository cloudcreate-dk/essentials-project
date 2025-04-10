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

package dk.cloudcreate.essentials.shared.reflection;

import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Utility for resolving human-readable, meaningful names from functional interface implementations - also supports normal classes.
 * <p>
 * This class attempts to provide clear and informative names from lambda expressions and method references
 * for logging and debugging purposes, trying to avoid default obscure names like {@code $$Lambda$123}.
 * <p>
 * It uses several strategies to extract names, in order of preference:
 * <ol>
 *   <li>Functional interface information / class name</li>
 *   <li>Stack trace analysis</li>
 * </ol>
 * <p>
 * Implementation notes:
 * <ul>
 *   <li>Results are cached internally for performance</li>
 *   <li>Handles Java security restrictions gracefully</li>
 *   <li>Thread-safe implementation</li>
 * </ul>
 */
public final class FunctionalInterfaceLoggingNameResolver {
    private static final Logger logger = LoggerFactory.getLogger(FunctionalInterfaceLoggingNameResolver.class);

    /**
     * Key: functional interface implementation class<br>
     * Value: logging-friendly name
     */
    private static final ConcurrentMap<Class<?>, String> loggingNameCache = new ConcurrentHashMap<>();

    private FunctionalInterfaceLoggingNameResolver() {
    }

    /**
     * Resolves a logging‐friendly name for the provided functional interface implementation - also supports normal classes.
     *
     * <p>If the implementation is a lambda expression, this method attempts to extract the underlying
     * implementation class and method via reflection. Otherwise, it falls back to the class's simple name.
     * The result is cached per implementation class.
     *
     * @param handler the functional interface implementation or class instance
     * @return a logging‐friendly name for diagnostic purposes
     * @throws IllegalArgumentException if the handler is null
     */
    public static String resolveLoggingName(Object handler) {
        requireNonNull(handler, "No handler provided");
        var clazz = handler.getClass();
        return loggingNameCache.computeIfAbsent(clazz, clz -> computeLoggingName(handler));
    }

    /**
     * Computes the logging‐friendly name for a functional interface implementation (or a normal class).
     *
     * <p>This method checks if the implementation appears to be a lambda expression (synthetic class
     * or name containing "$$Lambda$"). On failure or for non-lambda classes, the simple class name is used.
     *
     * @param handler the functional interface implementation instance
     * @return the computed logging‐friendly name
     */
    private static String computeLoggingName(Object handler) {
        var clazz = handler.getClass();

        // If it's a lambda or generated proxy class
        if (clazz.isSynthetic() || clazz.getName().contains("$$Lambda$")) {
            var functionalInfo = getFunctionalInterfaceAndMethod(clazz);
            if (functionalInfo != null) {
                return extractClassNameFromLambda(clazz.getSimpleName()) + "::" + functionalInfo.getKey();
            }
            var stackTraceInfo = extractFromStackTrace();
            return Objects.requireNonNullElseGet(stackTraceInfo, clazz::getSimpleName);
        } else {
            // For non-lambda objects, just use class name
            if (clazz.isAnonymousClass()) {
                var enclosingClass = clazz.getEnclosingClass();
                var enclosingName  = enclosingClass != null ? enclosingClass.getSimpleName() : "";

                // Try to get method name for anonymous implementation of functional interface
                var methods = clazz.getDeclaredMethods();
                var functionalMethod = Arrays.stream(methods)
                                             .filter(m -> !m.isBridge() && !m.isSynthetic())
                                             .findFirst()
                                             .orElse(null);

                var methodName = functionalMethod != null ? functionalMethod.getName() : "";
                return enclosingName + "$Anonymous" + (methodName.isEmpty() ? "" : "::" + methodName);
            }

            // For regular classes, use simple name
            return clazz.getSimpleName();
        }
    }

    public static String extractClassNameFromLambda(String lambdaString) {
        if (!lambdaString.contains("$$Lambda$")) {
            return lambdaString;
        }

        int lambdaIndex = lambdaString.indexOf("$$Lambda$");
        return lambdaString.substring(0, lambdaIndex);
    }

    /**
     * Helper method to get both the functional interface and its abstract method.
     * Returns a Map.Entry with the interface name as key and the method as value, or null.
     */
    private static Map.Entry<String, java.lang.reflect.Method> getFunctionalInterfaceAndMethod(Class<?> lambdaClass) {
        try {
            for (var _interface : lambdaClass.getInterfaces()) {
                // Check if it's a functional interface
                var methods = _interface.getMethods();
                var functionalMethod = Arrays.stream(methods)
                                             .filter(m -> !m.isDefault() && !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                                             .findFirst()
                                             .orElse(null);

                if (functionalMethod != null) {
                    return Map.entry(_interface.getSimpleName(), functionalMethod);
                }
            }
        }  catch (SecurityException e) {
            logger.debug("Security exception when extracting functional interface info", e);
            // Fall through to next extraction method
        } catch (Exception e) {
            logger.debug("Exception when extracting functional interface info", e);
            // Fall through to next extraction method
        }
        return null;
    }

    private static String extractFromStackTrace() {
        try {
            var stackTrace = Thread.currentThread().getStackTrace();
            // Start at 3 to skip getStackTrace, extractFromStackTrace, and computeLoggingName
            for (int i = 3; i < Math.min(stackTrace.length, 10); i++) {
                var element   = stackTrace[i];
                var className = element.getClassName();

                // Skip system classes and this class
                if (!className.startsWith("java.") &&
                        !className.startsWith("javax.") &&
                        !className.startsWith("sun.") &&
                        !className.equals(FunctionalInterfaceLoggingNameResolver.class.getName())) {

                    var simpleClassName = className;
                    if (className.lastIndexOf('.') > 0) {
                        simpleClassName = className.substring(className.lastIndexOf('.') + 1);
                    }

                    return simpleClassName + "::" + element.getMethodName() + ":" + element.getLineNumber();
                }
            }
        } catch (SecurityException e) {
            logger.debug("Security exception when extracting stack trace info", e);
        } catch (Exception e) {
            logger.debug("Exception when extracting stack trace info", e);
        }
        return null;
    }
}