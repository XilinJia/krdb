package io.github.xilinjia.krdb.internal

import io.github.xilinjia.krdb.VersionId
import io.github.xilinjia.krdb.internal.interop.Callback
import io.github.xilinjia.krdb.internal.interop.RealmChangesPointer
import io.github.xilinjia.krdb.internal.interop.RealmInterop
import io.github.xilinjia.krdb.internal.interop.RealmKeyPathArrayPointer
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.internal.schema.RealmSchemaImpl
import io.github.xilinjia.krdb.internal.util.LiveRealmContext
import io.github.xilinjia.krdb.internal.util.Validation.sdkError
import io.github.xilinjia.krdb.notifications.internal.Cancellable
import io.github.xilinjia.krdb.notifications.internal.Cancellable.Companion.NO_OP_NOTIFICATION_TOKEN
import io.github.xilinjia.krdb.schema.RealmSchema
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Class responsible for controlling notifications for a Realm. It does this by wrapping a live Realm on which
 * notifications can be registered. Since all objects that are otherwise exposed to users are frozen, they need
 * to be thawed when reaching the live Realm.
 *
 * For Lists and Objects, this can result in the object no longer existing. In this case, Flows will just complete.
 * End users can catch this case by using `flow.onCompletion { ... }`.
 *
 * Users are only exposed to live objects inside a [MutableRealm], and change listeners are not supported
 * inside writes. Users can therefor not register change listeners on live objects, but it is assumed that other
 * layers check that invariant before methods on this class are called.
 */
internal class SuspendableNotifier(
    private val owner: RealmImpl,
    private val scheduler: LiveRealmContext,
) : LiveRealmHolder<LiveRealm>() {
    // Flow used to emit events when the version of the live realm is updated
    // Adding extra buffer capacity as we are otherwise never able to emit anything
    // see https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/SharedFlow.kt#L78
    private val _realmChanged = MutableSharedFlow<VersionId>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        replay = 1
    )

    val dispatcher: CoroutineDispatcher = scheduler.dispatcher

    // Could just be anonymous class, but easiest way to get BaseRealmImpl.toString to display the
    // right type with this
    private inner class NotifierRealm : LiveRealm(
        owner = owner,
        configuration = owner.configuration,
        scheduler = scheduler,
    ) {
        // This is guaranteed to be triggered before any other notifications for the same
        // update as we get all callbacks on the same single thread dispatcher
        override fun onRealmChanged() {
            super.onRealmChanged()
            if (!_realmChanged.tryEmit(version())) {
                // Should never fail to emit snapshot version as we just drop oldest
                sdkError("Failed to emit snapshot version")
            }
        }

        // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
        //  update, but requires that we initialize it all on the actual schema update to allow freezing
        //  it. If we make the schema backed by the actual realm_class_info_t/realm_property_info_t
        //  initialization it would probably be acceptable to initialize on schema updates
        override fun schema(): RealmSchema {
            return RealmSchemaImpl.fromTypedRealm(realmReference.dbPointer, realmReference.schemaMetadata)
        }
    }

    override val realmInitializer = lazy<LiveRealm> { NotifierRealm() }
    // Must only be accessed from the dispatchers thread
    override val realm: LiveRealm by realmInitializer

    /**
     * Listen to changes to a Realm.
     *
     * This flow is guaranteed to emit before any other streams listening to individual objects or
     * query results.
     */
    internal suspend fun realmChanged(): Flow<VersionId> {
        // Touching realm will open the underlying realm and register change listeners, but must
        // happen on the dispatcher as the realm can only be touched on the dispatcher's thread.
        if (!realmInitializer.isInitialized()) {
            withContext(dispatcher) {
                realm
                _realmChanged.emit(realm.version())
            }
        }
        return _realmChanged.asSharedFlow()
    }

    internal fun <T : CoreNotifiable<T, C>, C> registerObserver(flowable: Observable<T, C>, keyPathsPtr: RealmKeyPathArrayPointer?): Flow<C> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> =
                kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                // Ensure that the live realm is always up to date to avoid registering
                // notifications on newer objects.
                realm.refresh()
                val observable = flowable.notifiable()
                val lifeRef: CoreNotifiable<T, C>? = observable.coreObservable(realm)
                val changeFlow = observable.changeFlow(this@callbackFlow)
                // Only emit events during registration if the observed entity is already deleted
                // (lifeRef == null) as there is no guarantee when the first callback is delivered
                // by core (either on the version where the callback is registered or on a future
                // version if there is an ongoing transaction). If the observed entity exists upon
                // registration then the initial event will always be reported from the callback,
                // but can still be a deletion-event if the observed element is deleted at that
                // moment in time.
                if (lifeRef != null) {
                    val interopCallback: Callback<RealmChangesPointer> =
                        object : Callback<RealmChangesPointer> {
                            override fun onChange(change: RealmChangesPointer) {
                                // Notifications need to be delivered with the version they where created on, otherwise
                                // the fine-grained notification data might be out of sync.
                                // TODO Currently verifying that lifeRef is still valid to indicate
                                //  if it was actually deleted. This is only a problem for
                                //  collections as they seemed to be freezable from a deleted
                                //  reference (contrary to other objects that returns null from
                                //  freeze). An `out_collection_was_deleted` flag was added to the
                                //  change object, which would probably be the way to go, but
                                //  requires rework of our change set build infrastructure.
                                val frozenObservable: T? = if (lifeRef.isValid())
                                    lifeRef.freeze(realm.gcTrackedSnapshot())
                                else null
                                changeFlow.emit(frozenObservable, change)
                            }
                        }
                    token.value = NotificationToken(lifeRef.registerForNotification(keyPathsPtr, interopCallback))
                } else {
                    changeFlow.emit(null)
                }
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    internal fun close() {
        // FIXME Is it safe at all times to close a Realm? Probably not during a changelistener callback, but Mutexes
        //  are not supported within change listeners as they are not suspendable.
        runBlocking(dispatcher) {
            // Calling close on a non initialized Realm is wasteful since before calling RealmInterop.close
            // The Realm will be first opened (RealmInterop.open) and an instance created in vain.
            if (realmInitializer.isInitialized()) {
                realm.close()
            }
        }
    }

    /**
     * Manually force a refresh of the Realm, moving it to the latest version.
     * This will also trigger the evaluation of all change listeners, which will
     * be triggered as normal if anything changed.
     */
    suspend fun refresh() {
        return withContext(dispatcher) {
            // This logic should be safe due to the following reasons:
            // - Notifications and `refresh()` run on the same single-threaded dispatcher.
            // - `refresh()` will synchronously run notifications if the Realm is advanced.
            // - This mean that the `realm.snapshot` will have been updated synchronously
            //   through `onRealmChanged()` when `realm_refresh` completes.
            // - Thus we are guaranteed that `realm.snapshot` contains exactly the version
            //   the live Realm was advanced to when refreshing.
            val dbPointer = realm.realmReference.dbPointer
            RealmInterop.realm_refresh(dbPointer)
            val refreshedVersion = VersionId(RealmInterop.realm_get_version_id(dbPointer))
            realm.snapshotVersion.also { snapshotVersion ->
                // Assert that the above invariants never break
                if (snapshotVersion != refreshedVersion) {
                    throw IllegalStateException(
                        """
                        Live Realm and Snapshot version does not 
                        match: $refreshedVersion vs. $snapshotVersion
                        """.trimIndent()
                    )
                }
            }
        }
    }
}
