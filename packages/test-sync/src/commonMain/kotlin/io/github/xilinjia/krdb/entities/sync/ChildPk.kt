/*
 * Copyright 2021 Realm Inc.
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

package io.github.xilinjia.krdb.entities.sync

import io.github.xilinjia.krdb.ext.backlinks
import io.github.xilinjia.krdb.query.RealmResults
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import kotlin.random.Random

class ChildPk : RealmObject {
    @Suppress("VariableNaming")
    @PrimaryKey var _id: String = Random.nextLong().toString()
    var name: String = "DEFAULT"
    var age: Int = 0

    var link: ChildPk? = null
    val linkedFrom: RealmResults<ChildPk> by backlinks(ChildPk::link)
}
