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
import io.github.xilinjia.krdb.entities.sync.flx.FlexParentObject
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.mongodb.subscriptions
import io.github.xilinjia.krdb.mongodb.sync.Subscription
import io.github.xilinjia.krdb.mongodb.sync.SubscriptionSet
import io.github.xilinjia.krdb.mongodb.sync.SubscriptionSetState
import io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration
import io.github.xilinjia.krdb.mongodb.syncSession
import io.github.xilinjia.krdb.test.mongodb.TestApp
import io.github.xilinjia.krdb.test.mongodb.common.utils.uploadAllLocalChangesOrFail
import io.github.xilinjia.krdb.test.mongodb.createUserAndLogIn
import io.github.xilinjia.krdb.test.mongodb.util.DefaultFlexibleSyncAppInitializer
import io.github.xilinjia.krdb.test.util.TestHelper
import io.github.xilinjia.krdb.test.util.toRealmInstant
import io.github.xilinjia.krdb.test.util.use
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Class wrapping tests for modifying a subscription set.
 */
class MutableSubscriptionSetTests {

    private lateinit var app: TestApp
    private lateinit var realm: Realm
    private lateinit var config: SyncConfiguration

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultFlexibleSyncAppInitializer)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        config = SyncConfiguration.Builder(
            user,
            schema = FLEXIBLE_SYNC_SCHEMA
        )
            .build()
        realm = Realm.open(config)
    }

    @AfterTest
    fun tearDown() {
        realm.close()
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun initialSubscriptions() = runBlocking {
        realm.subscriptions.update {
            assertEquals(0, size)
            assertEquals(SubscriptionSetState.UNCOMMITTED, state)
        }
        Unit
    }

    @Test
    fun addNamedSubscription() = runBlocking {
        val now = Clock.System.now().toRealmInstant()
        // On macOS, Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        // See https://github.com/realm/realm-kotlin/issues/846
        delay(1000)
        val updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>(), "test")
        }
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.PENDING, updatedSubs.state)
        val sub: Subscription = updatedSubs.first()
        assertEquals("test", sub.name)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
        assertTrue(now <= sub.createdAt, "Was: $now <= ${sub.createdAt}")
        assertEquals(sub.updatedAt, sub.createdAt)
    }

    @Test
    fun addAnonymousSubscription() = runBlocking {
        val now = Clock.System.now().toRealmInstant()
        // on macOS Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        delay(1000)
        val updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>())
        }
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.PENDING, updatedSubs.state)
        val sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
        assertTrue(now <= sub.createdAt, "Was: $now <= ${sub.createdAt}")
        assertEquals(sub.updatedAt, sub.createdAt)
    }

    @Test
    fun add_multiple_anonymous() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            assertEquals(0, size)
            add(realmRef.query<FlexParentObject>())
            add(realmRef.query<FlexParentObject>("section = $0", 10L))
            add(realmRef.query<FlexParentObject>("section = $0 ", 5L))
            add(realmRef.query<FlexParentObject>("section = $0", 1L))
            assertEquals(4, size)
        }
        Unit
    }

    @Test
    fun addExistingAnonymous_returnsAlreadyPersisted() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            val sub1 = add(realmRef.query<FlexParentObject>())
            val sub2 = add(realmRef.query<FlexParentObject>())
            assertEquals(sub1, sub2)
        }
        Unit
    }

    @Test
    fun addExistingNamed_returnsAlreadyPersisted() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            val sub1 = add(realmRef.query<FlexParentObject>(), "sub1")
            val sub2 = add(realmRef.query<FlexParentObject>(), "sub1")
            assertEquals(sub1, sub2)
        }
        Unit
    }

    @Test
    fun add_conflictingNamesThrows() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>(), "sub1")
            assertFailsWith<IllegalStateException> {
                add(realmRef.query<FlexParentObject>("name = $0", "foo"), "sub1")
            }
        }
        Unit
    }

    @Test
    fun update() = runBlocking {
        val subs = realm.subscriptions
        subs.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("sub1")
        }
        val createdAt = subs.first().createdAt
        subs.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>("name = $0", "red").subscribe("sub1", updateExisting = true)
        }
        val sub = subs.first()
        assertEquals("sub1", sub.name)
        assertEquals("FlexParentObject", sub.objectType)
        assertEquals("name == \"red\"", sub.queryDescription)
        assertTrue(sub.createdAt < sub.updatedAt)
        assertEquals(createdAt, sub.createdAt)
    }

    @Test
    fun removeNamed() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("test")
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(remove("test"))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeNamed_fails() = runBlocking {
        realm.subscriptions.update {
            assertFalse(remove("dont-exists"))
        }
        Unit
    }

    @Test
    fun removeSubscription_success() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("test")
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(remove(first()))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeSubscription_fails() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            val managedSub = add(realmRef.query<FlexParentObject>())
            assertTrue(remove(managedSub))
            assertFalse(remove(managedSub))
        }
        Unit
    }

    @Test
    fun removeAllStringTyped() = runBlocking {
        var updatedSubs: SubscriptionSet<Realm> = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>())
            realmRef.query<FlexParentObject>().subscribe(name = "parents")
            add(realmRef.query<FlexChildObject>())
            realmRef.query<FlexChildObject>().subscribe(name = "children")
            removeAll("FlexParentObject")
        }
        assertEquals(2, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll("FlexChildObject"))
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAllStringTyped_fails() = runBlocking {
        // Not part of schema
        realm.subscriptions.update {
            assertFailsWith<IllegalArgumentException> {
                removeAll("DontExists")
            }
        }

        // part of schema
        realm.subscriptions.update {
            assertFalse(removeAll("FlexParentObject"))
        }
        Unit
    }

    @Test
    fun removeAllClassTyped() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>())
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll(FlexParentObject::class))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAllClassTyped_fails() = runBlocking {
        // Not part of schema
        realm.subscriptions.update {
            assertFailsWith<IllegalArgumentException> {
                removeAll(io.github.xilinjia.krdb.entities.Sample::class)
            }
        }

        // part of schema
        realm.subscriptions.update {
            assertFalse(removeAll(FlexParentObject::class))
        }
        Unit
    }

    @Test
    fun removeAll() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("test")
            realmRef.query<FlexParentObject>().subscribe("test2")
        }
        assertEquals(2, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll())
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAll_anonymouslyOnly() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("test")
            realmRef.query<FlexParentObject>().subscribe()
        }
        assertEquals(2, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll(anonymousOnly = true))
            assertEquals(1, size)
        }
        assertEquals(1, updatedSubs.size)
    }

    @Test
    fun removeAll_fails() = runBlocking {
        realm.subscriptions.update {
            assertFalse(removeAll())
        }
        Unit
    }

    // Ensure that all resources are correctly torn down when an error happens inside a
    // MutableSubscriptionSet
    @Ignore // Require support for deleting synchronized Realms. See https://github.com/realm/realm-kotlin/issues/1425
    @Test
    @Suppress("TooGenericExceptionThrown")
    fun deleteFile_exceptionInsideMutableRealm() = runBlocking {
        try {
            realm.subscriptions.update {
                throw RuntimeException("Boom!")
            }
        } catch (ex: RuntimeException) {
            if (ex.message == "Boom!") {
                realm.close()
                Realm.deleteRealm(config)
            }
        }
        Unit
    }

    @Test
    fun iterator_duringWrite() = runBlocking {
        realm.subscriptions.update {
            assertFalse(iterator().hasNext())
            add(realm.query<FlexParentObject>(), name = "sub")
            var iterator = iterator()
            assertTrue(iterator.hasNext())
            val sub = iterator.next()
            assertEquals("sub", sub.name)
            assertFalse(iterator.hasNext())
            removeAll()
            iterator = iterator()
            assertFalse(iterator.hasNext())
        }
        Unit
    }

    private suspend fun uploadServerData(sectionId: Int, noOfObjects: Int) {
        val user = app.createUserAndLogin()
        val config = SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA)
            .initialSubscriptions {
                it.query<FlexParentObject>().subscribe()
            }
            .waitForInitialRemoteData()
            .build()

        Realm.open(config).use { realm ->
            realm.writeBlocking {
                repeat(noOfObjects) {
                    copyToRealm(FlexParentObject(sectionId))
                }
            }
            realm.syncSession.uploadAllLocalChangesOrFail()
        }
    }
}
