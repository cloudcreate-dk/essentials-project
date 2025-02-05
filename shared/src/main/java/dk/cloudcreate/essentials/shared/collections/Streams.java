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

package dk.cloudcreate.essentials.shared.collections;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Utility class for working with streams.
 */
public final class Streams {
    /**
     * Combines two streams into a single stream by applying a zip function to pairs of elements from the two streams.
     * The streams must have equal sizes. The resulting stream will have the same order as the input streams.
     *
     * <p>The zip operation combines elements of the two streams pairwise: the first element from {@code streamT} with the
     * first element from {@code streamU}, the second element with the second, and so on. The provided {@code zipFunction}
     * determines how the elements are combined into the resulting stream.
     *
     * <p>The resulting stream will automatically close both input streams when it is closed.
     *
     * <p>Example:
     * <pre>{@code
     * var tStream = Stream.of("A", "B", "C", "D", "E");
     * var uStream = Stream.of("1", "2", "3", "4", "5");
     *
     * List<String> result = Streams.zipOrderedAndEqualSizedStreams(tStream, uStream, (t, u) -> t + ":" + u)
     *                         .collect(Collectors.toList());
     *
     * assertThat(result).isEqualTo(List.of("A:1", "B:2", "C:3", "D:4", "E:5"));
     * }
     * </pre>
     *
     * @param <R>         The type of elements in the resulting stream.
     * @param <T>         The type of elements in the first input stream.
     * @param <U>         The type of elements in the second input stream.
     * @param streamT     the first input stream; must be non-null.
     * @param streamU     the second input stream; must be non-null.
     * @param zipFunction a function to combine elements from {@code streamT} and {@code streamU}; must be non-null.
     * @return a new stream consisting of elements resulting from applying the {@code zipFunction} to pairs of elements
     * from {@code streamT} and {@code streamU}.
     * @throws IllegalArgumentException if {@code streamT}, {@code streamU}, or {@code zipFunction} is null or
     *                                  if {@code streamT} or {@code streamU} do not have equal sizes.
     */
    public static <R, T, U> Stream<R> zipOrderedAndEqualSizedStreams(Stream<T> streamT, Stream<U> streamU, BiFunction<T, U, R> zipFunction) {
        requireNonNull(streamT, "No streamT provided");
        requireNonNull(streamU, "No streamU provided");
        requireNonNull(zipFunction, "No zipFunction provided");
        var streamTSpliterator = streamT.spliterator();
        if (!streamTSpliterator.hasCharacteristics(Spliterator.SIZED) || !streamTSpliterator.hasCharacteristics(Spliterator.ORDERED)) {
            throw new IllegalArgumentException("streamT isn't a sized and ordered stream");
        }
        var streamUSpliterator = streamU.spliterator();
        if (!streamUSpliterator.hasCharacteristics(Spliterator.SIZED) || !streamUSpliterator.hasCharacteristics(Spliterator.ORDERED)) {
            throw new IllegalArgumentException("streamU isn't a sized and ordered stream");
        }

        if (streamTSpliterator.estimateSize() != streamUSpliterator.estimateSize()) {
            throw new IllegalArgumentException(msg("Expected equal size. streamT has estimatedSize {} where as streamU has estimatedSize {}",
                                                   streamTSpliterator.estimateSize(),
                                                   streamUSpliterator.estimateSize()));
        }
        var streamTIterator = Spliterators.iterator(streamTSpliterator);
        var streamUIterator = Spliterators.iterator(streamUSpliterator);
        Stream<R> zipStream = StreamSupport.stream(
                new Spliterators.AbstractSpliterator<>(Math.min(streamTSpliterator.estimateSize(),
                                                                streamUSpliterator.estimateSize()),
                                                       Spliterator.SIZED | Spliterator.ORDERED) {
                    @Override
                    public boolean tryAdvance(Consumer<? super R> consumer) {
                        var canAdvance = streamTIterator.hasNext() && streamUIterator.hasNext();
                        if (canAdvance) {
                            consumer.accept(zipFunction.apply(streamTIterator.next(), streamUIterator.next()));
                        }
                        return canAdvance;
                    }
                }, streamT.isParallel() || streamU.isParallel());

        return zipStream.onClose(streamT::close)
                        .onClose(streamU::close);
    }
}
