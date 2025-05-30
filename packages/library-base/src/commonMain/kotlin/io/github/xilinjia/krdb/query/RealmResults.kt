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

package io.github.xilinjia.krdb.query

import io.github.xilinjia.krdb.Deleteable
import io.github.xilinjia.krdb.MutableRealm
import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.Versioned
import io.github.xilinjia.krdb.notifications.InitialResults
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.UpdatedResults
import io.github.xilinjia.krdb.types.BaseRealmObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * A _Realm Result_ holds the results of querying the Realm.
 *
 * @see Realm.query
 * @see MutableRealm.query
 */
public interface RealmResults<T : BaseRealmObject> : List<T>, Deleteable, Versioned {

    /**
     * Perform a query on the objects of this result using the Realm Query Language.
     *
     * See [these docs](https://docs.mongodb.com/realm-sdks/java/latest/io/realm/RealmQuery.html#rawPredicate-java.lang.String-java.lang.Object...-)
     * for a description of the equivalent realm-java API and
     * [these docs](https://docs.mongodb.com/realm-sdks/js/latest/tutorial-query-language.html)
     * for a more detailed description of the actual Realm Query Language.
     *
     * Ex.:
     *  `'color = "tan" AND name BEGINSWITH "B" SORT(name DESC) LIMIT(5)`
     *
     * @param query The query string to use for filtering and sort. If the empty string is used,
     * the original query used to create this [RealmResults] is returned.
     * @param args The query parameters.
     * @return new result according to the query and query arguments.
     *
     * @throws IllegalArgumentException on invalid queries.
     */
    public fun query(query: String = TRUE_PREDICATE, vararg args: Any?): RealmQuery<T>

    // TODO list subqueries would stop once the object gets deleted see https://github.com/realm/realm-kotlin/pull/1061
    /**
     * Observe changes to the RealmResult. Once subscribed the flow will emit a [InitialResults]
     * event and then a [UpdatedResults] on any change to the objects represented by the query backing
     * the RealmResults. The flow will continue running indefinitely except if the results are from
     * a backlinks property, then they will stop once the target object is deleted.
     *
     * The change calculations will on on the thread represented by
     * [Configuration.SharedBuilder.notificationDispatcher].
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @param keyPaths An optional list of model class properties that defines when a change to
     * objects inside the RealmResults will result in a change being emitted. Nested properties can
     * be defined using a dotted syntax, e.g. `parent.child.name`. Wildcards `*` can be be used
     * to capture all properties at a given level, e.g. `child.*` or `*.*`. If no keypaths are
     * provided, changes to all top-level properties and nested properties up to 4 levels down
     * will trigger a change.
     * @return a flow representing changes to the list.
     * @throws IllegalArgumentException if an invalid keypath is provided.
     * @return a flow representing changes to the RealmResults.
     */
    public fun asFlow(keyPaths: List<String>? = null): Flow<ResultsChange<T>>
}
