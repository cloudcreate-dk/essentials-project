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

package dk.trustworks.essentials.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Interface representing an authenticated user with essential role-based access methods.
 * This interface defines the essential operations that an authenticated user can perform,
 * including checking their authentication status, roles, principal information, and logging out.
 * Used in essentials admin ui.
 */
public interface EssentialsAuthenticatedUser {

    /**
     * Gets the principal associated with the authenticated user.
     * The principal typically represents the unique identity of the user.
     *
     * @return The principal object associated with the user.
     */
    Object getPrincipal();

    /**
     * Checks if the user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    default boolean isAuthenticated() {
        return false;
    }

    /**
     * Checks if the user has the specified role.
     *
     * @param role The role to check.
     * @return true if the user has the specified role, false otherwise.
     */
    default boolean hasRole(String role) {
        return false;
    }

    /**
     * Gets the name of the principal associated with the authenticated user.
     * This is typically the username or another identifier of the user.
     *
     * @return An Optional containing the principal name if present, empty otherwise.
     */
    default Optional<String> getPrincipalName() {
        return Optional.empty();
    }

    /**
     * Checks if the authenticated user has the {@link EssentialsSecurityRoles#ESSENTIALS_ADMIN} role.
     *
     * @return true if the user has the {@link EssentialsSecurityRoles#ESSENTIALS_ADMIN} role, false otherwise.
     */
    default boolean hasAdminRole() { return false; }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#LOCK_READER} role.
     *
     * @return true if the user has the lock reader role, false otherwise.
     */
    default boolean hasLockReaderRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#LOCK_WRITER} role.
     *
     * @return true if the user has the lock writer role, false otherwise.
     */
    default boolean hasLockWriterRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#QUEUE_READER} role.
     *
     * @return true if the user has the queue reader role, false otherwise.
     */
    default boolean hasQueueReaderRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#QUEUE_PAYLOAD_READER} role.
     *
     * @return true if the user has the queue payload reader role, false otherwise.
     */
    default boolean hasQueuePayloadReaderRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#QUEUE_WRITER} role.
     *
     * @return true if the user has the queue writer role, false otherwise.
     */
    default boolean hasQueueWriterRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#SUBSCRIPTION_READER} role.
     *
     * @return true if the user has the subscription reader role, false otherwise.
     */
    default boolean hasSubscriptionReaderRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#SUBSCRIPTION_WRITER} role.
     *
     * @return true if the user has the subscription writer role, false otherwise.
     */
    default boolean hasSubscriptionWriterRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#SCHEDULER_READER} role.
     *
     * @return true if the user has the scheduled reader role, false otherwise.
     */
    default boolean hasScheduledReaderRole() {
        return false;
    }

    /**
     * Checks if the user has the {@link EssentialsSecurityRoles#POSTGRESQL_STATS_READER} role.
     *
     * @return true if the user has the scheduled reader role, false otherwise.
     */
    default boolean hasPostgresqlStatsReaderRole() {
        return false;
    }

    /**
     * Logs the user out of the system, clearing their authentication context.
     */
    void logout();

    class AllAccessAuthenticatedUser implements EssentialsAuthenticatedUser {

        private static final Logger logger = LoggerFactory.getLogger(AllAccessAuthenticatedUser.class);

        public AllAccessAuthenticatedUser() {
            logger.info("### Initializing AllAccessAuthenticatedUser ###");
        }

        @Override
        public Object getPrincipal() {
            return "AllAccessAuthenticatedUser";
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public boolean hasRole(String role) {
            return true;
        }

        @Override
        public boolean hasAdminRole() { return true; }

        @Override
        public Optional<String> getPrincipalName() {
            return Optional.of("AllAccessAuthenticatedUser");
        }

        @Override
        public boolean hasLockReaderRole() {
            return true;
        }

        @Override
        public boolean hasLockWriterRole() {
            return true;
        }

        @Override
        public boolean hasQueueReaderRole() {
            return true;
        }

        @Override
        public boolean hasQueuePayloadReaderRole() {
            return true;
        }

        @Override
        public boolean hasQueueWriterRole() {
            return true;
        }

        @Override
        public boolean hasSubscriptionReaderRole() {
            return true;
        }

        @Override
        public boolean hasSubscriptionWriterRole() {
            return true;
        }

        @Override
        public boolean hasScheduledReaderRole() {
            return true;
        }

        @Override
        public boolean hasPostgresqlStatsReaderRole() { return true; }

        @Override
        public void logout() {
        }
    }
}
