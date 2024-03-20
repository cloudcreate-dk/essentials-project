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

package dk.cloudcreate.essentials.components.foundation.postgresql;

import org.jdbi.v3.core.Handle;

import java.util.Set;
import java.util.regex.Pattern;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public final class PostgresqlUtil {
    /**
     * Read the major Postgresql server version
     *
     * @param handle the jdbi handle that will be used for querying
     * @return the major version (12, 13, 14, 15, etc.)
     */
    public static int getServiceMajorVersion(Handle handle) {
        requireNonNull(handle, "No handle provided");
        // version() returns something similar to "PostgreSQL 13.4 on x86_64..."
        return handle.createQuery("SELECT substring(version() from 'PostgreSQL ([0-9]+)')")
                     .mapTo(Integer.class)
                     .first();
    }

    private static final Pattern VALID_SQL_TABLE_AND_COLUMN_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * This list incorporates a broad range of reserved keywords, including those specific to PostgreSQL as well as standard SQL keywords.
     * Developers should use this list cautiously and always cross-reference against the current version of PostgreSQL they are working with,
     * as database systems frequently update their list of reserved keywords.<br>
     * <br>
     * The primary goal of this list is to avoid naming conflicts and ensure compatibility with SQL syntax, in an attempt to reduce errors
     * and potential SQL injection vulnerabilities.
     */
    public static final Set<String> RESERVED_KEYWORDS = Set.of(
            "ABORT", "ABS", "ABSOLUTE", "ACCESS", "ACTION", "ADA", "ADD", "ADMIN",
            "AFTER", "AGGREGATE", "ALSO", "ALTER", "ALWAYS", "ANALYSE", "ANALYZE",
            "AND", "ANY", "ARRAY", "AS", "ASC", "ASENSITIVE", "ASSERTION", "ASSIGNMENT",
            "ASYMMETRIC", "AT", "ATOMIC", "ATTRIBUTE", "ATTRIBUTES", "AUTHORIZATION",
            "AVG", "BACKWARD", "BEFORE", "BEGIN", "BERNOULLI", "BETWEEN", "BIGINT",
            "BINARY", "BIT", "BITVAR", "BIT_LENGTH", "BLOB", "BOOLEAN", "BOTH",
            "BREADTH", "BY", "C", "CACHE", "CALL", "CALLED", "CARDINALITY", "CASCADE",
            "CASCADED", "CASE", "CAST", "CATALOG", "CATALOG_NAME", "CEIL", "CEILING",
            "CHAIN", "CHAR", "CHARACTER", "CHARACTERISTICS", "CHARACTERS", "CHARACTER_LENGTH",
            "CHARACTER_SET_CATALOG", "CHARACTER_SET_NAME", "CHARACTER_SET_SCHEMA",
            "CHAR_LENGTH", "CHECK", "CHECKED", "CHECKPOINT", "CLASS", "CLASS_ORIGIN",
            "CLOB", "CLOSE", "CLUSTER", "COALESCE", "COBOL", "COLLATE", "COLLATION",
            "COLLATION_CATALOG", "COLLATION_NAME", "COLLATION_SCHEMA", "COLLECT",
            "COLUMN", "COLUMN_NAME", "COMMAND_FUNCTION", "COMMAND_FUNCTION_CODE",
            "COMMENT", "COMMIT", "COMMITTED", "COMPLETION", "CONDITION", "CONDITION_NUMBER",
            "CONNECT", "CONNECTION", "CONNECTION_NAME", "CONSTRAINT", "CONSTRAINTS",
            "CONSTRAINT_CATALOG", "CONSTRAINT_NAME", "CONSTRAINT_SCHEMA", "CONSTRUCTOR",
            "CONTAINS", "CONTINUE", "CONVERSION", "CONVERT", "COPY", "CORR", "CORRESPONDING",
            "COUNT", "COVAR_POP", "COVAR_SAMP", "CREATE", "CROSS", "CSV", "CUBE", "CUME_DIST",
            "CURRENT", "CURRENT_DATE", "CURRENT_DEFAULT_TRANSFORM_GROUP", "CURRENT_PATH",
            "CURRENT_ROLE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_TRANSFORM_GROUP_FOR_TYPE",
            "CURRENT_USER", "CURSOR", "CURSOR_NAME", "CYCLE", "DATA", "DATABASE", "DATE",
            "DATETIME_INTERVAL_CODE", "DATETIME_INTERVAL_PRECISION", "DAY", "DEALLOCATE",
            "DEC", "DECIMAL", "DECLARE", "DEFAULT", "DEFAULTS", "DEFERRABLE", "DEFERRED",
            "DEFINED", "DEFINER", "DEGREE", "DELETE", "DELIMITER", "DELIMITERS", "DENSE_RANK",
            "DEPTH", "DEREF", "DERIVED", "DESC", "DESCRIBE", "DESCRIPTOR", "DETERMINISTIC",
            "DIAGNOSTICS", "DICTIONARY", "DISABLE", "DISCONNECT", "DISPATCH", "DISTINCT",
            "DLNEWCOPY", "DLPREVIOUSCOPY", "DLURLCOMPLETE", "DLURLCOMPLETEONLY", "DLURLCOMPLETEWRITE",
            "DLURLPATH", "DLURLPATHONLY", "DLURLPATHWRITE", "DLURLSCHEME", "DLURLSERVER",
            "DLVALUE", "DO", "DOCUMENT", "DOMAIN", "DOUBLE", "DROP", "DYNAMIC", "DYNAMIC_FUNCTION",
            "DYNAMIC_FUNCTION_CODE", "EACH", "ELEMENT", "ELSE", "ENABLE", "ENCODING",
            "ENCRYPTED", "END", "ENUM", "EQUALS", "ESCAPE", "EVENT", "EXCEPT", "EXCEPTION",
            "EXCLUDE", "EXCLUDING", "EXCLUSIVE", "EXEC", "EXECUTE", "EXISTING", "EXISTS",
            "EXP", "EXPLAIN", "EXTENSION", "EXTERNAL", "EXTRACT", "FALSE", "FETCH", "FILTER",
            "FINAL", "FIRST", "FLOAT", "FLOOR", "FOLLOWING", "FOR", "FORCE", "FOREIGN",
            "FORTRAN", "FORWARD", "FOUND", "FREE", "FREEZE", "FROM", "FULL", "FUNCTION",
            "FUNCTIONS", "FUSION", "G", "GENERAL", "GENERATED", "GET", "GLOBAL", "GO",
            "GOTO", "GRANT", "GRANTED", "GREATEST", "GROUP", "GROUPING", "HANDLER", "HAVING",
            "HEADER", "HEX", "HIERARCHY", "HOLD", "HOUR", "IDENTITY", "IF", "IGNORE",
            "ILIKE", "IMMEDIATE", "IMMEDIATELY", "IMMUTABLE", "IMPLEMENTATION", "IMPLICIT",
            "IN", "INCLUDING", "INCREMENT", "INDEX", "INDICATOR", "INHERIT", "INHERITS",
            "INITIALLY", "INLINE", "INNER", "INOUT", "INPUT", "INSENSITIVE", "INSERT",
            "INSTANCE", "INSTANTIABLE", "INSTEAD", "INT", "INTEGER", "INTEGRITY", "INTERSECT",
            "INTERSECTION", "INTERVAL", "INTO", "INVOKER", "IS", "ISNULL", "ISOLATION",
            "JOIN", "K", "KEY", "KEY_MEMBER", "KEY_TYPE", "LABEL", "LAG", "LANGUAGE",
            "LARGE", "LAST", "LATERAL", "LEAD", "LEADING", "LEAKPROOF", "LEAST", "LEFT",
            "LENGTH", "LEVEL", "LIKE", "LIMIT", "LISTEN", "LN", "LOAD", "LOCAL", "LOCALTIME",
            "LOCALTIMESTAMP", "LOCATION", "LOCK", "LOCKED", "LOGGED", "LOWER", "M", "MAP",
            "MAPPING", "MATCH", "MATCHED", "MATERIALIZED", "MAX", "MAXVALUE", "MEASURE",
            "MEMBER", "MERGE", "MESSAGE_LENGTH", "MESSAGE_OCTET_LENGTH", "MESSAGE_TEXT",
            "METHOD", "MIN", "MINUTE", "MINVALUE", "MOD", "MODE", "MODIFIES", "MODULE",
            "MONTH", "MORE", "MOVE", "MULTISET", "MUMPS", "NAME", "NAMES", "NAMESPACE",
            "NATIONAL", "NATURAL", "NCHAR", "NCLOB", "NESTING", "NEW", "NEXT", "NFC",
            "NFD", "NFKC", "NFKD", "NIL", "NO", "NONE", "NORMALIZE", "NORMALIZED", "NOT",
            "NOTHING", "NOTIFY", "NOTNULL", "NOWAIT", "NTH_VALUE", "NTILE", "NULL", "NULLABLE",
            "NULLIF", "NULLS", "NUMBER", "NUMERIC", "OBJECT", "OCCURRENCES_REGEX", "OCTETS",
            "OCTET_LENGTH", "OF", "OFF", "OFFSET", "OIDS", "OLD", "ON", "ONLY", "OPEN",
            "OPERATOR", "OPTION", "OPTIONS", "OR", "ORDER", "ORDERING", "ORDINALITY",
            "OTHERS", "OUT", "OUTER", "OUTPUT", "OVER", "OVERLAPS", "OVERLAY", "OVERRIDING",
            "OWNED", "OWNER", "P", "PAD", "PARAMETER", "PARAMETER_MODE", "PARAMETER_NAME",
            "PARAMETER_ORDINAL_POSITION", "PARAMETER_SPECIFIC_CATALOG", "PARAMETER_SPECIFIC_NAME",
            "PARAMETER_SPECIFIC_SCHEMA", "PARTIAL", "PARTITION", "PASCAL", "PASSING",
            "PASSWORD", "PATH", "PERCENT", "PERCENTILE_CONT", "PERCENTILE_DISC",
            "PERCENT_RANK", "PERIOD", "PERMISSION", "PLACING", "PLANS", "PLI",
            "POSITION", "POSITION_REGEX", "POSTFIX", "POWER", "PRECEDING", "PRECISION",
            "PREPARE", "PREPARED", "PRESERVE", "PRIMARY", "PRIOR", "PRIVILEGES",
            "PROCEDURAL", "PROCEDURE", "PROGRAM", "QUOTE", "RANGE", "RANK", "READ",
            "READS", "REAL", "REASSIGN", "RECHECK", "RECURSIVE", "REF", "REFERENCES",
            "REFERENCING", "REFRESH", "REGR_AVGX", "REGR_AVGY", "REGR_COUNT",
            "REGR_INTERCEPT", "REGR_R2", "REGR_SLOPE", "REGR_SXX", "REGR_SXY",
            "REGR_SYY", "REINDEX", "RELATIVE", "RELEASE", "RENAME", "REPEATABLE",
            "REPLACE", "REPLICA", "REQUIRING", "RESET", "RESPECT", "RESTART",
            "RESTORE", "RESTRICT", "RESULT", "RETURN", "RETURNED_CARDINALITY",
            "RETURNED_LENGTH", "RETURNED_OCTET_LENGTH", "RETURNED_SQLSTATE",
            "RETURNING", "RETURNS", "REVOKE", "RIGHT", "ROLE", "ROLLBACK", "ROLLUP",
            "ROUTINE", "ROUTINES", "ROUTINE_CATALOG", "ROUTINE_NAME", "ROUTINE_SCHEMA",
            "ROW", "ROWS", "ROW_COUNT", "ROW_NUMBER", "RULE", "SAVEPOINT", "SCALE",
            "SCHEMA", "SCHEMA_NAME", "SCOPE", "SCOPE_CATALOG", "SCOPE_NAME",
            "SCOPE_SCHEMA", "SCROLL", "SEARCH", "SECOND", "SECTION", "SECURITY",
            "SELECT", "SELECTIVE", "SELF", "SENSITIVE", "SEQUENCE", "SEQUENCES",
            "SERIALIZABLE", "SERVER", "SERVER_NAME", "SESSION", "SESSION_USER",
            "SET", "SETOF", "SETS", "SHARE", "SHOW", "SIMILAR", "SIMPLE", "SIZE",
            "SKIP", "SMALLINT", "SNAPSHOT", "SOME", "SOURCE", "SPACE", "SPECIFIC",
            "SPECIFICTYPE", "SPECIFIC_NAME", "SQL", "SQLCODE", "SQLERROR", "SQLEXCEPTION",
            "SQLSTATE", "SQLWARNING", "SQRT", "STABLE", "STANDALONE", "START",
            "STATE", "STATEMENT", "STATIC", "STATISTICS", "STDDEV_POP", "STDDEV_SAMP",
            "STDIN", "STDOUT", "STORAGE", "STRICT", "STRIP", "STRUCTURE", "STYLE",
            "SUBCLASS_ORIGIN", "SUBLIST", "SUBMULTISET", "SUBSTRING", "SUBSTRING_REGEX",
            "SUCCEEDS", "SUM", "SYMMETRIC", "SYSID", "SYSTEM", "SYSTEM_USER", "TABLE",
            "TABLES", "TABLESAMPLE", "TABLESPACE", "TABLE_NAME", "TEMP", "TEMPLATE",
            "TEMPORARY", "TEXT", "THEN", "TIES", "TIME", "TIMEZONE_HOUR",
            "TIMEZONE_MINUTE", "TO", "TOKEN", "TOP_LEVEL_COUNT", "TRAILING", "TRANSACTION",
            "TRANSACTIONS_COMMITTED", "TRANSACTIONS_ROLLED_BACK", "TRANSACTION_ACTIVE",
            "TRANSFORM", "TRANSFORMS", "TRANSLATE", "TRANSLATE_REGEX", "TRANSLATION",
            "TREAT", "TRIGGER", "TRIGGER_CATALOG", "TRIGGER_NAME", "TRIGGER_SCHEMA",
            "TRIM", "TRIM_ARRAY", "TRUE", "TRUNCATE", "TRUSTED", "TYPE", "TYPES",
            "UESCAPE", "UNBOUNDED", "UNCOMMITTED", "UNDER", "UNENCRYPTED", "UNION", "UNIQUE",
            "UNKNOWN", "UNLISTEN", "UNLOGGED", "UNTIL", "UPDATE", "UPPER", "USAGE",
            "USER", "USER_DEFINED_TYPE_CATALOG", "USER_DEFINED_TYPE_CODE", "USER_DEFINED_TYPE_NAME",
            "USER_DEFINED_TYPE_SCHEMA", "USING", "VACUUM", "VALID", "VALIDATE", "VALIDATOR",
            "VALUE", "VALUES", "VARCHAR", "VARIADIC", "VARYING", "VAR_POP", "VAR_SAMP",
            "VERBOSE", "VERSION", "VIEW", "VIEWS", "VOLATILE", "WHEN", "WHENEVER", "WHERE",
            "WHITESPACE", "WIDTH_BUCKET", "WINDOW", "WITH", "WITHIN", "WITHOUT", "WORK",
            "WRAPPER", "WRITE", "XML", "XMLATTRIBUTES", "XMLCONCAT", "XMLELEMENT",
            "XMLEXISTS", "XMLFOREST", "XMLNAMESPACES", "XMLPARSE", "XMLPI", "XMLROOT",
            "XMLSERIALIZE", "XMLTABLE", "YEAR", "YES", "ZONE");

    /**
     * Validates whether the provided table or column name is valid according to PostgreSQL naming conventions
     * and does not conflict with reserved keywords.<br>
     * <br>
     * The method provided is designed as an initial layer of defense against SQL injection by applying naming conventions intended to reduce the risk of malicious input.<br>
     * However, Essentials components as well as {@link PostgresqlUtil#checkIsValidTableOrColumnName(String)} does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL against SQL injection threats.<br>
     * <b>The responsibility for implementing protective measures against SQL Injection lies exclusively with the users/developers using the Essentials components and its supporting classes.<br>
     * Users must ensure thorough sanitization and validation of API input parameters,  column, table, and index names.<br>
     * Insufficient attention to these practices may leave the application vulnerable to SQL injection, potentially endangering the security and integrity of the database.<br>
     * <p>
     * The method checks if the {@code tableOrColumnName}:
     * <ul>
     *     <li>Is not null, empty, and does not consist solely of whitespace.</li>
     *     <li>Does not match any PostgreSQL reserved keyword (case-insensitive check).</li>
     *     <li>Contains only characters valid for PostgreSQL identifiers: letters, digits, and underscores,
     *         and does not start with a digit.</li>
     * </ul>
     * <p>
     *
     * @param tableOrColumnName the table or column name to validate.
     * @throws InvalidTableOrColumnNameException if the provided name is null, empty, matches a reserved keyword,
     *                                           or contains invalid characters.
     */
    public static void checkIsValidTableOrColumnName(String tableOrColumnName) {
        if (tableOrColumnName == null || tableOrColumnName.trim().isEmpty()) {
            throw new InvalidTableOrColumnNameException("Table or column name cannot be null or empty.");
        }

        // Check against reserved keywords
        String upperCaseName = tableOrColumnName.toUpperCase().trim();
        if (RESERVED_KEYWORDS.contains(upperCaseName)) {
            throw new InvalidTableOrColumnNameException(msg("The name '{}' is a reserved keyword and cannot be used as a table or column name.", tableOrColumnName));
        }

        // Validate characters in the name
        if (!VALID_SQL_TABLE_AND_COLUMN_NAME_PATTERN.matcher(tableOrColumnName).matches()) {
            throw new InvalidTableOrColumnNameException(msg("Invalid table or column name: '{}'. Names must start with a letter or underscore, followed by letters, digits, or underscores.", tableOrColumnName));
        }
    }
}
