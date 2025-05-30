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

package io.github.xilinjia.krdb.mongodb.internal

import io.github.xilinjia.krdb.BaseRealm
import io.github.xilinjia.krdb.internal.BaseRealmImpl
import io.github.xilinjia.krdb.internal.RealmImpl
import io.github.xilinjia.krdb.internal.interop.RealmBaseSubscriptionSetPointer
import io.github.xilinjia.krdb.internal.interop.RealmInterop
import io.github.xilinjia.krdb.internal.interop.RealmSubscriptionSetPointer
import io.github.xilinjia.krdb.internal.interop.SubscriptionSetCallback
import io.github.xilinjia.krdb.internal.interop.sync.CoreSubscriptionSetState
import io.github.xilinjia.krdb.internal.util.Validation
import io.github.xilinjia.krdb.mongodb.exceptions.BadFlexibleSyncQueryException
import io.github.xilinjia.krdb.mongodb.sync.MutableSubscriptionSet
import io.github.xilinjia.krdb.mongodb.sync.SubscriptionSet
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal class SubscriptionSetImpl<T : BaseRealm>(
    realm: T,
    nativePointer: RealmSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(realm), SubscriptionSet<T> {

    private val _nativePointer: AtomicRef<RealmSubscriptionSetPointer> = atomic(nativePointer)
    override val nativePointer: RealmSubscriptionSetPointer
        get() = _nativePointer.value

    override fun getIteratorSafePointer(): RealmBaseSubscriptionSetPointer {
        return if (realm.isClosed()) {
            // If the Realm is closed, no further changes can happen to the SubscriptionSet,
            // So just return the current set
            nativePointer
        } else {
            RealmInterop.realm_sync_get_latest_subscriptionset(
                (realm as BaseRealmImpl).realmReference.dbPointer
            )
        }
    }

    override fun close() {
        nativePointer.release()
    }

    override suspend fun update(block: MutableSubscriptionSet.(realm: T) -> Unit): SubscriptionSet<T> {
        checkClosed()
        val ptr = RealmInterop.realm_sync_make_subscriptionset_mutable(nativePointer)
        val mut = MutableSubscriptionSetImpl(realm, ptr)
        try {
            mut.block(realm)
            _nativePointer.value = RealmInterop.realm_sync_subscriptionset_commit(ptr)
        } finally {
            // Manually release the MutableSubscriptionSetPointer as it holds on to DB resources
            // that should not be controlled by the GC.
            ptr.release()
        }
        return this
    }

    override suspend fun waitForSynchronization(timeout: Duration): Boolean {
        checkClosed()
        Validation.require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }

        // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
        // that results in the `Continuation` being frozen, which breaks it.
        val channel = Channel<Any>(1)
        try {
            val result: Any = withTimeout(timeout) {
                // TODO Assuming this is always a RealmImpl is probably dangerous. But should be safe until we introduce a public DynamicRealm.
                withContext((realm as RealmImpl).notificationScheduler.dispatcher) {
                    val callback = SubscriptionSetCallback { state ->
                        when (state) {
                            CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_COMPLETE -> {
                                channel.trySend(true)
                            }
                            CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_ERROR -> {
                                channel.trySend(false)
                            }
                            else -> {
                                // Ignore all other states, wait for either complete or error.
                            }
                        }
                    }
                    RealmInterop.realm_sync_on_subscriptionset_state_change_async(
                        nativePointer,
                        CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_COMPLETE,
                        callback
                    )
                    channel.receive()
                }
            }
            refresh()
            // Also refresh the Realm as the data has only been written on a background thread
            // when this is called. So the user facing Realm might not see the data yet.
            //
            if (realm is RealmImpl) {
                realm.refresh()
            } else {
                // Currently we only support accessing subscriptions through
                // `Realm.subscriptions`.
                TODO("Calling `waitForSynchronization` on this type of Realm is not supported: $realm")
            }

            when (result) {
                is Boolean -> {
                    if (result) {
                        return true
                    } else {
                        throw BadFlexibleSyncQueryException(errorMessage, isFatal = false)
                    }
                }
                else -> throw IllegalStateException("Unexpected value: $result")
            }
        } catch (ex: TimeoutCancellationException) {
            // Don't throw if timeout is hit, instead just return false per the API contract.
            return false
        } finally {
            channel.close()
        }
    }

    override fun refresh(): SubscriptionSet<T> {
        checkClosed()
        RealmInterop.realm_sync_subscriptionset_refresh(nativePointer)
        return this
    }
}
