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
 * Provides security-related services for validating roles and user access based on a principal.
 * <p>
 * This interface defines methods used to validate whether an authenticated user has the required roles,
 * mapped from client roles to "Essentials" roles. The roles are essential for performing security checks
 * on API methods to ensure that users have the proper access rights before performing certain operations.
 * </p>
 * <p>
 * The role validation process is fundamental in methods such as {@code validateSchedulerReaderRoles(Object principal)},
 * where the principal (authenticated user) is checked for specific roles required to perform certain actions.
 * Roles are mapped to Essentials roles defined in the {@link EssentialsSecurityRoles} enumeration.
 * For example, the role {@code SCHEDULER_READER} is used in access control checks to ensure that a user has
 * the appropriate role to interact with the scheduling-related resources.
 * </p>
 * <p>
 * The {@link #isAllowed(Object, String)} method checks if the provided principal has the required Essentials role.
 * If the role is missing, access is denied, and a {@link SecurityException} is thrown, ensuring that unauthorized
 * access is prevented.
 * </p>
 * <p>
 * Additionally, the {@link #getPrincipalName(Object)} method allows extracting the user’s identification
 * from the principal, which is important for tracking and auditing user actions based on their roles.
 * </p>
 */
public interface EssentialsSecurityProvider {

    /**
     * Extracts roles/scopes from the principal to validate that the required Essentials role
     * from {@link EssentialsSecurityRoles} is present.
     * <p>
     * This method is used for verifying if an authenticated user (principal) has a specific role mapped to an
     * "Essentials" role, such as {@code SCHEDULER_READER} or other roles defined {@link EssentialsSecurityRoles}.
     * </p>
     *
     * @param principal The authenticated user principal.
     * @param requiredRole The role to be checked for the principal.
     * @return {@code true} if the principal has the required role, {@code false} otherwise.
     */
    boolean isAllowed(Object principal, String requiredRole);

    /**
     * Extracts the user identification from the principal.
     * <p>
     * This method is used to retrieve a unique identifier for the user associated with the principal.
     * It is essential for tracking user activities and enforcing access control based on the user's identity.
     * </p>
     *
     * @param principal The authenticated user principal.
     * @return The unique user identification as a string.
     */
    Optional<String> getPrincipalName(Object principal);

    /**
     * Default All access security provider
     */
    class AllAccessSecurityProvider implements EssentialsSecurityProvider {

        private static final Logger logger = LoggerFactory.getLogger(AllAccessSecurityProvider.class);

        public AllAccessSecurityProvider() {
            logger.info("### Initializing AllAccessSecurityProvider ###");
        }

        @Override
        public boolean isAllowed(Object principal, String requiredRole) {
            return true;
        }

        @Override
        public Optional<String> getPrincipalName(Object principal) {
            return Optional.of("AllAccessSecurityProvider");
        }
    }
}
