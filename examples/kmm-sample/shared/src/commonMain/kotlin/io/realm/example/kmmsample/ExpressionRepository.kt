/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.example.kmmsample

import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.notifications.ResultsChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExpressionRepository {

    val realm: Realm by lazy {
        val configuration = RealmConfiguration.create(schema = setOf(Expression::class, AllTypes::class))
        Realm.open(configuration)
    }

    fun addExpression(expression: String): Expression = realm.writeBlocking {
        copyToRealm(Expression().apply { expressionString = expression })
    }

    fun expressions(): List<Expression> = realm.query<Expression>().find()

    fun observeChanges(): Flow<List<Expression>> =
        realm.query<Expression>().asFlow().map { resultsChange: ResultsChange<Expression> ->
            resultsChange.list
        }
}
