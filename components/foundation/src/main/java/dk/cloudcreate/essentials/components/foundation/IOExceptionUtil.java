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

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Helper class to resolve IO, Connection, Transaction, Socket exception
 */
public final class IOExceptionUtil {

    /**
     * Is the exception provided an IO, Transaction, Connection or Socket style exception (across Mongo and Postgresql/JDBC/JPA) - i.e. a transient style of error
     * @param e the exception that occurred
     * @return true if the exception is determined to be an IO, Transaction, Connection or Socket style exception (across Mongo and Postgresql/JDBC/JPA) - i.e. a transient style of error
     */
    public static boolean isIOException(Throwable e) {
        requireNonNull(e, "No exception provided");
        var rootCause = Exceptions.getRootCause(e);
        return (
                (e.getMessage().contains("An I/O error occurred while sending to the backend") && rootCause instanceof EOFException) ||
                        (e.getMessage().contains("Could not open JDBC Connection for transaction") && rootCause instanceof EOFException) ||
                        (e.getMessage().contains("Could not open JDBC Connection for transaction") && rootCause instanceof ConnectException) ||
                        (e.getMessage().contains("Could not open JDBC Connection for transaction") && rootCause instanceof SQLException && rootCause.getMessage().contains("has been closed")) ||
                        e.getMessage().contains("has been closed") ||
                        e.getMessage().contains("Connection is closed") ||
                        e.getMessage().contains("Unable to acquire JDBC Connection") ||
                        e.getMessage().contains("Could not open JPA EntityManager for transaction") ||
                        rootCause.getClass().getSimpleName().equals("ConnectionException") ||
                        rootCause.getClass().getSimpleName().equals("MongoSocketReadException") ||
                        rootCause instanceof MongoSocketException ||
                        rootCause instanceof ConnectionException ||
                        rootCause instanceof UnableToExecuteStatementException ||
                        rootCause instanceof IOException
        );
    }
}
