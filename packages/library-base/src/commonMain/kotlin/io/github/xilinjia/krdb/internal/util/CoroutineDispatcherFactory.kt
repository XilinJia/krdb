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

package io.github.xilinjia.krdb.internal.util

import io.github.xilinjia.krdb.internal.interop.RealmInterop
import io.github.xilinjia.krdb.internal.interop.RealmSchedulerPointer
import io.github.xilinjia.krdb.internal.platform.multiThreadDispatcher
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.internal.platform.singleThreadDispatcher
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Factory wrapper for passing around dispatchers without needing to create them. This makes it
 * possible to configure dispatchers in Configuration objects, but allow Realm instances to control
 * their lifecycle.
 */
public fun interface CoroutineDispatcherFactory {
    public companion object {

        /**
         * Let Realm create and control the dispatcher. Managed dispatchers will be closed
         * when their owner Realm/App is closed as well.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        public fun managed(name: String, threads: Int = 1): CoroutineDispatcherFactory {
            return CoroutineDispatcherFactory {
                ManagedDispatcherHolder(
                    when (threads) {
                        1 -> singleThreadDispatcher(name)
                        else -> multiThreadDispatcher(threads)
                    }
                )
            }
        }

        /**
         * Unmanaged dispatchers are dispatchers that are passed into Realm from the outside.
         * Realm should not determine when they are closed and leave that to the owner of them.
         */
        public fun unmanaged(dispatcher: CoroutineDispatcher): CoroutineDispatcherFactory {
            return CoroutineDispatcherFactory {
                UnmanagedDispatcherHolder(dispatcher)
            }
        }
    }

    /**
     * Returns the dispatcher from the factory configuration, creating it if needed.
     * If a dispatcher is created, calling this method multiple times will create a
     * new dispatcher for each call.
     */
    public fun create(): DispatcherHolder
}

/**
 * This interface wraps any dispatcher used by Realm and is used so we can track whether a
 * dispatcher has been created internally by us or has been passed in from the outside.
 *
 * This pattern is a bit awkward. But since CoroutineDispatcher is an abstract class it is
 * not feasible to wrap them as they hold a lot of internal state.
 *
 * Instead we just expose a reference to the underlying dispatcher.
 */
public sealed interface DispatcherHolder {

    /**
     * Reference to dispatcher that should be used.
     */
    public val dispatcher: CoroutineDispatcher

    /**
     * Mark the dispatcher as no longer being used. For internal dispatchers, this will also
     * close them.
     */
    public fun close()
}

@OptIn(ExperimentalCoroutinesApi::class)
private class ManagedDispatcherHolder(
    override val dispatcher: CloseableCoroutineDispatcher,
) : DispatcherHolder {
    override fun close(): Unit = dispatcher.close()
}

private class UnmanagedDispatcherHolder(
    override val dispatcher: CoroutineDispatcher
) : DispatcherHolder {
    override fun close(): Unit = Unit
}

/**
 * Object that creates a Realm scheduler based and a coroutine dispatcher, and binds their resource
 * lifecycle.
 */
public class LiveRealmContext(
    private val dispatcherHolder: DispatcherHolder,
) : DispatcherHolder by dispatcherHolder {

    public val scheduler: RealmSchedulerPointer = runBlocking {
        withContext(dispatcherHolder.dispatcher) {
            RealmInterop.realm_create_scheduler(dispatcher)
        }
    }

    override fun close() {
        // Warning: It is important to release the scheduler before closing the
        // dispatcher. Failing to do it this way might create race conditions on Darwin.
        // See SingleThreadDispatcherScheduler in RealmInterop for the Darwin
        // source set.
        scheduler.release()
        dispatcherHolder.close()
    }
}

internal fun CoroutineDispatcherFactory.createLiveRealmContext(): LiveRealmContext =
    LiveRealmContext(create())
