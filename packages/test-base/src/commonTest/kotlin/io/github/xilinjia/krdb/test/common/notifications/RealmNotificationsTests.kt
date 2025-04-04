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

package io.github.xilinjia.krdb.test.common.notifications

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.VersionId
import io.github.xilinjia.krdb.entities.Sample
import io.github.xilinjia.krdb.ext.asFlow
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.notifications.InitialRealm
import io.github.xilinjia.krdb.notifications.RealmChange
import io.github.xilinjia.krdb.notifications.UpdatedRealm
import io.github.xilinjia.krdb.test.common.utils.FlowableTests
import io.github.xilinjia.krdb.test.platform.PlatformUtils
import io.github.xilinjia.krdb.test.util.TestChannel
import io.github.xilinjia.krdb.test.util.receiveOrFail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RealmNotificationsTests : FlowableTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    override fun initialElement() {
        runBlocking {
            val c = TestChannel<RealmChange<Realm>>()
            val startingVersion = realm.version()
            val observer = async {
                realm.asFlow().collect {
                    c.send(it)
                }
            }
            c.receiveOrFail().let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            observer.cancel()
            c.close()
        }
    }

    @Test
    fun registerTwoFlows() = runBlocking {
        val c1 = TestChannel<RealmChange<Realm>>()
        val c2 = TestChannel<RealmChange<Realm>>()
        val startingVersion = realm.version()
        val observer1 = async {
            realm.asFlow().collect {
                c1.send(it)
            }
        }
        c1.receiveOrFail(message = "Failed to receive initial event on Channel 1").let { realmChange ->
            assertIs<InitialRealm<Realm>>(realmChange)
            assertEquals(startingVersion, realmChange.realm.version())
        }

        realm.write { /* Do nothing */ }
        val nextVersion = realm.version()

        c1.receiveOrFail(message = "Failed to receive update event on Channel 1").let { realmChange ->
            assertIs<UpdatedRealm<Realm>>(realmChange)
            assertEquals(nextVersion, realmChange.realm.version())
        }

        val observer2 = async {
            realm.asFlow().collect {
                c2.send(it)
            }
        }
        c2.receiveOrFail(message = "Failed to receive initial event on Channel 2").let { realmChange ->
            assertIs<InitialRealm<Realm>>(realmChange)
            assertEquals(nextVersion, realmChange.realm.version())
        }

        observer1.cancel()
        observer2.cancel()
        c1.cancel()
        c2.cancel()
    }

    @Test
    override fun asFlow() {
        runBlocking {
            val c = TestChannel<RealmChange<Realm>>()
            val startingVersion = realm.version()
            val observer = async {
                realm.asFlow().collect {
                    c.send(it)
                }
            }

            // We should first receive an initial Realm notification.
            c.receiveOrFail(message = "Failed to receive initial event").let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            realm.write { /* Do nothing */ }

            // Now we we should receive an updated Realm change notification.
            c.receiveOrFail(message = "Failed to receive update event").let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 1), realmChange.realm.version())
            }

            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val c1 = TestChannel<RealmChange<Realm>>()
            val c2 = TestChannel<RealmChange<Realm>>()
            val startingVersion = realm.version()

            val observer1 = async {
                realm.asFlow().collect {
                    c1.send(it)
                }
            }
            val observer2Cancelled = Mutex(false)
            val observer2 = async {
                realm.asFlow().collect {
                    if (!observer2Cancelled.isLocked) {
                        c2.send(it)
                    } else {
                        fail("Should not receive notifications on a canceled scope")
                    }
                }
            }

            // We should first receive an initial Realm notification.
            c1.receiveOrFail(message = "Failed to receive initial event on Channel 1").let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            c2.receiveOrFail(message = "Failed to receive initial event on Channel 2").let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            realm.write { /* Do nothing */ }

            // Now we we should receive an updated Realm change notification.
            c1.receiveOrFail(message = "Failed to receive first update event on Channel 1").let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 1), realmChange.realm.version())
            }

            c2.receiveOrFail(message = "Failed to receive first update event on Channel 2").let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 1), realmChange.realm.version())
            }

            // Stop one observer and ensure that we dont receive any more notifications in that scope
            observer2.cancel()
            observer2Cancelled.lock()

            realm.write { /* Do nothing */ }

            // But unclosed channels should receive notifications
            c1.receiveOrFail(message = "Failed to receive second update event on Channel 1").let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 2), realmChange.realm.version())
            }

            observer1.cancel()
            c1.close()
            c2.close()
        }
    }

    @Test
    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Wait for a Global change listener to become available")
    }

    @Test
    @Ignore
    override fun closingRealmDoesNotCancelFlows() {
        TODO("Wait for a Global change listener to become available")
    }

    @Test
    fun closingRealmCompletesFlow() {
        runBlocking {
            val mutex = Mutex(true)
            val observer = async {
                realm.asFlow().collect { mutex.unlock() }
            }
            mutex.lock()
            realm.close()
            withTimeout(5.seconds) {
                observer.await()
            }
        }
    }

    @Test
    fun notification_cancelsOnInsufficientBuffers() {
        val sample = realm.writeBlocking { copyToRealm(Sample()) }
        val flow = sample.asFlow()

        runBlocking {
            val listener = async {
                withTimeout(30.seconds) {
                    assertFailsWith<CancellationException> {
                        flow.collect {
                            delay(2000.milliseconds)
                        }
                    }.message!!.let { message ->
                        assertEquals(
                            "Cannot deliver object notifications. Increase dispatcher processing resources or buffer the flow with buffer(...)",
                            message
                        )
                    }
                }
            }
            (1..100).forEach {
                realm.writeBlocking {
                    findLatest(sample)!!.apply {
                        stringField = it.toString()
                    }
                }
            }
            listener.await()
        }
    }

    // This test shows that our internal logic still works (by closing the flow on deletion events)
    // even though the public consumer is dropping elements
    @Test
    fun notification_backpressureStrategyDoesNotRuinInternalLogic() {
        val sample = realm.writeBlocking { copyToRealm(Sample()) }
        val flow = sample.asFlow()
            .buffer(0, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        runBlocking {
            val listener = async {
                withTimeout(10.seconds) {
                    flow.collect {
                        delay(100.milliseconds)
                    }
                }
            }
            (1..100).forEach {
                realm.writeBlocking {
                    findLatest(sample)!!.apply {
                        stringField = it.toString()
                    }
                }
            }
            realm.write { delete(findLatest(sample)!!) }
            listener.await()
        }
    }
}
