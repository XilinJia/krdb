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
package io.github.xilinjia.krdb.test.common

import io.github.xilinjia.krdb.VersionId
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersionIdTests {

    @Suppress("ReplaceAssertBooleanWithAssertEquality")
    @Test
    fun compareVersions() {
        assertTrue(VersionId(0) == VersionId(0))
        assertTrue(VersionId(1) > VersionId(0))
        assertTrue(VersionId(1) >= VersionId(0))
        assertTrue(VersionId(1) < VersionId(2))
        assertTrue(VersionId(1) <= VersionId(2))
    }

    @Test
    fun throwsForNegativeNumbers() {
        assertFailsWith<IllegalArgumentException> { VersionId(-1) }
    }
}
