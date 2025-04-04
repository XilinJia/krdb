/*
 * Copyright 2021 Realm Inc.
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

actual enum class SchemaValidationMode(override val nativeValue: Int) : NativeEnumerated {
    RLM_SCHEMA_VALIDATION_BASIC(realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_BASIC),
    RLM_SCHEMA_VALIDATION_SYNC_PBS(realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_SYNC_PBS),
    RLM_SCHEMA_VALIDATION_SYNC_FLX(realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_SYNC_FLX),
    RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS(realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS),
}
