/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.xilinjia.krdb.internal.interop

/**
 * Wrapper for C-API `realm_app_errno_client`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2553
 */
expect enum class ErrorCode : CodeDescription {
    RLM_ERR_NONE,
    RLM_ERR_RUNTIME,
    RLM_ERR_RANGE_ERROR,
    RLM_ERR_BROKEN_INVARIANT,
    RLM_ERR_OUT_OF_MEMORY,
    RLM_ERR_OUT_OF_DISK_SPACE,
    RLM_ERR_ADDRESS_SPACE_EXHAUSTED,
    RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED,
    RLM_ERR_INCOMPATIBLE_SESSION,
    RLM_ERR_INCOMPATIBLE_LOCK_FILE,
    RLM_ERR_INVALID_QUERY,
    RLM_ERR_BAD_VERSION,
    RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION,
    RLM_ERR_MULTIPLE_SYNC_AGENTS,
    RLM_ERR_OBJECT_ALREADY_EXISTS,
    RLM_ERR_NOT_CLONABLE,
    RLM_ERR_BAD_CHANGESET,
    RLM_ERR_SUBSCRIPTION_FAILED,
    RLM_ERR_FILE_OPERATION_FAILED,
    RLM_ERR_FILE_PERMISSION_DENIED,
    RLM_ERR_FILE_NOT_FOUND,
    RLM_ERR_FILE_ALREADY_EXISTS,
    RLM_ERR_INVALID_DATABASE,
    RLM_ERR_DECRYPTION_FAILED,
    RLM_ERR_INCOMPATIBLE_HISTORIES,
    RLM_ERR_FILE_FORMAT_UPGRADE_REQUIRED,
    RLM_ERR_SCHEMA_VERSION_MISMATCH,
    RLM_ERR_NO_SUBSCRIPTION_FOR_WRITE,
    RLM_ERR_OPERATION_ABORTED,
    RLM_ERR_AUTO_CLIENT_RESET_FAILED,
    RLM_ERR_BAD_SYNC_PARTITION_VALUE,
    RLM_ERR_CONNECTION_CLOSED,
    RLM_ERR_INVALID_SUBSCRIPTION_QUERY,
    RLM_ERR_SYNC_CLIENT_RESET_REQUIRED,
    RLM_ERR_SYNC_COMPENSATING_WRITE,
    RLM_ERR_SYNC_CONNECT_FAILED,
    RLM_ERR_SYNC_CONNECT_TIMEOUT,
    RLM_ERR_SYNC_INVALID_SCHEMA_CHANGE,
    RLM_ERR_SYNC_PERMISSION_DENIED,
    RLM_ERR_SYNC_PROTOCOL_INVARIANT_FAILED,
    RLM_ERR_SYNC_PROTOCOL_NEGOTIATION_FAILED,
    RLM_ERR_SYNC_SERVER_PERMISSIONS_CHANGED,
    RLM_ERR_SYNC_USER_MISMATCH,
    RLM_ERR_TLS_HANDSHAKE_FAILED,
    RLM_ERR_WRONG_SYNC_TYPE,
    RLM_ERR_SYNC_WRITE_NOT_ALLOWED,
    RLM_ERR_SYNC_LOCAL_CLOCK_BEFORE_EPOCH,
    RLM_ERR_SYNC_SCHEMA_MIGRATION_ERROR,
    RLM_ERR_SYSTEM_ERROR,
    RLM_ERR_LOGIC,
    RLM_ERR_NOT_SUPPORTED,
    RLM_ERR_BROKEN_PROMISE,
    RLM_ERR_CROSS_TABLE_LINK_TARGET,
    RLM_ERR_KEY_ALREADY_USED,
    RLM_ERR_WRONG_TRANSACTION_STATE,
    RLM_ERR_WRONG_THREAD,
    RLM_ERR_ILLEGAL_OPERATION,
    RLM_ERR_SERIALIZATION_ERROR,
    RLM_ERR_STALE_ACCESSOR,
    RLM_ERR_INVALIDATED_OBJECT,
    RLM_ERR_READ_ONLY_DB,
    RLM_ERR_DELETE_OPENED_REALM,
    RLM_ERR_MISMATCHED_CONFIG,
    RLM_ERR_CLOSED_REALM,
    RLM_ERR_INVALID_TABLE_REF,
    RLM_ERR_SCHEMA_VALIDATION_FAILED,
    RLM_ERR_SCHEMA_MISMATCH,
    RLM_ERR_INVALID_SCHEMA_VERSION,
    RLM_ERR_INVALID_SCHEMA_CHANGE,
    RLM_ERR_MIGRATION_FAILED,
    RLM_ERR_TOP_LEVEL_OBJECT,
    RLM_ERR_INVALID_ARGUMENT,
    RLM_ERR_PROPERTY_TYPE_MISMATCH,
    RLM_ERR_PROPERTY_NOT_NULLABLE,
    RLM_ERR_READ_ONLY_PROPERTY,
    RLM_ERR_MISSING_PROPERTY_VALUE,
    RLM_ERR_MISSING_PRIMARY_KEY,
    RLM_ERR_UNEXPECTED_PRIMARY_KEY,
    RLM_ERR_MODIFY_PRIMARY_KEY,
    RLM_ERR_INVALID_QUERY_STRING,
    RLM_ERR_INVALID_PROPERTY,
    RLM_ERR_INVALID_NAME,
    RLM_ERR_INVALID_DICTIONARY_KEY,
    RLM_ERR_INVALID_DICTIONARY_VALUE,
    RLM_ERR_INVALID_SORT_DESCRIPTOR,
    RLM_ERR_INVALID_ENCRYPTION_KEY,
    RLM_ERR_INVALID_QUERY_ARG,
    RLM_ERR_NO_SUCH_OBJECT,
    RLM_ERR_INDEX_OUT_OF_BOUNDS,
    RLM_ERR_LIMIT_EXCEEDED,
    RLM_ERR_OBJECT_TYPE_MISMATCH,
    RLM_ERR_NO_SUCH_TABLE,
    RLM_ERR_TABLE_NAME_IN_USE,
    RLM_ERR_ILLEGAL_COMBINATION,
    RLM_ERR_BAD_SERVER_URL,
    RLM_ERR_CUSTOM_ERROR,
    RLM_ERR_CLIENT_USER_NOT_FOUND,
    RLM_ERR_CLIENT_USER_NOT_LOGGED_IN,
    RLM_ERR_CLIENT_REDIRECT_ERROR,
    RLM_ERR_CLIENT_TOO_MANY_REDIRECTS,
    RLM_ERR_CLIENT_USER_ALREADY_NAMED,
    RLM_ERR_BAD_TOKEN,
    RLM_ERR_MALFORMED_JSON,
    RLM_ERR_MISSING_JSON_KEY,
    RLM_ERR_BAD_BSON_PARSE,
    RLM_ERR_MISSING_AUTH_REQ,
    RLM_ERR_INVALID_SESSION,
    RLM_ERR_USER_APP_DOMAIN_MISMATCH,
    RLM_ERR_DOMAIN_NOT_ALLOWED,
    RLM_ERR_READ_SIZE_LIMIT_EXCEEDED,
    RLM_ERR_INVALID_PARAMETER,
    RLM_ERR_MISSING_PARAMETER,
    RLM_ERR_TWILIO_ERROR,
    RLM_ERR_GCM_ERROR,
    RLM_ERR_HTTP_ERROR,
    RLM_ERR_AWS_ERROR,
    RLM_ERR_MONGODB_ERROR,
    RLM_ERR_ARGUMENTS_NOT_ALLOWED,
    RLM_ERR_FUNCTION_EXECUTION_ERROR,
    RLM_ERR_NO_MATCHING_RULE,
    RLM_ERR_INTERNAL_SERVER_ERROR,
    RLM_ERR_AUTH_PROVIDER_NOT_FOUND,
    RLM_ERR_AUTH_PROVIDER_ALREADY_EXISTS,
    RLM_ERR_SERVICE_NOT_FOUND,
    RLM_ERR_SERVICE_TYPE_NOT_FOUND,
    RLM_ERR_SERVICE_ALREADY_EXISTS,
    RLM_ERR_SERVICE_COMMAND_NOT_FOUND,
    RLM_ERR_VALUE_NOT_FOUND,
    RLM_ERR_VALUE_ALREADY_EXISTS,
    RLM_ERR_VALUE_DUPLICATE_NAME,
    RLM_ERR_FUNCTION_NOT_FOUND,
    RLM_ERR_FUNCTION_ALREADY_EXISTS,
    RLM_ERR_FUNCTION_DUPLICATE_NAME,
    RLM_ERR_FUNCTION_SYNTAX_ERROR,
    RLM_ERR_FUNCTION_INVALID,
    RLM_ERR_INCOMING_WEBHOOK_NOT_FOUND,
    RLM_ERR_INCOMING_WEBHOOK_ALREADY_EXISTS,
    RLM_ERR_INCOMING_WEBHOOK_DUPLICATE_NAME,
    RLM_ERR_RULE_NOT_FOUND,
    RLM_ERR_API_KEY_NOT_FOUND,
    RLM_ERR_RULE_ALREADY_EXISTS,
    RLM_ERR_RULE_DUPLICATE_NAME,
    RLM_ERR_AUTH_PROVIDER_DUPLICATE_NAME,
    RLM_ERR_RESTRICTED_HOST,
    RLM_ERR_API_KEY_ALREADY_EXISTS,
    RLM_ERR_INCOMING_WEBHOOK_AUTH_FAILED,
    RLM_ERR_EXECUTION_TIME_LIMIT_EXCEEDED,
    RLM_ERR_NOT_CALLABLE,
    RLM_ERR_USER_ALREADY_CONFIRMED,
    RLM_ERR_USER_NOT_FOUND,
    RLM_ERR_USER_DISABLED,
    RLM_ERR_AUTH_ERROR,
    RLM_ERR_BAD_REQUEST,
    RLM_ERR_ACCOUNT_NAME_IN_USE,
    RLM_ERR_INVALID_PASSWORD,
    RLM_ERR_SCHEMA_VALIDATION_FAILED_WRITE,
    RLM_ERR_APP_UNKNOWN,
    RLM_ERR_MAINTENANCE_IN_PROGRESS,
    RLM_ERR_USERPASS_TOKEN_INVALID,
    RLM_ERR_INVALID_SERVER_RESPONSE,
    RLM_ERR_APP_SERVER_ERROR,
    RLM_ERR_CALLBACK,
    RLM_ERR_UNKNOWN;

    override val nativeValue: Int
    override val description: String?

    companion object {
        fun of(nativeValue: Int): ErrorCode?
    }
}
