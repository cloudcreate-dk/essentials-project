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
 * Represents a set of predefined security roles used within the Essentials system.
 *
 * These roles are utilized to enforce access control and permissions in various parts
 * of the application by assigning specific roles to users and verifying their access
 * to protected resources. The roles defined in this enumeration help categorize and
 * manage different levels of access, enabling fine-grained control over system operations.
 *
 * Each role corresponds to a specific function or area of the system, such as
 * administration, lock management, queue operations, subscription handling, PostgreSQL
 * statistics, and scheduler management. These roles enable integration with role-based
 * access control mechanisms implemented in the application security layer.
 * </pre>
 */
public enum EssentialsSecurityRoles {

    // If only one role is needed for all essentials administration map to this
    ESSENTIALS_ADMIN("essentials_admin"),

    LOCK_READER("essentials_lock_reader"),
    LOCK_WRITER("essentials_lock_writer"),

    QUEUE_READER("essentials_queue_reader"),
    QUEUE_PAYLOAD_READER("essentials_queue_payload_reader"),
    QUEUE_WRITER("essentials_queue_writer"),

    SUBSCRIPTION_READER("essentials_subscription_reader"),
    SUBSCRIPTION_WRITER("essentials_subscription_writer"),

    POSTGRESQL_STATS_READER("essentials_postgresql_stats_reader"),

    SCHEDULER_READER("essentials_scheduler_reader");

    private final String roleName;

    EssentialsSecurityRoles(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleName() {
        return this.roleName;
    }
}
