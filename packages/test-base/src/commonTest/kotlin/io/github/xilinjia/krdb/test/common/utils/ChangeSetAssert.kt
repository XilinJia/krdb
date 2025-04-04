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

package io.github.xilinjia.krdb.test.common.utils

import io.github.xilinjia.krdb.notifications.ListChangeSet
import io.github.xilinjia.krdb.notifications.ListChangeSet.Range
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun assertContains(array: IntArray, element: Range) {
    for (value in element.startIndex until element.startIndex + element.length) {
        kotlin.test.assertContains(
            array,
            value,
            "Array [${array.joinToString(",")}] does not contain `$value`"
        )
    }
}

private fun assertContains(
    expectedRanges: Array<Range>,
    indices: IntArray,
    ranges: Array<Range>
) {
    if (expectedRanges.isEmpty()) {
        assertTrue(indices.isEmpty())
        assertTrue(ranges.isEmpty())
    } else {
        var elementCount = 0

        for (range in expectedRanges) {
            assertContains(indices, range)
            assertContains(ranges, range)

            elementCount += range.length
        }

        assertEquals(elementCount, indices.size)
    }
}

fun assertIsChangeSet(
    listChangeSet: ListChangeSet,
    insertRanges: Array<Range> = emptyArray(),
    deletionRanges: Array<Range> = emptyArray(),
    changesRanges: Array<Range> = emptyArray()
) {
    assertContains(
        insertRanges,
        listChangeSet.insertions,
        listChangeSet.insertionRanges
    )

    assertContains(
        deletionRanges,
        listChangeSet.deletions,
        listChangeSet.deletionRanges
    )

    assertContains(
        changesRanges,
        listChangeSet.changes,
        listChangeSet.changeRanges
    )
}
