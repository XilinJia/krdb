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

package io.github.xilinjia.krdb.migration

import io.github.xilinjia.krdb.RealmConfiguration

/**
 * A base class for the various **realm migration** schemes.
 *
 * The migration scheme controls how schema and data is migrated when there are changes to the realm
 * object model.
 *
 * @see RealmConfiguration.Builder.migration
 * @see AutomaticSchemaMigration
 */
public sealed interface RealmMigration
