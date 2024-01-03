/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.types;

/**
 * Base class for all {@link SingleValueType}'s that encapsulate a {@link Long}.<br>
 * Example of candidates for concrete {@link LongType} sub-types are Primary-Key identifiers, Aggregate Identifiers, Counters, Indexes, Percentage, etc.<br>
 * <br>
 * <b>Concrete</b> {@link LongType}'s are always subclasses for a <b>base</b> <code>CONCRETE_TYPE</code>, such as the OrderId:
 * <pre>{@code
 * public class OrderId extends LongType<OrderId> implements Identifier {
 *     private static Random RANDOM_ID_GENERATOR = new Random();
 *
 *     public OrderId(Long value) {
 *         super(value);
 *     }
 *
 *     public static OrderId of(long value) {
 *         return new OrderId(value);
 *     }
 *
 *     public static OrderId ofNullable(Long value) {
 *         return value != null ? new OrderId(value) : null;
 *     }
 *
 *     public static OrderId random() {
 *         return new OrderId(RANDOM_ID_GENERATOR.nextLong());
 *     }
 * }
 * }</pre>
 *
 * @param <CONCRETE_TYPE> The concrete {@link LongType} implementation
 */
public abstract class LongType<CONCRETE_TYPE extends LongType<CONCRETE_TYPE>> extends NumberType<Long, CONCRETE_TYPE> {

    public LongType(Long value) {
        super(value);
    }

    public CONCRETE_TYPE increment() {
        return  (CONCRETE_TYPE) SingleValueType.from(this.getValue()+1, this.getClass());
    }

    public CONCRETE_TYPE decrement() {
        return  (CONCRETE_TYPE) SingleValueType.from(this.getValue()-1, this.getClass());
    }


    @Override
    public int compareTo(CONCRETE_TYPE o) {
        return value.compareTo(o.longValue());
    }
}
