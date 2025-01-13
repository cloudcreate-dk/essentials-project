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

package dk.cloudcreate.essentials.components.foundation.postgresql;

import com.fasterxml.jackson.annotation.JsonProperty;
import dk.cloudcreate.essentials.components.foundation.postgresql.ListenNotify.SqlOperation;

import java.util.Objects;

/**
 * Represents a base notification, which is a change to a given table caused by a specific {@link SqlOperation}
 */
public abstract class TableChangeNotification {
    @JsonProperty(ListenNotify.TABLE_NAME)
    private String       tableName;
    @JsonProperty(ListenNotify.SQL_OPERATION)
    private SqlOperation operation;

    protected TableChangeNotification() {
    }

    protected TableChangeNotification(String tableName, SqlOperation operation) {
        this.tableName = tableName;
        this.operation = operation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableChangeNotification)) return false;
        TableChangeNotification that = (TableChangeNotification) o;
        return tableName.equals(that.tableName) && operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, operation);
    }

    public String getTableName() {
        return tableName;
    }

    public SqlOperation getOperation() {
        return operation;
    }
}
