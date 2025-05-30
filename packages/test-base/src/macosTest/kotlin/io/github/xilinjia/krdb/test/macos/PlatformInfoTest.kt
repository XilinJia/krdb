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
package io.github.xilinjia.krdb.test.macos

import io.github.xilinjia.krdb.internal.platform.OS_NAME
import io.github.xilinjia.krdb.internal.platform.OS_VERSION
import io.github.xilinjia.krdb.internal.platform.RUNTIME
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformInfoTest {
    @Test
    fun platformInfo() {
        assertEquals("Native", RUNTIME.description)
        assertEquals("MacOS", OS_NAME)
        assertTrue(OS_VERSION.startsWith("Version "))
    }
}
