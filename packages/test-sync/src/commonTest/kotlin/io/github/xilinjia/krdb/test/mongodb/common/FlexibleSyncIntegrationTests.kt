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

package io.github.xilinjia.krdb.test.mongodb.common

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.entities.sync.flx.FlexChildObject
import io.github.xilinjia.krdb.entities.sync.flx.FlexEmbeddedObject
import io.github.xilinjia.krdb.entities.sync.flx.FlexParentObject
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.mongodb.exceptions.CompensatingWriteException
import io.github.xilinjia.krdb.mongodb.exceptions.DownloadingRealmTimeOutException
import io.github.xilinjia.krdb.mongodb.exceptions.SyncException
import io.github.xilinjia.krdb.mongodb.subscriptions
import io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration
import io.github.xilinjia.krdb.mongodb.sync.SyncSession
import io.github.xilinjia.krdb.mongodb.syncSession
import io.github.xilinjia.krdb.test.mongodb.TestApp
import io.github.xilinjia.krdb.test.mongodb.common.utils.uploadAllLocalChangesOrFail
import io.github.xilinjia.krdb.test.mongodb.common.utils.waitForSynchronizationOrFail
import io.github.xilinjia.krdb.test.mongodb.createUserAndLogIn
import io.github.xilinjia.krdb.test.mongodb.util.DefaultFlexibleSyncAppInitializer
import io.github.xilinjia.krdb.test.util.TestChannel
import io.github.xilinjia.krdb.test.util.TestHelper
import io.github.xilinjia.krdb.test.util.receiveOrFail
import io.github.xilinjia.krdb.test.util.use
import kotlinx.atomicfu.atomic
import org.mongodb.kbson.BsonObjectId
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Integration smoke tests for Flexible Sync. This is not intended to cover all cases, but just
 * test common scenarios.
 */
class FlexibleSyncIntegrationTests {

    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultFlexibleSyncAppInitializer)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun downloadInitialData() = runBlocking {
        val randomSection = Random.nextInt() // Generate random name to allow replays of unit tests

        // Upload data from user 1
        val user1 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config1 = SyncConfiguration.create(user1, FLEXIBLE_SYNC_SCHEMA)
        Realm.open(config1).use { realm1 ->
            val subs = realm1.subscriptions.update {
                add(realm1.query<FlexParentObject>("section = $0", randomSection))
            }
            subs.waitForSynchronizationOrFail()
            realm1.write {
                copyToRealm(FlexParentObject(randomSection).apply { name = "red" })
                copyToRealm(FlexParentObject(randomSection).apply { name = "blue" })
            }
            realm1.syncSession.uploadAllLocalChangesOrFail()
        }

        // Download data from user 2
        val user2 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config2 = SyncConfiguration.Builder(user2, FLEXIBLE_SYNC_SCHEMA)
            .initialSubscriptions { realm ->
                add(
                    realm.query<FlexParentObject>(
                        "section = $0 AND name = $1",
                        randomSection,
                        "blue"
                    )
                )
            }
            .waitForInitialRemoteData(timeout = 1.minutes)
            .build()

        Realm.open(config2).use { realm2 ->
            assertEquals(1, realm2.query<FlexParentObject>().count().find())
        }
    }

    @Test
    fun writeFailsIfNoSubscription() = runBlocking {
        val user = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config = SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA)
            .build()

        Realm.open(config).use { realm ->
            realm.writeBlocking {
                assertFailsWith<IllegalArgumentException> {
                    // This doesn't trigger a client reset event, it is caught by Core instead
                    copyToRealm(FlexParentObject().apply { name = "red" })
                }
            }
        }
    }

    @Test
    fun dataIsDeletedWhenSubscriptionIsRemoved() = runBlocking {
        val randomSection = Random.nextInt() // Generate random section to allow replays of unit tests

        val user = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config = SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA).build()
        Realm.open(config).use { realm ->
            realm.subscriptions.update {
                val query = realm.query<FlexParentObject>()
                    .query("section = $0", randomSection)
                    .query("(name = 'red' OR name = 'blue')")
                add(query, "sub")
            }
            realm.subscriptions.waitForSynchronizationOrFail()
            realm.write {
                copyToRealm(FlexParentObject(randomSection).apply { name = "red" })
                copyToRealm(FlexParentObject(randomSection).apply { name = "blue" })
            }
            assertEquals(2, realm.query<FlexParentObject>().count().find())
            realm.subscriptions.update {
                val query = realm.query<FlexParentObject>("section = $0 AND name = 'red'", randomSection)
                add(query, "sub", updateExisting = true)
            }
            realm.subscriptions.waitForSynchronizationOrFail()
            assertEquals(1, realm.query<FlexParentObject>().count().find())
        }
    }

    @Test
    fun initialSubscriptions_timeOut() {
        val config = SyncConfiguration.Builder(app.currentUser!!, FLEXIBLE_SYNC_SCHEMA)
            .initialSubscriptions { realm ->
                repeat(10) {
                    add(realm.query<FlexParentObject>("section = $0", it))
                }
            }
            .waitForInitialRemoteData(1.nanoseconds)
            .build()
        assertFailsWith<DownloadingRealmTimeOutException> {
            Realm.open(config).use {
                fail("Realm should not have opened in time.")
            }
        }
    }

    // Make sure that if `rerunOnOpen` and `waitForInitialRemoteData` is set, we don't
    // open the Realm until all new subscription data is downloaded.
    @Test
    fun rerunningInitialSubscriptionsAndWaitForInitialRemoteData() = runBlocking {
        val randomSection = Random.nextInt() // Generate random name to allow replays of unit tests

        // Prepare some user data
        val user1 = app.createUserAndLogin()
        val config1 = SyncConfiguration.create(user1, FLEXIBLE_SYNC_SCHEMA)
        Realm.open(config1).use { realm ->
            assertTrue(
                realm.subscriptions.update {
                    add(realm.query<FlexParentObject>("section = $0", randomSection))
                }.waitForSynchronization(4.minutes),
                "Failed to update subscriptions in time"
            )

            realm.write {
                repeat(10) { counter ->
                    copyToRealm(
                        FlexParentObject().apply {
                            section = randomSection
                            name = "Name-$counter"
                        }
                    )
                }
            }
            realm.syncSession.uploadAllLocalChangesOrFail()
        }

        // User 2 opens a Realm twice
        val counter = atomic(0)
        val user2 = app.createUserAndLogin()
        val config2 = SyncConfiguration.Builder(user2, FLEXIBLE_SYNC_SCHEMA)
            .initialSubscriptions(rerunOnOpen = true) { realm ->
                add(
                    realm.query<FlexParentObject>(
                        "section = $0 AND name = $1",
                        randomSection,
                        "Name-${counter.getAndIncrement()}"
                    )
                )
            }
            .waitForInitialRemoteData(2.minutes)
            .build()

        Realm.open(config2).use { realm ->
            assertEquals(1, realm.query<FlexParentObject>().count().find())
        }
        Realm.open(config2).use { realm ->
            assertEquals(2, realm.query<FlexParentObject>().count().find())
        }
    }

    @Suppress("LongMethod")
    @Test
    fun roundTripLinkedAndEmbeddedObjects() = runBlocking {
        val randomSection = Random.nextInt() // Generate random name to allow replays of unit tests

        // Upload data from user 1
        val user1 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config1 = SyncConfiguration.create(user1, FLEXIBLE_SYNC_SCHEMA)
        Realm.open(config1).use { realm1 ->
            val subs = realm1.subscriptions.update {
                add(realm1.query<FlexParentObject>("section = $0", randomSection))
                add(realm1.query<FlexChildObject>("section = $0", randomSection))
            }
            subs.waitForSynchronizationOrFail()
            realm1.write {
                copyToRealm(
                    FlexParentObject(randomSection).apply {
                        name = "red"
                        child = FlexChildObject().apply {
                            section = randomSection
                            name = "redChild"
                        }
                        embedded = FlexEmbeddedObject().apply {
                            embeddedName = "redEmbedded"
                        }
                    }
                )
                copyToRealm(
                    FlexParentObject(randomSection).apply {
                        name = "blue"
                        child = FlexChildObject().apply {
                            section = randomSection
                            name = "blueChild"
                        }
                        embedded = FlexEmbeddedObject().apply {
                            embeddedName = "blueEmbedded"
                        }
                    }
                )
            }
            realm1.syncSession.uploadAllLocalChangesOrFail()
        }

        // Download data from user 2
        val user2 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config2 = SyncConfiguration.Builder(user2, FLEXIBLE_SYNC_SCHEMA)
            .initialSubscriptions { realm ->
                add(
                    realm.query<FlexParentObject>(
                        "section = $0 AND name = $1",
                        randomSection,
                        "blue"
                    )
                )
                add(realm.query<FlexChildObject>("section = $0", randomSection))
            }
            .waitForInitialRemoteData(timeout = 1.minutes)
            .build()

        Realm.open(config2).use { realm2 ->
            assertEquals(1, realm2.query<FlexParentObject>().count().find())
            assertEquals(2, realm2.query<FlexChildObject>().count().find())
            // Embedded objects are pulled down as part of their parents
            assertEquals(1, realm2.query<FlexEmbeddedObject>().count().find())
            val obj = realm2.query<FlexParentObject>().first().find()!!
            assertEquals("blueChild", obj.child!!.name)
            assertEquals("blueEmbedded", obj.embedded!!.embeddedName)
        }
    }

    @Test
    fun compensationWrite_writeOutsideOfSubscriptionsGetsReveredByServer() {
        val user1 = app.createUserAndLogin()

        val channel = TestChannel<CompensatingWriteException>()

        val config1 = SyncConfiguration.Builder(user1, FLEXIBLE_SYNC_SCHEMA)
            .errorHandler { _: SyncSession, syncException: SyncException ->
                runBlocking {
                    channel.send(syncException as CompensatingWriteException)
                }
            }
            .build()

        runBlocking {
            val expectedPrimaryKey = BsonObjectId()

            Realm.open(config1).use { realm ->
                val objectId = BsonObjectId()

                realm.subscriptions.update {
                    add(realm.query<FlexParentObject>("_id = $0", objectId))
                }.waitForSynchronizationOrFail()

                assertNotEquals(expectedPrimaryKey, objectId)

                realm.write {
                    copyToRealm(FlexParentObject().apply { _id = expectedPrimaryKey })
                }
                realm.syncSession.uploadAllLocalChangesOrFail()
            }

            val exception: CompensatingWriteException = channel.receiveOrFail()

            assertTrue(exception.message!!.startsWith("[Sync][CompensatingWrite(1033)] Client attempted a write that is not allowed; it has been reverted Logs:"), exception.message)
            assertEquals(1, exception.writes.size)

            exception.writes[0].run {
                assertContains(reason, "object is outside of the current query view")
                assertEquals("FlexParentObject", objectType)
                assertEquals(expectedPrimaryKey, primaryKey?.asObjectId())
            }
        }
    }
}
