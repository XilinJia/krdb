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
import io.github.xilinjia.krdb.entities.sync.flx.FlexParentObject
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.mongodb.exceptions.BadFlexibleSyncQueryException
import io.github.xilinjia.krdb.mongodb.subscriptions
import io.github.xilinjia.krdb.mongodb.sync.Subscription
import io.github.xilinjia.krdb.mongodb.sync.SubscriptionSetState
import io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration
import io.github.xilinjia.krdb.test.mongodb.TestApp
import io.github.xilinjia.krdb.test.mongodb.common.utils.waitForSynchronizationOrFail
import io.github.xilinjia.krdb.test.mongodb.createUserAndLogIn
import io.github.xilinjia.krdb.test.mongodb.use
import io.github.xilinjia.krdb.test.mongodb.util.DefaultFlexibleSyncAppInitializer
import io.github.xilinjia.krdb.test.mongodb.util.DefaultPartitionBasedAppInitializer
import io.github.xilinjia.krdb.test.util.TestHelper
import io.github.xilinjia.krdb.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Class wrapping tests for SubscriptionSets
 */
class SubscriptionSetTests {

    private lateinit var app: TestApp
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultFlexibleSyncAppInitializer)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val config = SyncConfiguration.Builder(
            user,
            schema = FLEXIBLE_SYNC_SCHEMA
        )
            .build()
        realm = Realm.open(config)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        if (this::app.isInitialized) {
            app.close()
        }
    }

    // Verify that we only have a single SubscriptionSet instance exposed to end users
    @Test
    fun realmSubscriptionsReturnSameInstance() {
        val sub1 = realm.subscriptions
        val sub2 = realm.subscriptions
        assertSame(sub1, sub2)
    }

    @Test
    fun subscriptions_failOnNonFlexibleSyncRealms() {
        TestApp(this::class.simpleName, DefaultPartitionBasedAppInitializer).use { testApp ->
            val (email, password) = TestHelper.randomEmail() to "password1234"
            val user = runBlocking {
                testApp.createUserAndLogIn(email, password)
            }
            val config = SyncConfiguration.create(
                user,
                TestHelper.randomPartitionValue(),
                PARTITION_BASED_SCHEMA
            )
            Realm.open(config).use { partionBasedRealm ->
                assertFailsWith<IllegalStateException> { partionBasedRealm.subscriptions }
            }
        }
    }

    @Test
    fun subscriptions_throwsOnClosedRealm() {
        realm.close()
        assertFailsWith<IllegalStateException> { realm.subscriptions }
    }

    @Test
    fun initialSubscriptions() {
        val subscriptions = realm.subscriptions
        assertEquals(0, subscriptions.size)
        val initialState = subscriptions.state
        val expectedStates = listOf(
            SubscriptionSetState.PENDING,
            SubscriptionSetState.BOOTSTRAPPING,
            SubscriptionSetState.COMPLETE,
        )
        assertTrue(expectedStates.contains(initialState), "State was: $initialState")
    }

    @Test
    fun findByQuery() = runBlocking {
        val query = realm.query<FlexParentObject>()
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.findByQuery(query))
        subscriptions.update { add(query) }
        val sub: Subscription = subscriptions.findByQuery(query)!!
        assertNotNull(sub)
        assertEquals("FlexParentObject", sub.objectType)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
    }

    @Test
    fun findByName() = runBlocking {
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.findByName("foo"))
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe("foo")
        }
        val sub: Subscription = subscriptions.findByName("foo")!!
        assertNotNull(sub)
        assertEquals("foo", sub.name)
    }

    @Test
    fun state() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe("test1")
        }
        assertEquals(SubscriptionSetState.PENDING, subscriptions.state)
        subscriptions.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        subscriptions.update {
            // Flexible Sync queries cannot use limit
            realm.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe("test2")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            subscriptions.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, subscriptions.state)
    }

    @Test
    fun size() = runBlocking {
        val subscriptions = realm.subscriptions
        assertEquals(0, subscriptions.size)
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe()
        }
        assertEquals(1, subscriptions.size)
        subscriptions.update {
            removeAll()
        }
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun errorMessage() = runBlocking {
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.errorMessage)
        subscriptions.update {
            realm.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe()
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            subscriptions.waitForSynchronization()
        }
        assertTrue(subscriptions.errorMessage!!.contains("Invalid query: invalid RQL for table \"FlexParentObject\": syntax error: unexpected Limit, expecting Or or RightParenthesis"))
        subscriptions.update {
            removeAll()
        }
        subscriptions.waitForSynchronizationOrFail()
        assertNull(subscriptions.errorMessage)
    }

    @Test
    fun iterator_zeroSize() {
        val subscriptions = realm.subscriptions
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun iterator() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe("sub1")
        }
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("sub1", iterator.next().name)
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        Unit
    }

    @Test
    fun waitForSynchronizationInitialSubscriptions() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun waitForSynchronizationInitialEmptySubscriptionSet() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update { /* Do nothing */ }
        subscriptions.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun waitForSynchronization_success() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<FlexParentObject>().subscribe("test")
        }
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        updatedSubs.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun waitForSynchronization_error() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe("test")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            updatedSubs.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, updatedSubs.state)
        assertTrue(updatedSubs.errorMessage!!.contains("Invalid query: invalid RQL for table \"FlexParentObject\": syntax error: unexpected Limit, expecting Or or RightParenthesis"))
    }

    // Test case for https://github.com/realm/realm-core/issues/5504
    @Test
    fun waitForSynchronization_errorOnDescriptors() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<FlexParentObject>().limit(1).subscribe("test")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            updatedSubs.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, updatedSubs.state)
        assertEquals("TRUEPREDICATE and TRUEPREDICATE LIMIT(1)", updatedSubs.first().queryDescription)
        assertTrue(updatedSubs.errorMessage!!.contains("Invalid query: invalid RQL for table \"FlexParentObject\": syntax error: unexpected Limit, expecting Or or RightParenthesis"))
    }

    @Test
    fun waitForSynchronization_timeOut() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<FlexParentObject>().subscribe()
        }
        assertTrue(updatedSubs.waitForSynchronization(1.minutes))
    }

    @Test
    fun waitForSynchronization_timeOutFails() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<FlexParentObject>().subscribe()
        }
        assertFalse(updatedSubs.waitForSynchronization(1.nanoseconds))
    }

    @Test
    fun methodsOnClosedRealm() = runBlocking {
        // SubscriptionSets own their own DB resources, which is disconnected from the
        // user facing Realm. This means that the subscription set technically can still
        // be modified after the Realm is closed, but since this would produce awkward interactions
        // with other API's that work on the Realm file, we should disallow modifying the
        // SubscriptionSet if the Realm is closed. Just accessing data should be fine.
        val subs = realm.subscriptions.update {
            realm.query<FlexParentObject>().subscribe("sub")
        }.also {
            it.waitForSynchronizationOrFail()
        }
        realm.close()

        // Valid methods
        assertEquals(1, subs.size)
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertNull(subs.errorMessage)
        assertNotNull(subs.findByName("sub"))
        // `findByQuery` does not work as queries will throw on closed Realms.
        val iter = subs.iterator()
        assertTrue(iter.hasNext())
        assertNotNull(iter.next())

        // These methods will throw
        assertFailsWith<IllegalStateException> {
            subs.refresh()
        }
        assertFailsWith<IllegalStateException> {
            subs.waitForSynchronization()
        }
        assertFailsWith<IllegalStateException> {
            subs.update { /* Do nothing */ }
        }

        // Reading subscription data will also work
        assertEquals("sub", subs.first().name)
    }
}
