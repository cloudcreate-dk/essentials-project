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

import com.mongodb.*;
import org.jdbi.v3.core.ConnectionException;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ConnectException;
import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;

public class IOExceptionUtilTest {

    @Test
    void shouldIdentifyIOException_whenRootCauseIsIOException() {
        var ioException = new IOException("Root IO Exception");
        assertThat(IOExceptionUtil.isIOException(ioException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenRootCauseIsSQLTransientException() {
        var ioException = new SQLTransientException("Root IO Exception");
        assertThat(IOExceptionUtil.isIOException(ioException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMessageMatchesKnownErrors() {
        assertThat(IOExceptionUtil.isIOException(new Exception("An I/O error occurred while sending to the backend"))).isTrue();
        assertThat(IOExceptionUtil.isIOException(new Exception("Could not open JDBC Connection for transaction"))).isTrue();
        assertThat(IOExceptionUtil.isIOException(new Exception("Connection is closed"))).isTrue();
        assertThat(IOExceptionUtil.isIOException(new Exception("Unable to acquire JDBC Connection"))).isTrue();
        assertThat(IOExceptionUtil.isIOException(new Exception("Could not open JPA EntityManager for transaction"))).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenSQLExceptionWithConnectException() {
        var connectException = new ConnectException("Connection refused");
        var sqlException     = new SQLException("Unable to acquire JDBC Connection", connectException);
        assertThat(IOExceptionUtil.isIOException(sqlException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenSQLExceptionWithEOFException() {
        var eofException = new EOFException("Simulated EOF");
        var sqlException = new SQLException("Could not open JDBC Connection for transaction", eofException);
        assertThat(IOExceptionUtil.isIOException(sqlException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenJPAHasIssuesOpeningATransaction() {
        var rootCause       = new InterruptedException();
        var hikariException = new SQLException("Hikari - Interrupted during connection acquisition", rootCause);
        var transactionException = new org.springframework.transaction.CannotCreateTransactionException(
                "Could not open JPA EntityManager for transaction", hikariException);

        assertThat(IOExceptionUtil.isIOException(transactionException)).isTrue();
    }

    @Test
    void shouldNotIdentifyIOException_forUnrelatedExceptions() {
        var nullPointerException = new NullPointerException("This is not an IO exception");
        assertThat(IOExceptionUtil.isIOException(nullPointerException)).isFalse();

        var runtimeException = new RuntimeException("Generic runtime exception");
        assertThat(IOExceptionUtil.isIOException(runtimeException)).isFalse();
    }

    // ---------------------- MongoDB ----------------------

    @Test
    void shouldIdentifyIOException_whenActualMongoSocketException() {
        var mongoSocketException = new MongoSocketException("Simulated MongoSocketException", new ServerAddress());
        assertThat(IOExceptionUtil.isIOException(mongoSocketException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketReadException() {
        var mongoSocketReadException = new MongoSocketReadException("Simulated MongoSocketReadException", new ServerAddress());
        assertThat(IOExceptionUtil.isIOException(mongoSocketReadException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketClosedException() {
        var mongoSocketClosedException = new MongoSocketClosedException("Simulated MongoSocketClosedException", new ServerAddress());
        assertThat(IOExceptionUtil.isIOException(mongoSocketClosedException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketOpenException() {
        var mongoSocketOpenException = new MongoSocketOpenException("Simulated MongoSocketOpenException", new ServerAddress());
        assertThat(IOExceptionUtil.isIOException(mongoSocketOpenException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketWriteException() {
        var mongoSocketWriteException = new MongoSocketWriteException("Simulated MongoSocketWriteException", new ServerAddress(), new RuntimeException("Simulated RuntimeException"));
        assertThat(IOExceptionUtil.isIOException(mongoSocketWriteException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketReadTimeoutException() {
        var mongoSocketReadTimeoutException = new MongoSocketReadTimeoutException("Simulated MongoSocketReadTimeoutException", new ServerAddress(), new RuntimeException("Simulated RuntimeException"));
        assertThat(IOExceptionUtil.isIOException(mongoSocketReadTimeoutException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenActualMongoSocketExceptionWrapped() {
        var mongoSocketException = new MongoSocketException("Simulated MongoSocketException", new ServerAddress());
        var wrappedException     = new RuntimeException("Wrapper exception", mongoSocketException);
        assertThat(IOExceptionUtil.isIOException(wrappedException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketReadExceptionWrapped() {
        var mongoSocketReadException = new MongoSocketReadException("Simulated MongoSocketReadException", new ServerAddress());
        var wrappedException         = new RuntimeException("Wrapper exception", mongoSocketReadException);
        assertThat(IOExceptionUtil.isIOException(wrappedException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketClosedExceptionWrapped() {
        var mongoSocketClosedException = new MongoSocketClosedException("Simulated MongoSocketClosedException", new ServerAddress());
        var wrappedException           = new RuntimeException("Wrapper exception", mongoSocketClosedException);
        assertThat(IOExceptionUtil.isIOException(wrappedException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketOpenExceptionWrapped() {
        var mongoSocketOpenException = new MongoSocketOpenException("Simulated MongoSocketOpenException", new ServerAddress());
        var wrappedException         = new RuntimeException("Wrapper exception", mongoSocketOpenException);
        assertThat(IOExceptionUtil.isIOException(wrappedException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketWriteExceptionWrapped() {
        var mongoSocketWriteException = new MongoSocketWriteException(
                "Simulated MongoSocketWriteException",
                new ServerAddress(),
                new RuntimeException("Simulated RuntimeException"));
        var wrappedException = new RuntimeException("Wrapper exception", mongoSocketWriteException);
        assertThat(IOExceptionUtil.isIOException(wrappedException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenMongoSocketReadTimeoutExceptionWrapped() {
        var mongoSocketReadTimeoutException = new MongoSocketReadTimeoutException(
                "Simulated MongoSocketReadTimeoutException",
                new ServerAddress(),
                new RuntimeException("Simulated RuntimeException"));
        var wrappedException = new RuntimeException("Wrapper exception", mongoSocketReadTimeoutException);
        assertThat(IOExceptionUtil.isIOException(wrappedException)).isTrue();
    }

    // ---------------------- JDBI ----------------------
    @Test
    void shouldIdentifyIOException_whenJdbiConnectionException() {
        var jdbiException = new ConnectionException(new RuntimeException("Simulated RuntimeException"));
        assertThat(IOExceptionUtil.isIOException(jdbiException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenActualConnectionException() {
        var connectionException = new ConnectionException(new InterruptedException("Simulated exception"));
        assertThat(IOExceptionUtil.isIOException(connectionException)).isTrue();
    }

    @Test
    void shouldIdentifyIOException_whenConnectionExceptionAsRootCause() {
        // Test with ConnectionException as the root cause
        var connectionException = new ConnectionException(new RuntimeException("Simulated RuntimeException"));
        var wrappedException    = new RuntimeException("Wrapper exception", connectionException);
        assertThat(IOExceptionUtil.isIOException(wrappedException)).isTrue();
    }
}
