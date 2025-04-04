package io.github.xilinjia.krdb.internal

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.VersionId
import io.github.xilinjia.krdb.internal.interop.FrozenRealmPointer
import io.github.xilinjia.krdb.internal.interop.LiveRealmPointer
import io.github.xilinjia.krdb.internal.interop.RealmInterop
import io.github.xilinjia.krdb.internal.interop.RealmPointer
import io.github.xilinjia.krdb.internal.schema.CachedSchemaMetadata
import io.github.xilinjia.krdb.internal.schema.SchemaMetadata
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * A _Realm Reference_ that links a specific Kotlin BaseRealm instance with an underlying C++
 * SharedRealm.
 *
 * This is needed as each Results, List or Object need to know, both which public Realm they belong
 * to, but also what underlying SharedRealm they are part of. Each object linked to a Realm needs
 * to keep it's own C++ SharedInstance, as the owning [Realm]'s C++ SharedRealm instance is updated
 * on writes/notifications.
 *
 * For frozen Realms, the `dbPointer` will point to a specific version of a read transaction that
 * is guaranteed to not change.
 *
 * For live Realms, the `dbPointer` will point to a live SharedRealm that can advance its internal
 * version.
 *
 * NOTE: There should never be multiple RealmReferences with the same `dbPointer` as the underlying
 * C++ SharedRealm is closed when the RealmReference is no longer referenced by the [Realm].
 */
// TODO Public due to being a transitive dependency to Notifiable
public interface RealmReference : RealmState {
    public val owner: BaseRealmImpl
    public val schemaMetadata: SchemaMetadata
    public val dbPointer: RealmPointer

    override fun version(): VersionId {
        checkClosed()
        return VersionId(RealmInterop.realm_get_version_id(dbPointer))
    }
    public fun uncheckedVersion(): VersionId =
        VersionId(RealmInterop.realm_get_version_id(dbPointer))

    override fun isFrozen(): Boolean {
        checkClosed()
        return RealmInterop.realm_is_frozen(dbPointer)
    }

    override fun isClosed(): Boolean {
        return RealmInterop.realm_is_closed(dbPointer)
    }

    public fun close() {
        checkClosed()
        RealmInterop.realm_close(dbPointer)
    }

    public fun asValidLiveRealmReference(): LiveRealmReference {
        if (this !is LiveRealmReference) {
            throw IllegalStateException("Cannot modify managed objects outside of a write transaction")
        }
        checkClosed()
        return this
    }

    public fun checkClosed() {
        if (isClosed()) {
            throw IllegalStateException("Realm has been closed and is no longer accessible: ${owner.configuration.path}")
        }
    }
}

public interface FrozenRealmReference : RealmReference {
    override val dbPointer: FrozenRealmPointer
}

public data class FrozenRealmReferenceImpl(
    override val owner: BaseRealmImpl,
    override val dbPointer: FrozenRealmPointer,
    override val schemaMetadata: SchemaMetadata = CachedSchemaMetadata(
        dbPointer,
        owner.configuration.mapOfKClassWithCompanion.values
    ),
) : FrozenRealmReference {
    init {
        // realm_open/realm_freeze doesn't implicitly create a transaction which can cause the
        // underlying core version to be cleaned up if the realm is advanced before any objects,
        // queries, etc. triggers creation of the transaction. Thus, we need to force a transaction
        // on any realm references to keep the version around for future operations.
        RealmInterop.realm_begin_read(dbPointer)
    }
}

/**
 * A **live realm reference** linking to the underlying live SharedRealm with the option to update
 * schema metadata when the schema has changed.
 */
public data class LiveRealmReference(
    override val owner: BaseRealmImpl,
    override val dbPointer: LiveRealmPointer
) : RealmReference {

    override val schemaMetadata: SchemaMetadata
        get() = _schemaMetadata.value

    private val _schemaMetadata: AtomicRef<SchemaMetadata> =
        atomic(CachedSchemaMetadata(dbPointer, owner.configuration.mapOfKClassWithCompanion.values))

    /**
     * Returns a frozen realm reference of the current live realm reference.
     */
    public fun snapshot(owner: BaseRealmImpl): FrozenRealmReference {
        return FrozenRealmReferenceImpl(owner, RealmInterop.realm_freeze(dbPointer), schemaMetadata)
    }

    /**
     * Refreshes the realm reference's cached schema meta data from the current live realm reference.
     *
     * This means that any existing live realm objects will get an updated schema. This should be
     * safe as we don't expect live objects to leave the scope of the write block of [Realm.write].
     */
    public fun refreshSchemaMetadata() {
        _schemaMetadata.value = CachedSchemaMetadata(dbPointer, owner.configuration.mapOfKClassWithCompanion.values)
    }
}
