/*
 * Copyright 2020 Realm Inc.
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

actual object PropertyFlags {
    actual val RLM_PROPERTY_NORMAL: Int = realm_property_flags_e.RLM_PROPERTY_NORMAL
    actual val RLM_PROPERTY_NULLABLE: Int = realm_property_flags_e.RLM_PROPERTY_NULLABLE
    actual val RLM_PROPERTY_PRIMARY_KEY: Int = realm_property_flags_e.RLM_PROPERTY_PRIMARY_KEY
    actual val RLM_PROPERTY_INDEXED: Int = realm_property_flags_e.RLM_PROPERTY_INDEXED
    actual val RLM_PROPERTY_FULLTEXT_INDEXED: Int = realm_property_flags_e.RLM_PROPERTY_FULLTEXT_INDEXED
}
