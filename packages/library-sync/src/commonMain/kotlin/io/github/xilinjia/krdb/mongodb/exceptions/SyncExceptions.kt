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

package io.github.xilinjia.krdb.mongodb.exceptions

import io.github.xilinjia.krdb.internal.asPrimitiveRealmAnyOrElse
import io.github.xilinjia.krdb.internal.interop.sync.CoreCompensatingWriteInfo
import io.github.xilinjia.krdb.mongodb.sync.SyncSession
import io.github.xilinjia.krdb.types.RealmAny

/**
 * This exception is considered the top-level exception or general "catch-all" for problems related
 * to using Device Sync.
 *
 * This exception and subclasses of it will be passed to users through
 * [io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration.Builder.errorHandler] and the the exact reason
 * must be found in [Throwable.message].
 *
 * @see io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration.Builder.errorHandler
 */
public open class SyncException internal constructor(message: String?, isFatal: Boolean) : AppException(message) {
    /**
     * Flag to indicate that something has gone wrong with Device Sync in a way that is not
     * recoverable and [SyncSession] will be [SyncSession.State.INACTIVE] until this error is
     * resolved.
     *
     * It is still possible to use the Realm locally after receiving an error where this flag is
     * true. However, this must be done with caution as data written to the realm after this point
     * risk getting lost as many errors of this category will result in a Client Reset once the
     * client re-connects to the server.
     */
    public val isFatal: Boolean = isFatal
}

/**
 * Thrown when something has gone wrong with Device Sync in a way that is not recoverable.
 *
 * Generally, errors of this kind are due to incompatible versions of Realm and Atlas App Services
 * being used or bugs in the library or on the server, and the only fix would be installing a new
 * version of the app with a new version of Realm.
 *
 * It is still possible to use the Realm locally after this error occurs. However, this must be
 * done with caution as data written to the realm after this point risk getting lost as
 * many errors of this category will result in a Client Reset once the client
 * re-connects to the server.
 *
 * @see io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration.Builder.errorHandler
 */
@Deprecated("This will be removed in the future. Test for SyncException.isFatal instead.")
public open class UnrecoverableSyncException internal constructor(message: String) :
    SyncException(message, true)

/**
 * Thrown when the type of sync used by the server does not match the one used by the client, i.e.
 * the server and client disagrees whether to use Partition-based or Flexible Sync.
 */
@Suppress("DEPRECATION")
public class WrongSyncTypeException internal constructor(message: String) :
    UnrecoverableSyncException(message)

/**
 * Thrown when the server does not support one or more of the queries defined in the
 * [io.github.xilinjia.krdb.mongodb.sync.SubscriptionSet].
 */
public class BadFlexibleSyncQueryException internal constructor(message: String?, isFatal: Boolean) :
    SyncException(message, isFatal)

/**
 * Thrown when the server undoes one or more client writes. Details on undone writes can be found in
 * [writes].
 */
public class CompensatingWriteException internal constructor(
    message: String,
    compensatingWrites: Array<CoreCompensatingWriteInfo>,
    isFatal: Boolean
) : SyncException(message, isFatal) {
    /**
     * List of all the objects created that has been reversed as part of triggering this exception.
     */
    public val writes: List<CompensatingWriteInfo> = compensatingWrites.map {
        CompensatingWriteInfo(
            reason = it.reason,
            objectType = it.objectName,
            primaryKey = it.primaryKey.asPrimitiveRealmAnyOrElse {
                // We currently don't support objects as primary keys, return a String value to avoid
                // throwing within an exception.
                RealmAny.create("Unknown")
            },
        )
    }

    /**
     * Class that describes the details for a reversed write.
     */
    public inner class CompensatingWriteInfo(
        /**
         * Reason for the compensating write.
         */
        public val reason: String,

        /**
         * Name of the object class for which a write has been reversed.
         */
        public val objectType: String,

        /**
         * Primary key value for the object for which a write has been reversed.
         */
        public val primaryKey: RealmAny?
    )
}
