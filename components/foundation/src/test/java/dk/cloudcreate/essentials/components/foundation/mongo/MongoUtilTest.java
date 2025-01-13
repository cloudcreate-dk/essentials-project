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

package dk.cloudcreate.essentials.components.foundation.mongo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class MongoUtilTest {

    @Test
    void whenCollectionNameIsNull_thenThrowsException() {
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName(null))
                .withMessageContaining("cannot be null or empty");
    }

    @Test
    void whenCollectionNameIsEmpty_thenThrowsException() {
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName(""))
                .withMessageContaining("cannot be null or empty");
    }

    @Test
    void whenCollectionNameOnlyContainsSpaces_thenThrowsException() {
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName("   "))
                .withMessageContaining("cannot be null or empty");
    }

    @Test
    void whenCollectionNameIsTooLong_thenThrowsException() {
        String longName = "a".repeat(MongoUtil.MAX_LENGTH + 1);
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName(longName))
                .withMessageContaining("exceeds max length");
    }

    @Test
    void whenCollectionNameStartsWithSystem_thenThrowsException() {
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName("system.collectionName"))
                .withMessageContaining("Starts with 'system.' which is not allowed");
    }

    @Test
    void whenCollectionNameContainsDollar_thenThrowsException() {
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName("name$withDollar"))
                .withMessageContaining("Contains $ or null characters");
    }

    @Test
    void whenCollectionNameContainsNullChar_thenThrowsException() {
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName("name\0WithNull"))
                .withMessageContaining("Contains $ or null characters");
    }

    @Test
    void whenCollectionNameIsInvalid_thenThrowsException() {
        assertThatExceptionOfType(InvalidCollectionNameException.class)
                .isThrownBy(() -> MongoUtil.checkIsValidCollectionName("Invalid Name With Spaces"))
                .withMessageContaining("Names only contain letters, underscore or digits");
    }

    @Test
    void whenCollectionNameIsValid_thenDoesNotThrowException() {
        assertThatCode(() -> MongoUtil.checkIsValidCollectionName("valid_collectionName123"))
                .doesNotThrowAnyException();
    }
}