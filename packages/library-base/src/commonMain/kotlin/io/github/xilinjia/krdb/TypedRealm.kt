package io.github.xilinjia.krdb

import io.github.xilinjia.krdb.ext.realmDictionaryOf
import io.github.xilinjia.krdb.query.RealmQuery
import io.github.xilinjia.krdb.query.TRUE_PREDICATE
import io.github.xilinjia.krdb.types.RealmDictionary
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.TypedRealmObject
import kotlin.reflect.KClass

/**
 * A **typed realm** that can be queried for objects of a specific type.
 */
public interface TypedRealm : BaseRealm {

    /**
     * Returns a [RealmQuery] matching the predicate represented by [query].
     *
     * For a [Realm] instance this reflects the state of the Realm at the invocation time, this
     * the results obtained from the query will not change on updates to the Realm. For a
     * [MutableRealm] the query will produce live results and will in fact reflect updates to the
     * [MutableRealm].
     *
     * @param query the Realm Query Language predicate to append.
     * @param args Realm values for the predicate.
     */
    public fun <T : TypedRealmObject> query(
        clazz: KClass<T>,
        query: String = TRUE_PREDICATE,
        vararg args: Any?
    ): RealmQuery<T>

    /**
     * Makes an unmanaged in-memory copy of an already persisted
     * [io.github.xilinjia.krdb.types.RealmObject]. This is a deep copy that will copy all referenced
     * objects.
     *
     * @param obj managed object to copy from the Realm.
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
     * @returns an in-memory copy of the input object.
     * @throws IllegalArgumentException if [obj] is not a valid object to copy.
     */
    public fun <T : TypedRealmObject> copyFromRealm(obj: T, depth: UInt = UInt.MAX_VALUE): T

    /**
     * Makes an unmanaged in-memory copy of a collection of already persisted
     * [io.github.xilinjia.krdb.types.RealmObject]s. This is a deep copy that will copy all referenced
     * objects.
     *
     * @param collection the list of objects to copy. The collection itself does not need to be
     * managed by Realm, but can eg. be a normal unmanaged [List] or [Set]. The only requirement is
     * that all objects inside the collection are managed by Realm.
     *
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [RealmList] and [RealmSet] variables containing objects will be empty. Starting depth is 0.
     * @returns an in-memory copy of all input objects.
     * @throws IllegalArgumentException if the [collection] is not valid or contains objects that
     * are not valid to copy.
     */
    public fun <T : TypedRealmObject> copyFromRealm(
        collection: Iterable<T>,
        depth: UInt = UInt.MAX_VALUE
    ): List<T>

    /**
     * Makes an unmanaged in-memory copy of a [RealmDictionary] of already persisted
     * [io.github.xilinjia.krdb.types.RealmObject]s. This is a deep copy that will copy all referenced
     * objects.
     *
     * @param dictionary the dictionary of objects to copy. The dictionary itself does not need to
     * be managed by Realm, but can eg. be a dictionary produced by [realmDictionaryOf]. The only
     * requirement is that all objects inside the collection are managed by Realm.
     *
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [RealmDictionary] variables containing objects will be empty. Starting depth is 0.
     * @returns an in-memory copy of the dictionary with all key-value pairs with the input objects.
     * @throws IllegalArgumentException if the [dictionary] is not valid or contains objects that
     * are not valid to copy.
     */
    public fun <T : TypedRealmObject> copyFromRealm(
        dictionary: RealmDictionary<T?>,
        depth: UInt = UInt.MAX_VALUE
    ): Map<String, T?>
}
