/*
 * Copyright 2021-2022 the original author or authors.
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

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.*;

import java.sql.SQLException;
import java.time.Duration;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class EventStoreSqlLogger implements org.jdbi.v3.core.statement.SqlLogger {
    private final Logger log;

    public EventStoreSqlLogger() {
        log = LoggerFactory.getLogger("EventStore.Sql");
    }

    @Override
    public void logBeforeExecution(StatementContext context) {

    }

    @Override
    public void logAfterExecution(StatementContext context) {
        if (log.isTraceEnabled()) {
            log.trace("Execution time: {} ms - {}", Duration.between(context.getExecutionMoment(), context.getCompletionMoment()).toMillis(), context.getRenderedSql());
        }
    }

    @Override
    public void logException(StatementContext context, SQLException ex) {
        log.error(msg("Failed Execution time: {} ms - {}", Duration.between(context.getExecutionMoment(), context.getExceptionMoment()).toMillis(), context.getRenderedSql()), ex);
    }
}
