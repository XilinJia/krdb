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

package io.github.xilinjia.krdb.entities.sync.bogus

import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

/**
 * Represents the same entity as [io.github.xilinjia.krdb.entities.sync.ChildPk] but with a different `name` in
 * order to trigger an invalid schema error in Sync.
 */
class ChildPk : RealmObject {
    @Suppress("VariableNaming")
    @PrimaryKey var _id: String = ""
    @Suppress("MagicNumber")
    var name: Int = 42 // Triggers a sync error since this field is a String in the original entity
}
