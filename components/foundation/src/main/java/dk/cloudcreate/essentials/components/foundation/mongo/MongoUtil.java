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

import org.springframework.data.mongodb.UncategorizedMongoDbException;

import java.util.Locale;
import java.util.regex.Pattern;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public final class MongoUtil {
    public static final  int     MAX_LENGTH         = 64;
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    /**
     * Validates if the provided collection name is valid and safe to use.<br>
     * The method provided is designed as an initial layer of defense against users providing unsafe collection names, by applying naming conventions intended to reduce the risk of malicious input.<br>
     * However, Essentials components as well as {@link #checkIsValidCollectionName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting MongoDB configuration and associated Queries/Updates/etc.<br>
     * <b>The responsibility for implementing protective measures against malicious input lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     * Users must ensure thorough sanitization and validation of API input parameters,  collection names.<br>
     * Insufficient attention to these practices may leave the application vulnerable to attacks, potentially endangering the security and integrity of the database.<br>
     * <p>
     * The method checks if the {@code collectionName}:
     * <ul>
     *     <li>Is not null, empty, and does not consist solely of whitespace.</li>
     *     <li>Does not start with "system." (case-insensitive check).</li>
     *     <li>Does not start with $, or contains the null character.</li>
     *     <li>Contains only characters valid for Mongo collection names: letters, digits, and underscores</li>
     * </ul>
     * <p>
     *
     * @param collectionName The collection name to validate.
     * @throws InvalidCollectionNameException in case the collectionName violates the rules
     */
    public static void checkIsValidCollectionName(String collectionName) {
        if (collectionName == null || collectionName.trim().isEmpty()) {
            throw new InvalidCollectionNameException("Collection name cannot be null or empty.");
        }

        if (collectionName.length() > MAX_LENGTH) {
            throw new InvalidCollectionNameException(msg("Collection name: '{}' exceeds max length of {}", collectionName, MAX_LENGTH));
        }

        // MongoDB does not allow collections with empty name, or names with $, or containing the null character.
        if (collectionName.contains("$") || collectionName.contains("\0")) {
            throw new InvalidCollectionNameException(msg("Invalid Collection name: '{}'. Contains $ or null characters", collectionName));
        }

        // MongoDB specific restrictions
        // Avoid system. prefix, which is reserved for system collections.
        if (collectionName.toLowerCase(Locale.ROOT).startsWith("system.")) {
            throw new InvalidCollectionNameException(msg("Invalid Collection name: '{}'. Starts with 'system.' which is not allowed", collectionName));
        }

        // Check against the pattern to ensure the name contains only allowed characters.
        if (!VALID_NAME_PATTERN.matcher(collectionName).matches()) {
            throw new InvalidCollectionNameException(msg("Invalid Collection name: '{}'. Names only contain letters, underscore or digits", collectionName));
        }
    }

    /**
     * Check if the exception is a Mongo write conflict
     * @param e the exception to check
     * @return true if the exception is a write conflict, otherwise false
     */
    public static boolean isWriteConflict(Throwable e) {
        requireNonNull(e, "No exception provided");
        return e instanceof UncategorizedMongoDbException && e.getMessage() != null && (e.getMessage().contains("WriteConflict") || e.getMessage().contains("Write Conflict"));
    }
}
