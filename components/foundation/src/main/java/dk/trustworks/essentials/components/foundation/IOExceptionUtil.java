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

package dk.trustworks.essentials.components.foundation;

import com.mongodb.MongoSocketException;
import dk.trustworks.essentials.shared.Exceptions;
import org.jdbi.v3.core.ConnectionException;

import java.io.IOException;
import java.sql.SQLTransientException;
import java.util.concurrent.*;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Helper class to resolve IO, Connection, Transaction, Socket exception
 */
public final class IOExceptionUtil {
    private static final ConcurrentMap<String, Boolean> classCache = new ConcurrentHashMap<>();

    /**
     * Is the exception provided an IO, Transaction, Connection or Socket style exception (across Mongo and Postgresql/JDBC/JPA) - i.e. a transient style of error
     *
     * @param e the exception that occurred
     * @return true if the exception is determined to be an IO, Transaction, Connection or Socket style exception (across Mongo and Postgresql/JDBC/JPA) - i.e. a transient style of error
     */
    public static boolean isIOException(Throwable e) {
        requireNonNull(e, "No exception provided");
        var message = e.getMessage() != null ? e.getMessage() : "";

        var rootCause = Exceptions.getRootCause(e);
        return e instanceof IOException ||
                rootCause instanceof IOException ||
                e instanceof SQLTransientException ||
                rootCause instanceof SQLTransientException ||
                containsKnownErrorMessage(message) ||
                isJdbiRelatedException(e) ||
                isMongoRelatedException(e);
    }

    private static boolean containsKnownErrorMessage(String message) {
        return message.contains("An I/O error occurred while sending to the backend") ||
                message.contains("Could not open JDBC Connection for transaction") ||
                message.contains("Connection is closed") ||
                message.contains("Unable to acquire JDBC Connection") ||
                message.contains("Could not open JPA EntityManager for transaction");
    }

    private static boolean isMongoRelatedException(Throwable e) {
        return (isClassAvailable("com.mongodb.MongoSocketException") &&
                Exceptions.doesStackTraceContainExceptionOfType(e, MongoSocketException.class));
    }

    private static boolean isJdbiRelatedException(Throwable e) {
        return e.getClass().getName().equals("org.jdbi.v3.core.ConnectionException") ||
                (isClassAvailable("org.jdbi.v3.core.ConnectionException") && Exceptions.doesStackTraceContainExceptionOfType(e, ConnectionException.class));
    }


    static boolean isClassAvailable(String className) {
        return classCache.computeIfAbsent(className, key -> {
            try {
                Class.forName(key);
                return true;
            } catch (ClassNotFoundException ex) {
                return false;
            }
        });
    }
}
