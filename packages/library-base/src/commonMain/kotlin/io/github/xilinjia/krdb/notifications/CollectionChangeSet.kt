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
package io.github.xilinjia.krdb.notifications

import io.github.xilinjia.krdb.query.RealmResults
import io.github.xilinjia.krdb.types.RealmDictionary

/**
 * This interface models the changes that can occur to a list.
 */
public interface ListChangeSet {
    /**
     * The deleted indices in the previous version of the collection. It will be set as a zero-sized
     * array if no objects were deleted.
     */
    public val deletions: IntArray

    /**
     * The inserted indices in the new version of the collection. It will be set as a zero-sized
     * array if no objects were inserted.
     */
    public val insertions: IntArray

    /**
     * The modified indices in the new version of the collection.
     * <p>
     * For [RealmResults], this means that one or more of the properties of the object at the given index were
     * modified (or an object linked to by that object was modified). It will be set as a zero-sized
     * array if no objects were changed.
     */
    public val changes: IntArray

    /**
     * The deleted ranges of objects in the previous version of the collection. It will be set as a zero-sized
     * array if no objects were deleted.
     */
    public val deletionRanges: Array<Range>

    /**
     * The inserted ranges of objects in the new version of the collection. It will be set as a zero-sized
     * array if no objects were inserted.
     */
    public val insertionRanges: Array<Range>

    /**
     * The modified ranges of objects in the new version of the collection. It will be set as a zero-sized
     * array if no objects were changed.
     */
    public val changeRanges: Array<Range>

    /**
     * Defines a range of elements in a list.
     */
    public data class Range(
        /**
         * The start index of this change range.
         */
        val startIndex: Int,
        /**
         * How many elements are inside this range.
         */
        val length: Int
    )
}

/**
 * This interface models the changes that can occur to a set.
 */
public interface SetChangeSet {
    /**
     * The number of entries that have been inserted in this version of the collection.
     */
    public val insertions: Int

    /**
     * The number of entries that have been deleted in this version of the collection.
     */
    public val deletions: Int
}

/**
 * This interface models the changes that can occur to a map.
 */
public interface MapChangeSet<K> {
    /**
     * The keys that have been deleted in this version of the map.
     */
    public val deletions: Array<K>

    /**
     * The keys that have been inserted in this version of the map.
     */
    public val insertions: Array<K>

    /**
     * The keys whose values have changed in this version of the map.
     */
    public val changes: Array<K>
}

/**
 * Convenience alias for [RealmDictionary] changesets. It represents a [MapChange] of `<String, V>`.
 */
public typealias DictionaryChangeSet = MapChangeSet<String>
