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

package dk.trustworks.essentials.shared.functional;

import java.io.Serializable;
import java.util.function.Function;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * Represents a function that accepts four arguments and produces a result.<br>
 * This is a specialization of a function interface.
 *
 * @param <T1> the first function argument type
 * @param <T2> the second function argument type
 * @param <T3> the third function argument type
 * @param <T4> the fourth function argument type
 * @param <R>  the type of the result of the function
 */
@FunctionalInterface
public interface QuadFunction<T1, T2, T3, T4, R> extends Serializable {
    /**
     * Applies this function to the given arguments.
     *
     * @param t1 the first function argument
     * @param t2 the second function argument
     * @param t3 the third function argument
     * @param t4 the fourth function argument
     * @return the function result
     */
    R apply(T1 t1, T2 t2, T3 t3, T4 t4);

    /**
     * Returns a composed function that first applies this function to its input, and then applies the after function to the result.
     * If evaluation of either function throws an exception, it is relayed to the caller of the composed function.
     *
     * @param after the function to apply after this function is applied
     * @param <V>   return type of the <code>after</code> function
     * @return a function composed of this and after
     */
    default <V> QuadFunction<T1, T2, T3, T4, V> andThen(Function<? super R, ? extends V> after) {
        requireNonNull(after, "You must supply an after function");
        return (t1, t2, t3, t4) -> after.apply(apply(t1, t2, t3, t4));
    }
}
