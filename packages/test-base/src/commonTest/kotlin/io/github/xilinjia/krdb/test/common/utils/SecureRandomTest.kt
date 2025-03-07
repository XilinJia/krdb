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
 *
 * See: https://github.com/korlibs/korge/tree/main/krypto
 */

@file:Suppress("invisible_reference", "invisible_member")

package io.github.xilinjia.krdb.test.common.utils

import io.github.xilinjia.krdb.internal.platform.SecureRandom
import kotlin.test.Test
import kotlin.test.assertNotEquals

internal class SecureRandomTest {
    @Test
    fun nextBytes_ReturnsDifferentBytes() {
        repeat(3) {
            assertNotEquals(SecureRandom.nextBytes(16).toList(), SecureRandom.nextBytes(16).toList())
        }
    }

    @Test
    fun nextBites_ReturnsDifferentBits() {
        repeat(3) {
            assertNotEquals(SecureRandom.nextBits(16), SecureRandom.nextBits(16))
        }
    }

    @Test
    fun nextInt_ReturnsDifferentInts() {
        repeat(3) {
            assertNotEquals(SecureRandom.nextInt(), SecureRandom.nextInt())
        }
    }
}
