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

package dk.cloudcreate.essentials.components.foundation;

import com.mongodb.MongoSocketException;
import dk.cloudcreate.essentials.shared.Exceptions;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

import java.io.*;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

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
        var rootCause = Exceptions.getRootCause(e);
        var message   = e.getMessage() != null ? e.getMessage() : "";
        return (
                (message.contains("An I/O error occurred while sending to the backend") && rootCause instanceof EOFException) ||
                        (message.contains("Could not open JDBC Connection for transaction") && rootCause instanceof EOFException) ||
                        (message.contains("Could not open JDBC Connection for transaction") && rootCause instanceof ConnectException) ||
                        (message.contains("Could not open JDBC Connection for transaction") && rootCause instanceof SQLException && rootCause.getMessage().contains("has been closed")) ||
                        message.contains("Connection is closed") ||
                        message.contains("Unable to acquire JDBC Connection") ||
                        message.contains("Could not open JPA EntityManager for transaction") ||
                        rootCause.getClass().getSimpleName().equals("ConnectionException") ||
                        rootCause.getClass().getSimpleName().equals("MongoSocketReadException") ||
                        (isClassAvailable("com.mongodb.MongoSocketException") && rootCause instanceof MongoSocketException) ||
                        (isClassAvailable("org.jdbi.v3.core.statement.UnableToExecuteStatementException") && rootCause instanceof UnableToExecuteStatementException) ||
                        rootCause instanceof IOException ||
                        rootCause instanceof ConnectionException ||
                        message.contains("has been closed")
        );
    }

    private static boolean isClassAvailable(String className) {
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
