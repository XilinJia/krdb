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
@file:Suppress("invisible_member", "invisible_reference")
package io.github.xilinjia.krdb.test.mongodb.common.internal

import io.github.xilinjia.krdb.internal.interop.SyncConnectionParams
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verify that input to io.github.xilinjia.krdb.internal.interop.SyncConnectionParams is accessible.
 */
internal class SyncConnectionParamsTests {

    @Test
    fun allProperties() {
        val props = SyncConnectionParams(
            sdkVersion = "sdkVersion",
            bundleId = "bundleId",
            platformVersion = "platformVersion",
            device = "device",
            deviceVersion = "deviceVersion",
            framework = SyncConnectionParams.Runtime.JVM,
            frameworkVersion = "frameworkVersion",
        )
        assertEquals("Kotlin", props.sdkName)
        assertEquals("sdkVersion", props.sdkVersion)
        assertEquals("bundleId", props.bundleId)
        assertEquals("platformVersion", props.platformVersion)
        assertEquals("device", props.device)
        assertEquals("deviceVersion", props.deviceVersion)
        assertEquals("JVM", props.framework)
        assertEquals("frameworkVersion", props.frameworkVersion)
    }
}
