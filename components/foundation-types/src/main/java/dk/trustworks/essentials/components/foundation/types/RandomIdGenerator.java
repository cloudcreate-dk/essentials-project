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

package dk.trustworks.essentials.components.foundation.types;

import com.fasterxml.uuid.Generators;
import dk.trustworks.essentials.shared.reflection.Classes;

import java.util.UUID;
import java.util.function.Supplier;

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;

/**
 * RandomId generator that either:
 * <ul>
 *     <li>Generates UUIDv1 time-based (i.e. sequential/monotonical) random id's
 *   if <code>com.fasterxml.uuid.Generators</code> is on the classpath</li>
 *   <li>Otherwise it generates UUIDv4 random id's
 *   using the default Java {@link UUID#randomUUID()} generator.</li>
 *   <li>The random-id generator provided to {@link #overrideRandomIdGenerator(Supplier, boolean)}</li>
 * </ul>
 * <p>
 * To add <code>com.fasterxml.uuid.Generators</code> you need to include the <code>com.fasterxml.uuid:java-uuid-generator</code> dependency in your project.<br>
 * Maven example:
 * <pre>{@code
 * <dependency>
 *    <groupId>com.fasterxml.uuid</groupId>
 *    <artifactId>java-uuid-generator</artifactId>
 *    <version>?.?.?</version>
 * </dependency>
 * }</pre>
 */
public final class RandomIdGenerator {
    private static Supplier<String> randomIdGenerator;
    private static boolean          isSequentialIdGenerator;

    static {
        if (Classes.doesClassExistOnClasspath("com.fasterxml.uuid.Generators")) {
            randomIdGenerator = () -> Generators.timeBasedGenerator().generate().toString();
            isSequentialIdGenerator = true;
        } else {
            randomIdGenerator = () -> UUID.randomUUID().toString();
            isSequentialIdGenerator = false;
        }
    }

    /**
     * Override the default configured random id generator.<br>
     * Example:
     * <pre>{@code
     * overrideRandomIdGenerator(() -> Generators.timeBasedEpochGenerator().generate().toString());
     * }</pre>
     *
     * @param randomIdGenerator       the new random id generator that generates a new random id every time it's called.<br>
     *                                The provided random id generator MUST be thread safe
     * @param isSequentialIdGenerator Is this a sequential id generator
     */
    public static void overrideRandomIdGenerator(Supplier<String> randomIdGenerator, boolean isSequentialIdGenerator) {
        RandomIdGenerator.randomIdGenerator = requireNonNull(randomIdGenerator, "No randomIdGenerator instance provided");
        RandomIdGenerator.isSequentialIdGenerator = isSequentialIdGenerator;
    }

    /**
     * Generates a new random id according to these rules:
     * <ul>
     *     <li>Generates UUIDv1 time-based (i.e. sequential/monotonical) random id's
     *   if <code>com.fasterxml.uuid.Generators</code> is on the classpath</li>
     *   <li>Otherwise it generates UUIDv4 random id's
     *   using the default Java {@link UUID#randomUUID()} generator.</li>
     *   <li>The random-id generator provided to {@link #overrideRandomIdGenerator(Supplier, boolean)}</li>
     * </ul>
     *
     * @return a new random id
     */
    public static String generate() {
        return randomIdGenerator.get();
    }

    public static boolean isOrderedIdGenerator() {
        return isSequentialIdGenerator;
    }
}
