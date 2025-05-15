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

import static dk.trustworks.essentials.shared.FailFast.requireNonNull;
import static dk.trustworks.essentials.shared.FailFast.requireTrue;

/**
 * <pre>
 * Utility class providing static helper methods related to security validation in the Essentials system.
 * This class cannot be instantiated and is designed for role-based security validations.
 *
 * The primary purpose of this class is to validate that a given user (principal) has the necessary
 * security role before allowing access to certain application functionalities. The class works in
 * conjunction with the {@link EssentialsSecurityProvider} and {@link EssentialsSecurityRoles} to
 * enforce role-based access control.
 *
 * Methods in this class are intended to perform checks based on the role mappings and security policies
 * defined for the application. These checks ensure reliable enforcement of access restrictions to
 * sensitive or privileged resources.
 * </pre>
 */
public final class EssentialsSecurityValidator {

    private EssentialsSecurityValidator() {}

    /**
     * Validates whether the specified principal has the required Essentials security role.
     * If the principal does not have the necessary role, an {@link EssentialsSecurityException} is thrown.
     *
     * @param securityProvider The security provider responsible for checking the roles of the principal.
     * @param principal The authenticated user whose roles need to be validated. Must not be null.
     * @param role The required role that the principal must possess to gain access.
     * @throws IllegalArgumentException If any of the parameters are null.
     * @throws EssentialsSecurityException If the principal does not have the required role.
     */
    public static void validateHasEssentialsSecurityRole(EssentialsSecurityProvider securityProvider, Object principal, EssentialsSecurityRoles role) {
        requireNonNull(securityProvider, "The securityProvider cannot be null");
        requireNonNull(principal, "The principal cannot be null");
        requireNonNull(role, "The role cannot be null");
        if (!securityProvider.isAllowed(principal, role.getRoleName())) {
            throw new EssentialsSecurityException("Unauthorized access required role is missing");
        }
    }

    /**
     * Validates whether the specified principal has at least one of the given Essentials security roles.
     * Throws an {@link EssentialsSecurityException} if none of the specified roles are assigned to the principal.
     *
     * @param securityProvider The security provider responsible for checking the roles of the principal. Must not be null.
     * @param principal The authenticated user whose roles need to be validated. Must not be null.
     * @param roles The list of security roles to validate against the principal. Must not be null and must contain at least one role.
     * @throws IllegalArgumentException If any of the parameters are null or if no roles are provided.
     * @throws EssentialsSecurityException If the principal does not have any of the specified roles.
     */
    public static void validateHasAnyEssentialsSecurityRoles(EssentialsSecurityProvider securityProvider, Object principal, EssentialsSecurityRoles ... roles) {
        requireNonNull(securityProvider, "The securityProvider cannot be null");
        requireNonNull(principal, "The principal cannot be null");
        requireNonNull(roles, "The role cannot be null");
        requireTrue(roles.length > 0, "At least one role must be specified");
        for (EssentialsSecurityRoles role : roles) {
            if (securityProvider.isAllowed(principal, role.getRoleName())) {
                return;
            }
        }
        throw new EssentialsSecurityException("Unauthorized access required role is missing");
    }

    /**
     * Validates whether the specified principal has all of the required Essentials security roles.
     * If the principal does not possess one or more of the specified roles, an {@link EssentialsSecurityException} is thrown.
     *
     * @param securityProvider The security provider responsible for verifying the roles of the principal. Must not be null.
     * @param principal The authenticated user whose roles need to be validated. Must not be null.
     * @param roles The list of required security roles that the principal must possess. Must not be null and must contain at least one role.
     * @throws IllegalArgumentException If any of the parameters are null or no roles are provided.
     * @throws EssentialsSecurityException If the principal does not possess one or more of the specified roles.
     */
    public static void validateHasEssentialsSecurityRoles(EssentialsSecurityProvider securityProvider, Object principal, EssentialsSecurityRoles ... roles) {
        requireNonNull(securityProvider, "The securityProvider cannot be null");
        requireNonNull(principal, "The principal cannot be null");
        requireNonNull(roles, "The role cannot be null");
        requireTrue(roles.length > 0, "At least one role must be specified");
        for (EssentialsSecurityRoles role : roles) {
            if (!securityProvider.isAllowed(principal, role.getRoleName())) {
                throw new EssentialsSecurityException("Unauthorized access required role is missing");
            }
        }
    }

    /**
     * <pre>
     * Checks if the given principal has the specified Essentials security role.
     *
     * This method uses the provided {@link EssentialsSecurityProvider} to validate
     * whether the principal possesses the required role by checking the role's name.
     *
     * @param securityProvider The security provider used to verify the roles of the principal. Must not be null.
     * @param principal The authenticated user whose roles need to be checked. Must not be null.
     * @param role The required security role to verify against the principal. Must not be null.
     * @return {@code true} if the principal has the specified role, {@code false} otherwise.
     * @throws IllegalArgumentException If any of the parameters are null.
     * </pre>
     */
    public static boolean hasEssentialsSecurityRole(EssentialsSecurityProvider securityProvider, Object principal, EssentialsSecurityRoles role) {
        requireNonNull(securityProvider, "The securityProvider cannot be null");
        requireNonNull(principal, "The principal cannot be null");
        requireNonNull(role, "The role cannot be null");
        return securityProvider.isAllowed(principal, role.getRoleName());
    }

    /**
     * Determines if the specified principal has at least one of the given Essentials security roles.
     * Uses the provided {@code EssentialsSecurityProvider} to check the roles associated with the principal.
     *
     * @param securityProvider The security provider used to validate the roles of the principal. Must not be null.
     * @param principal The authenticated user whose roles need to be checked. Must not be null.
     * @param roles The list of security roles to verify against the principal. Must not be null and must contain at least one role.
     * @return {@code true} if the principal has at least one of the specified roles, {@code false} otherwise.
     * @throws IllegalArgumentException If any of the parameters are null or no roles are provided.
     */
    public static boolean hasAnyEssentialsSecurityRoles(EssentialsSecurityProvider securityProvider, Object principal, EssentialsSecurityRoles ... roles) {
        requireNonNull(securityProvider, "The securityProvider cannot be null");
        requireNonNull(principal, "The principal cannot be null");
        requireNonNull(roles, "The role cannot be null");
        requireTrue(roles.length > 0, "At least one role must be specified");
        for (EssentialsSecurityRoles role : roles) {
            if (securityProvider.isAllowed(principal, role.getRoleName())) {
                return true;
            }
        }
        return false;
    }
}
