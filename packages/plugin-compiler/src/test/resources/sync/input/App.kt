/*
 * Copyright 2023 Realm Inc.
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

import io.github.xilinjia.krdb.mongodb.App
import io.github.xilinjia.krdb.mongodb.AppConfiguration
import io.github.xilinjia.krdb.mongodb.sync.SyncConfiguration
import io.github.xilinjia.krdb.mongodb.User

val app1 = App.create("app1")
val app2 = AppConfiguration.create("app2")
val app3 = AppConfiguration.Builder("app3").build()

class A {
    val app4 = App.create("app4")
    val app5 = AppConfiguration.create("app5")
    val app6 = AppConfiguration.Builder("app6").build()
}
