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

package dk.cloudcreate.essentials.shared.collections;

import dk.cloudcreate.essentials.shared.functional.tuple.*;

import java.util.*;
import java.util.stream.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * {@link List} utility methods
 */
public class Lists {
    /**
     * Convert the <code>list</code> to an 0-based Indexed Stream consisting of {@link Pair}'s, where each {@link Pair}
     * consists of the 0-based Index (i.e. the first index has value 0) and the corresponding List element at the given index: <code>Pair&lt;Index, List-element-at-the-given-index&gt;</code><br>
     * <br>
     * Example:<br>
     * List.of("A", "B", "C") will return a {@link Stream} of:
     * <ul>
     *     <li>Pair(0, "A")</li>
     *     <li>Pair(1, "B")</li>
     *     <li>Pair(2, "C")</li>
     * </ul>
     *
     * @param list the list that's converted to an indexed stream
     * @param <T>  the type of list element
     * @return the indexed list
     */
    public static <T> Stream<Pair<Integer, T>> toIndexedStream(List<T> list) {
        requireNonNull(list, "No list provided");
        if (list.isEmpty()) return Stream.empty();

        var listElementIterator = list.listIterator();
        return IntStream.range(0, list.size())
                        .mapToObj(index -> Tuple.of(index, listElementIterator.next()));

    }

    /**
     * Get the first element in a list
     *
     * @param list the non-null list
     * @param <T>  the type of elements in the list
     * @return the first element wrapped in an {@link Optional} or {@link Optional#empty()} if the list is empty
     */
    public static <T> Optional<T> first(List<T> list) {
        requireNonNull(list, "You must provide a non null list");
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(0));
    }

    /**
     * Get the last element in a list
     *
     * @param list the non-null list
     * @param <T>  the type of elements in the list
     * @return the last element wrapped in an {@link Optional} or {@link Optional#empty()} if the list is empty
     */
    public static <T> Optional<T> last(List<T> list) {
        requireNonNull(list, "You must provide a non null list");
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(list.size() - 1));
    }
}
