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

package io.github.xilinjia.krdb.internal

import io.github.xilinjia.krdb.internal.interop.ArrayAccessor
import io.github.xilinjia.krdb.internal.interop.CollectionChangeSetBuilder
import io.github.xilinjia.krdb.internal.interop.MapChangeSetBuilder
import io.github.xilinjia.krdb.internal.interop.RealmChangesPointer
import io.github.xilinjia.krdb.internal.interop.RealmInterop
import io.github.xilinjia.krdb.notifications.DictionaryChangeSet
import io.github.xilinjia.krdb.notifications.ListChangeSet
import io.github.xilinjia.krdb.notifications.ListChangeSet.Range
import io.github.xilinjia.krdb.notifications.SetChangeSet

// --------------------------------------------------------
// Collections: List and Set
// --------------------------------------------------------

internal abstract class CollectionChangeSetBuilderImpl<T>(
    change: RealmChangesPointer
) : CollectionChangeSetBuilder<T, Range>() {

    init {
        RealmInterop.realm_collection_changes_get_indices(change, this)
        RealmInterop.realm_collection_changes_get_ranges(change, this)
    }

    override fun initIndicesArray(size: Int, indicesAccessor: ArrayAccessor): IntArray =
        IntArray(size) { index -> indicesAccessor(index) }

    override fun initRangesArray(
        size: Int,
        fromAccessor: ArrayAccessor,
        toAccessor: ArrayAccessor
    ): Array<Range> =
        Array(size) { index ->
            val from: Int = fromAccessor(index)
            val to: Int = toAccessor(index)
            Range(from, to - from)
        }
}

internal class ListChangeSetBuilderImpl(
    change: RealmChangesPointer
) : CollectionChangeSetBuilderImpl<ListChangeSet>(change) {

    override fun build(): ListChangeSet = object : ListChangeSet {
        override val deletions: IntArray =
            this@ListChangeSetBuilderImpl.deletionIndices

        override val insertions: IntArray =
            this@ListChangeSetBuilderImpl.insertionIndices

        override val changes: IntArray =
            this@ListChangeSetBuilderImpl.modificationIndicesAfter

        override val deletionRanges: Array<Range> =
            this@ListChangeSetBuilderImpl.deletionRanges

        override val insertionRanges: Array<Range> =
            this@ListChangeSetBuilderImpl.insertionRanges

        override val changeRanges: Array<Range> =
            this@ListChangeSetBuilderImpl.modificationRangesAfter
    }
}

internal class SetChangeSetBuilderImpl(
    change: RealmChangesPointer
) : CollectionChangeSetBuilderImpl<SetChangeSet>(change) {

    override fun build(): SetChangeSet = object : SetChangeSet {
        override val insertions: Int
            get() = this@SetChangeSetBuilderImpl.insertionIndices.size
        override val deletions: Int
            get() = this@SetChangeSetBuilderImpl.deletionIndices.size
    }
}

// --------------------------------------------------------
// Dictionary: uses a different changeset internally
// --------------------------------------------------------

internal class DictionaryChangeSetBuilderImpl(
    change: RealmChangesPointer
) : MapChangeSetBuilder<DictionaryChangeSet, String>() {

    init {
        RealmInterop.realm_dictionary_get_changes(change, this)
    }

    override fun initDeletions(keys: Array<String>) {
        this.deletedKeys = keys
    }

    override fun initInsertions(keys: Array<String>) {
        this.insertedKeys = keys
    }

    override fun initModifications(keys: Array<String>) {
        this.modifiedKeys = keys
    }

    override fun build(): DictionaryChangeSet {
        return object : DictionaryChangeSet {
            override val deletions: Array<String>
                get() = this@DictionaryChangeSetBuilderImpl.deletedKeys
            override val insertions: Array<String>
                get() = this@DictionaryChangeSetBuilderImpl.insertedKeys
            override val changes: Array<String>
                get() = this@DictionaryChangeSetBuilderImpl.modifiedKeys
        }
    }
}
