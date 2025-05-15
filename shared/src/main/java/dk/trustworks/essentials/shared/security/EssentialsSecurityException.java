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

/**
 * <pre>
 * EssentialsSecurityException represents a custom unchecked exception used within
 * the Essentials application to indicate and handle security-related errors.
 *
 * This exception is typically thrown when there's a violation of security policies
 * or when access to restricted resources is denied due to insufficient permissions.
 * It serves as a useful way to manage and propagate security-specific errors across the
 * application, ensuring they can be handled appropriately.
 *
 * Usage of this exception aligns with the security context established by Essentials,
 * such as roles defined in EssentialsSecurityRoles or access control mechanisms
 * provided by EssentialsSecurityProvider. It encapsulates an error message
 * describing the specific security violation.
 * </pre>
 */
public class EssentialsSecurityException extends RuntimeException {
    public EssentialsSecurityException(String message) {
        super(message);
    }
}
