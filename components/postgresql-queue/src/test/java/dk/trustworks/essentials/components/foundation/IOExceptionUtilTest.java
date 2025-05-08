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

import org.jdbi.v3.core.ConnectionException;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ConnectException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Related to IOExceptionUtilTest in Foundation, with the goal of verifying
 * that any lack of MongoDB dependencies will not result in {@link NoClassDefFoundError}'s
 */
public class IOExceptionUtilTest {

    @Test
    void shouldIdentifyIOException_whenRootCauseIsIOException() {
        var ioException = new IOException("Root IO Exception");
        assertThat(IOExceptionUtil.isIOException(ioException)).isTrue();
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
    void shouldNotIdentifyIOException_forUnrelatedExceptions() {
        var nullPointerException = new NullPointerException("This is not an IO exception");
        assertThat(IOExceptionUtil.isIOException(nullPointerException)).isFalse();

        var runtimeException = new RuntimeException("Generic runtime exception");
        assertThat(IOExceptionUtil.isIOException(runtimeException)).isFalse();
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
