/*
 * Copyright 2023 Realm Inc.
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

package io.github.xilinjia.krdb.internal

import io.github.xilinjia.krdb.VersionId

/**
 * Version meta data for a single instance.
 */
public data class VersionData(val current: VersionId?, val active: Set<VersionId>) {
    val versions: Set<VersionId> = (current?.let { setOf(it) } ?: emptySet()) + active
}
