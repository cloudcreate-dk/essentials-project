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

package dk.cloudcreate.essentials.types.jdbi;

import dk.cloudcreate.essentials.types.IntegerType;
import org.jdbi.v3.core.argument.*;
import org.jdbi.v3.core.config.ConfigRegistry;

import java.sql.Types;

/**
 * Base implementation for a {@link IntegerType}.<br>
 * Extend this class to support your concrete {@link IntegerType} sub-type:
 * <pre>{@code
 * public final class QuantityArgumentFactory extends IntegerTypeArgumentFactory<Quantity> {
 * }}</pre>
 *
 * @param <T> the concrete {@link IntegerType} subclass
 */
public abstract class IntegerTypeArgumentFactory<T extends IntegerType<T>> extends AbstractArgumentFactory<T> {
    public IntegerTypeArgumentFactory() {
        super(Types.INTEGER);
    }

    @Override
    protected Argument build(T value, ConfigRegistry config) {
        return (position, statement, ctx) -> statement.setInt(position, value.value());
    }
}
