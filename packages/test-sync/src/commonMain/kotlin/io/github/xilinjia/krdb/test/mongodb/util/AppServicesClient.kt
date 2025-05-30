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

package io.github.xilinjia.krdb.test.mongodb.util

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.github.xilinjia.krdb.internal.interop.PropertyType
import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.internal.schema.RealmClassImpl
import io.github.xilinjia.krdb.mongodb.sync.SyncMode
import io.github.xilinjia.krdb.schema.RealmClassKind
import io.github.xilinjia.krdb.test.mongodb.SyncServerConfig
import io.github.xilinjia.krdb.test.mongodb.TEST_APP_CLUSTER_NAME
import io.github.xilinjia.krdb.types.BaseRealmObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ADMIN_PATH = "/api/admin/v3.0"
private const val PRIVATE_PATH = "/api/private/v1.0"

data class SyncPermissions(
    val read: Boolean,
    val write: Boolean
)

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    classDiscriminatorMode = ClassDiscriminatorMode.NONE
    encodeDefaults = true
}

@Serializable
data class Schema(
    val metadata: SchemaMetadata = SchemaMetadata(
        database = "database",
        collection = "title"
    ),
    val schema: SchemaData,
    val relationships: Map<String, SchemaRelationship> = emptyMap(),
) {
    constructor(
        database: String,
        schema: SchemaData,
        relationships: Map<String, SchemaRelationship>,
    ) : this(
        metadata = SchemaMetadata(
            database = database,
            collection = schema.title
        ),
        schema = schema,
        relationships = relationships
    )
}

@Serializable
data class SchemaMetadata(
    var database: String = "",
    @SerialName("data_source")
    var dataSource: String = "BackingDB",
    var collection: String = "SyncDog",
)

@Serializable
data class SchemaRelationship(
    @SerialName("source_key")
    val sourceKey: String,
    @SerialName("foreign_key")
    val foreignKey: String,
    @SerialName("is_list")
    val isList: Boolean,
    val ref: String = "",
) {
    constructor(
        target: String,
        database: String,
        sourceKey: String,
        foreignKey: String,
        isList: Boolean,
    ) : this(
        sourceKey = sourceKey,
        foreignKey = foreignKey,
        isList = isList,
        ref = "#/relationship/BackingDB/$database/$target"
    )
}

@Serializable
sealed interface SchemaPropertyType {
    @Transient val isRequired: Boolean
}

@Serializable
class ObjectReferenceType(
    @Transient val sourceKey: String = "",
    @Transient val targetKey: String = "",
    @Transient val target: String = "",
    @Transient val isList: Boolean = false,
    val bsonType: PrimitivePropertyType.Type,
) : SchemaPropertyType {
    constructor(sourceKey: String, targetSchema: RealmClassImpl, isCollection: Boolean) : this(
        sourceKey = sourceKey,
        targetKey = targetSchema.cinteropClass.primaryKey,
        target = targetSchema.name,
        bsonType = targetSchema.cinteropProperties
            .first { it.name == targetSchema.cinteropClass.primaryKey }
            .type
            .toSchemaType(),
        isList = isCollection
    )

    @Transient
    override val isRequired: Boolean = false
}

@Serializable
data class SchemaData(
    var title: String = "",
    var properties: Map<String, SchemaPropertyType> = mutableMapOf(),
    val required: List<String> = mutableListOf(),
    @Transient val kind: RealmClassKind = RealmClassKind.STANDARD,
    val type: PrimitivePropertyType.Type = PrimitivePropertyType.Type.OBJECT,
) : SchemaPropertyType {
    @Transient
    override val isRequired: Boolean = false
}

@Serializable
data class CollectionPropertyType(
    val items: SchemaPropertyType,
    val uniqueItems: Boolean = false,
) : SchemaPropertyType {
    val bsonType = PrimitivePropertyType.Type.ARRAY
    @Transient
    override val isRequired: Boolean = false
}

@Serializable
data class MapPropertyType(
    val additionalProperties: SchemaPropertyType,
) : SchemaPropertyType {
    val bsonType = PrimitivePropertyType.Type.OBJECT
    @Transient
    override val isRequired: Boolean = false
}

@Serializable
open class PrimitivePropertyType(
    val bsonType: Type,
    @Transient override val isRequired: Boolean = false,
) : SchemaPropertyType {

    enum class Type {
        @SerialName("string")
        STRING,

        @SerialName("object")
        OBJECT,

        @SerialName("array")
        ARRAY,

        @SerialName("objectId")
        OBJECT_ID,

        @SerialName("boolean")
        BOOLEAN,

        @SerialName("bool")
        BOOL,

        @SerialName("null")
        NULL,

        @SerialName("regex")
        REGEX,

        @SerialName("date")
        DATE,

        @SerialName("timestamp")
        TIMESTAMP,

        @SerialName("int")
        INT,

        @SerialName("long")
        LONG,

        @SerialName("decimal")
        DECIMAL,

        @SerialName("double")
        DOUBLE,

        @SerialName("number")
        NUMBER,

        @SerialName("binData")
        BIN_DATA,

        @SerialName("uuid")
        UUID,

        @SerialName("mixed")
        MIXED,

        @SerialName("float")
        FLOAT;
    }
}

fun PropertyType.toSchemaType() =
    when (this) {
        PropertyType.RLM_PROPERTY_TYPE_BOOL -> PrimitivePropertyType.Type.BOOL
        PropertyType.RLM_PROPERTY_TYPE_INT -> PrimitivePropertyType.Type.INT
        PropertyType.RLM_PROPERTY_TYPE_STRING -> PrimitivePropertyType.Type.STRING
        PropertyType.RLM_PROPERTY_TYPE_BINARY -> PrimitivePropertyType.Type.BIN_DATA
        PropertyType.RLM_PROPERTY_TYPE_OBJECT -> PrimitivePropertyType.Type.OBJECT
        PropertyType.RLM_PROPERTY_TYPE_FLOAT -> PrimitivePropertyType.Type.FLOAT
        PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> PrimitivePropertyType.Type.DOUBLE
        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128 -> PrimitivePropertyType.Type.DECIMAL
        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> PrimitivePropertyType.Type.DATE
        PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID -> PrimitivePropertyType.Type.OBJECT_ID
        PropertyType.RLM_PROPERTY_TYPE_UUID -> PrimitivePropertyType.Type.UUID
        PropertyType.RLM_PROPERTY_TYPE_MIXED -> PrimitivePropertyType.Type.MIXED
        else -> throw IllegalArgumentException("Unsupported type")
    }

@Serializable
data class LoginResponse(val access_token: String)

@Serializable
data class Profile(val roles: List<Role>)

@Serializable
data class Role(val role_name: String, val group_id: String? = null)

@Serializable
data class AuthProvider constructor(
    val _id: String,
    val type: String,
    val disabled: Boolean = false,
    @Transient val app: BaasApp? = null
)

@Serializable
data class Service(
    val _id: String,
    val name: String,
    val type: String,
    @Transient val app: BaasApp? = null
)

@Serializable
data class Function(
    val _id: String? = null,
    val name: String,
    val source: String? = null,
    @SerialName("run_as_system") val runAsSystem: Boolean? = null,
    val private: Boolean? = null,
    @SerialName("can_evaluate") val canEvaluate: JsonObject? = null
)

@Serializable
data class BaasApp(
    val _id: String,
    @SerialName("client_app_id") val clientAppId: String,
    val name: String,
    @SerialName("domain_id") val domainId: String,
    @SerialName("group_id") val groupId: String,

    @Transient private val _client: AppServicesClient? = null
) {
    val client: AppServicesClient
        get() = _client ?: TODO("App should be copy'ed with _client set to the AppServicesClient that retrieved it")
    val url: String
        get() = client.baseUrl + ADMIN_PATH + "/groups/${this.groupId}/apps/${this._id}"
    val privateUrl: String
        get() = client.baseUrl + PRIVATE_PATH + "/groups/${this.groupId}/apps/${this._id}"
}

/**
 * Client to interact with App Services Server. It allows to create Applications and tweak their
 * configurations.
 */
class AppServicesClient(
    val baseUrl: String,
    private val groupId: String,
    internal val httpClient: HttpClient,
    val dispatcher: CoroutineDispatcher,
) {

    val groupUrl: String
        get() = baseUrl + ADMIN_PATH + "/groups/$groupId"

    fun closeClient() {
        httpClient.close()
    }

    suspend fun getOrCreateApp(appInitializer: AppInitializer): BaasApp {
        val app = getApp(appInitializer.name)
        return app ?: createApp(appInitializer.name) {
            appInitializer.initialize(this@AppServicesClient, this)
        }
    }

    private suspend fun getApp(appName: String): BaasApp? {
        val withContext = withContext(dispatcher) {
            httpClient.typedListRequest<BaasApp>(Get, "$groupUrl/apps")
                .firstOrNull {
                    it.name == appName
                }?.copy(_client = this@AppServicesClient)
        }
        return withContext
    }

    private suspend fun createApp(
        appName: String,
        initializer: suspend BaasApp.() -> Unit
    ): BaasApp {
        if (appName.length > 32) {
            throw IllegalArgumentException("App names are restricted to 32 characters: $appName was ${appName.length}")
        }
        return withContext(dispatcher) {
            httpClient.typedRequest<BaasApp>(Post, "$groupUrl/apps") {
                setBody(Json.parseToJsonElement("""{"name": $appName}"""))
                contentType(ContentType.Application.Json)
            }.copy(_client = this@AppServicesClient).apply {
                initializer(this)
            }
        }
    }

    suspend fun BaasApp.setSchema(
        schema: Set<KClass<out BaseRealmObject>>,
        extraProperties: Map<String, PrimitivePropertyType.Type> = emptyMap()
    ) {
        val schemas = SchemaProcessor.process(
            databaseName = clientAppId,
            classes = schema,
            extraProperties = extraProperties
        )

        // First we create the schemas without the relationships
        val ids: Map<String, String> = schemas.entries
            .associate { (name, schema: Schema) ->
                name to addSchema(schema = schema.copy(relationships = emptyMap()))
            }

        // then we update the schema to add the relationships
        schemas.forEach { (name, schema) ->
            updateSchema(
                id = ids[name]!!,
                schema = schema
            )
        }
    }

    suspend fun BaasApp.updateSchema(
        id: String,
        schema: Schema,
    ): HttpResponse =
        withContext(dispatcher) {
            httpClient.request(
                "$url/schemas/$id"
            ) {
                this.method = HttpMethod.Put
                setBody(json.encodeToJsonElement(schema))
                contentType(ContentType.Application.Json)
            }
        }

    val BaasApp.url: String
        get() = "$groupUrl/apps/${this._id}"
    suspend fun BaasApp.toggleFeatures(features: Set<String>, enable: Boolean) {
        withContext(dispatcher) {
            httpClient.typedRequest<Unit>(
                Post,
                "$privateUrl/features"
            ) {
                setBody(Json.parseToJsonElement("""{ "action": "${if (enable) "enable" else "disable"}", "feature_flags": [ ${features.joinToString { "\"$it\"" }} ] }"""))
                contentType(ContentType.Application.Json)
            }
        }
    }

    suspend fun BaasApp.addFunction(function: Function): Function =
        withContext(dispatcher) {
            httpClient.typedRequest<Function>(
                Post,
                "$url/functions"
            ) {
                setBody(function)
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.addSchema(schema: Schema): String =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/schemas"
            ) {
                setBody(json.encodeToJsonElement(schema))
                contentType(ContentType.Application.Json)
            }
        }.let { jsonObject: JsonObject ->
            jsonObject["_id"]!!.jsonPrimitive.content
        }

    suspend fun BaasApp.addService(service: String): Service =
        withContext(dispatcher) {
            httpClient.typedRequest<Service>(
                Post,
                "$url/services"
            ) {
                setBody(Json.parseToJsonElement(service))
                contentType(ContentType.Application.Json)
            }.run {
                copy(app = this@addService)
            }
        }

    suspend fun BaasApp.addAuthProvider(authProvider: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/auth_providers"
            ) {
                setBody(Json.parseToJsonElement(authProvider))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.getAuthProvider(type: String): AuthProvider =
        withContext(dispatcher) {
            httpClient.typedListRequest<AuthProvider>(
                Get,
                "$url/auth_providers"
            )
        }.first {
            it.type == type
        }.run {
            copy(app = this@getAuthProvider)
        }

    suspend fun BaasApp.setDevelopmentMode(developmentModeEnabled: Boolean) =
        withContext(dispatcher) {
            httpClient.request("$url/sync/config") {
                this.method = HttpMethod.Put
                setBody(Json.parseToJsonElement("""{"development_mode_enabled": $developmentModeEnabled}"""))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.setCustomUserData(userDataConfig: String): HttpResponse =
        withContext(dispatcher) {
            httpClient.request("$url/custom_user_data") {
                this.method = HttpMethod.Patch
                setBody(Json.parseToJsonElement(userDataConfig))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.addEndpoint(endpoint: String) =
        withContext(dispatcher) {
            httpClient.request("$url/endpoints") {
                this.method = HttpMethod.Post
                setBody(Json.parseToJsonElement(endpoint))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.addSecret(secret: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/secrets"
            ) {
                setBody(Json.parseToJsonElement(secret))
                contentType(ContentType.Application.Json)
            }
        }

    val AuthProvider.url: String
        get() = "${app!!.url}/auth_providers/$_id"

    suspend fun AuthProvider.enable(enabled: Boolean) =
        withContext(dispatcher) {
            httpClient.request("$url/${if (enabled) "enable" else "disable"}") {
                this.method = HttpMethod.Put
            }
        }

    suspend fun AuthProvider.updateConfig(block: MutableMap<String, JsonElement>.() -> Unit) {
        mutableMapOf<String, JsonElement>().apply {
            block()
            httpClient.request(url) {
                this.method = HttpMethod.Patch
                setBody(JsonObject(mapOf("config" to JsonObject(this@apply))))
                contentType(ContentType.Application.Json)
            }
        }
    }

    val Service.url: String
        get() = "${app!!.url}/services/$_id"

    suspend fun Service.setSyncConfig(config: String) =
        withContext(dispatcher) {
            httpClient.request("$url/config") {
                this.method = HttpMethod.Patch
                setBody(Json.parseToJsonElement(config))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun Service.addDefaultRule(rule: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/default_rule"
            ) {
                setBody(Json.parseToJsonElement(rule))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun Service.addRule(rule: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/rules"
            ) {
                setBody(Json.parseToJsonElement(rule))
                contentType(ContentType.Application.Json)
            }
        }

    /**
     * Deletes all currently registered and pending users on the App Services Application.
     */
    suspend fun BaasApp.deleteAllUsers() = withContext(dispatcher) {
        deleteAllRegisteredUsers()
        deleteAllPendingUsers()
    }

    private suspend fun BaasApp.deleteAllPendingUsers() {
        val pendingUsers = httpClient.typedRequest<JsonArray>(
            Get,
            "$url/user_registrations/pending_users"
        )
        for (pendingUser in pendingUsers) {
            val loginTypes = pendingUser.jsonObject["login_ids"]!!.jsonArray
            loginTypes
                .filter { it.jsonObject["id_type"]!!.jsonPrimitive.content == "email" }
                .map {
                    httpClient.delete(
                        "$url/user_registrations/by_email/${it.jsonObject["id"]!!.jsonPrimitive.content}"
                    )
                }
        }
    }

    private suspend fun BaasApp.deleteAllRegisteredUsers() {
        val users = httpClient.typedRequest<JsonArray>(
            Get,
            "$url/users"
        )
        users.map {
            httpClient.delete("$url/users/${it.jsonObject["_id"]!!.jsonPrimitive.content}")
        }
    }

    val BaasApp.mongodbService: Service
        get() {
            return runBlocking {
                httpClient.typedListRequest<Service>(Get, "$url/services")
                    .first {
                        val type = if (TEST_APP_CLUSTER_NAME.isEmpty()) "mongodb" else "mongodb-atlas"
                        it.type == type
                    }.copy(app = this@mongodbService)
            }
        }

    private suspend fun BaasApp.controlSync(
        serviceId: String,
        enabled: Boolean,
        permissions: SyncPermissions? = null
    ) {
        val url = "$url/services/$serviceId/config"
        val syncEnabled = if (enabled) "enabled" else "disabled"
        val jsonPartition = permissions?.let {
            val permissionList = JsonObject(
                mapOf(
                    "read" to JsonPrimitive(permissions.read),
                    "write" to JsonPrimitive(permissions.read)
                )
            )
            JsonObject(mapOf("permissions" to permissionList, "key" to JsonPrimitive("realm_id")))
        }

        // Add permissions if present, otherwise just change state
        val content = jsonPartition?.let {
            mapOf(
                "state" to JsonPrimitive(syncEnabled),
                "partition" to jsonPartition
            )
        } ?: mapOf("state" to JsonPrimitive(syncEnabled))

        val configObj = JsonObject(mapOf("sync" to JsonObject(content)))
        httpClient.request(url) {
            this.method = HttpMethod.Patch
            setBody(configObj)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun BaasApp.pauseSync() =
        withContext(dispatcher) {
            val backingDbServiceId = mongodbService._id
            controlSync(backingDbServiceId, false)
        }

    suspend fun BaasApp.startSync() =
        withContext(dispatcher) {
            val backingDbServiceId = mongodbService._id
            controlSync(backingDbServiceId, true)
        }

    suspend fun BaasApp.triggerClientReset(userId: String) =
        withContext(dispatcher) {
            deleteDocument("__realm_sync", "clientfiles", """{"ownerId": "$userId"}""")
            deleteDocument("__realm_sync_$_id", "clientfiles", """{"ownerId": "$userId"}""")
        }

    suspend fun BaasApp.triggerClientReset(syncMode: SyncMode, userId: String) =
        withContext(dispatcher) {
            when (syncMode) {
                SyncMode.PARTITION_BASED ->
                    deleteDocument("__realm_sync", "clientfiles", """{"ownerId": "$userId"}""")
                SyncMode.FLEXIBLE ->
                    deleteDocument("__realm_sync_$_id", "clientfiles", """{"ownerId": "$userId"}""")
            }
        }

    suspend fun BaasApp.changeSyncPermissions(permissions: SyncPermissions, block: () -> Unit) =
        withContext(dispatcher) {
            val backingDbServiceId = mongodbService._id

            // Execute test logic
            try {
                controlSync(backingDbServiceId, true, permissions)
                block.invoke()
            } finally {
                // Restore original permissions
                controlSync(backingDbServiceId, true, SyncPermissions(read = true, write = true))
            }
        }

    suspend fun BaasApp.getAuthConfigData(): String =
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/auth_providers/$providerId"
            httpClient.typedRequest<JsonObject>(Get, url).toString()
        }

    suspend fun BaasApp.setAutomaticConfirmation(enabled: Boolean) =
        withContext(dispatcher) {
            getAuthProvider("local-userpass").updateConfig {
                put("autoConfirm", JsonPrimitive(enabled))
            }
        }

    suspend fun BaasApp.setCustomConfirmation(enabled: Boolean) =
        withContext(dispatcher) {
            getAuthProvider("local-userpass").updateConfig {
                put("runConfirmationFunction", JsonPrimitive(enabled))
            }
        }

    suspend fun BaasApp.setResetFunction(enabled: Boolean) =
        withContext(dispatcher) {
            getAuthProvider("local-userpass").updateConfig {
                put("runResetFunction", JsonPrimitive(enabled))
            }
        }

    suspend fun BaasApp.insertDocument(clazz: String, json: String): JsonObject? =
        withContext(dispatcher) {
            functionCall(
                name = "insertDocument",
                arguments = buildJsonArray {
                    add(mongodbService.name)
                    add(clientAppId)
                    add(clazz)
                    add(Json.decodeFromString<JsonObject>(json))
                }
            )
        }

    suspend fun BaasApp.queryDocument(clazz: String, query: String): JsonObject? =
        withContext(dispatcher) {
            functionCall(
                name = "queryDocument",
                arguments = buildJsonArray {
                    add(mongodbService.name)
                    add(clientAppId)
                    add(clazz)
                    add(query)
                }
            )
        }

    suspend fun BaasApp.countDocuments(clazz: String): Int {
        val result: JsonObject? = withContext(dispatcher) {
            functionCall(
                name = "countDocuments",
                arguments = buildJsonArray {
                    add(mongodbService.name)
                    add(clientAppId)
                    add(clazz)
                }
            )
        }
        return result?.let {
            it["value"]?.jsonObject?.get("\$numberLong")?.jsonPrimitive?.int
        } ?: throw IllegalStateException("Unexpected result: $result")
    }

    suspend fun BaasApp.deleteDocument(
        db: String,
        clazz: String,
        query: String
    ): JsonObject? =
        withContext(dispatcher) {
            functionCall(
                name = "deleteDocument",
                arguments = buildJsonArray {
                    add(mongodbService.name)
                    add(db)
                    add(clazz)
                    add(query)
                }
            )
        }

    suspend fun BaasApp.waitUntilInitialSyncCompletes() {
        withTimeout(5.minutes) {
            while (!initialSyncComplete()) {
                delay(1.seconds)
            }
        }
    }

    suspend fun BaasApp.initialSyncComplete(): Boolean {
        return withContext(dispatcher) {
            try {
                httpClient.typedRequest<JsonObject>(
                    Get,
                    "$url/sync/progress"
                ).let { obj: JsonObject ->
                    obj["accepting_clients"]?.jsonPrimitive?.boolean ?: false
                }
            } catch (ex: IllegalStateException) {
                if (ex.message!!.contains("there are no mongodb/atlas services with provided sync state")) {
                    // If the network returns this error, it means that Sync is not enabled for this app,
                    // in that case, just report success.
                    true
                } else {
                    throw ex
                }
            }
        }
    }

    private suspend fun BaasApp.getLocalUserPassProviderId(): String =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonArray>(
                Get,
                "$url/auth_providers"
            ).let { arr: JsonArray ->
                arr.firstOrNull { el: JsonElement ->
                    el.jsonObject["name"]!!.jsonPrimitive.content == "local-userpass"
                }?.let { el: JsonElement ->
                    el.jsonObject["_id"]?.jsonPrimitive?.content ?: throw IllegalStateException(
                        "Could not find '_id': $arr"
                    )
                } ?: throw IllegalStateException("Could not find local-userpass provider: $arr")
            }
        }

    private suspend fun BaasApp.functionCall(
        name: String,
        arguments: JsonArray
    ): JsonObject? =
        withContext(dispatcher) {
            val functionCall = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            }

            val url =
                "$url/debug/execute_function?run_as_system=true"
            httpClient.typedRequest<JsonObject>(Post, url) {
                setBody(functionCall)
                contentType(ContentType.Application.Json)
            }.jsonObject["result"]!!.let {
                when (it) {
                    is JsonNull -> null
                    else -> it.jsonObject
                }
            }
        }
    companion object {
        suspend fun build(
            debug: Boolean,
            baseUrl: String,
            dispatcher: CoroutineDispatcher,
        ): AppServicesClient {
            val adminUrl = baseUrl + ADMIN_PATH
            // Work around issues on Native with the Ktor client being created and used
            // on different threads.
            // Log in using unauthorized client
            val unauthorizedClient = defaultClient("realm-baas-unauthorized", debug)

            var loginMethod: String = "local-userpass"
            var json: Map<String, String> = mapOf("username" to SyncServerConfig.email, "password" to SyncServerConfig.password)
            if (SyncServerConfig.publicApiKey.isNotEmpty()) {
                loginMethod = "mongodb-cloud"
                json = mapOf("username" to SyncServerConfig.publicApiKey, "apiKey" to SyncServerConfig.privateApiKey)
            }
            val loginResponse = unauthorizedClient.typedRequest<LoginResponse>(
                HttpMethod.Post,
                "$adminUrl/auth/providers/$loginMethod/login"
            ) {
                contentType(ContentType.Application.Json)
                setBody(json)
            }

            // Setup authorized client for the rest of the requests
            val accessToken = loginResponse.access_token
            unauthorizedClient.close()

            val httpClient = defaultClient("realm-baas-authorized", debug) {
                expectSuccess = true

                defaultRequest {
                    headers {
                        append("Authorization", "Bearer $accessToken")
                    }
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            prettyPrint = true
                            isLenient = true
                        }
                    )
                }
                install(Logging) {
                    // Set to LogLevel.ALL to debug Admin API requests. All relevant
                    // data for each request/response will be console or LogCat.
                    level = LogLevel.ALL
                }
            }

            // Collect app group id
            val groupId = httpClient.typedRequest<Profile>(Get, "$adminUrl/auth/profile")
                .roles
                .first()
                .group_id ?: "null"

            return AppServicesClient(
                baseUrl,
                groupId,
                httpClient,
                dispatcher
            )
        }
    }
}

// Default serializer fails with
// InvalidMutabilityException: mutation attempt of frozen kotlin.collections.HashMap
// on native. Have tried the various workarounds from
// https://github.com/Kotlin/kotlinx.serialization/issues/1450
// but only one that works is manual invoking the deserializer
@OptIn(InternalSerializationApi::class)
private suspend inline fun <reified T : Any> HttpClient.typedRequest(
    method: HttpMethod,
    url: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): T {
    val response: HttpResponse = this@typedRequest.request(url) {
        this.method = method
        this.apply(block)
    }
    if (!response.status.isSuccess()) {
        throw IllegalStateException("Http request failed: $url. ${response.status}: ${response.bodyAsText()}")
    }
    return response.bodyAsText()
        .let {
            Json { ignoreUnknownKeys = true }.decodeFromString(
                T::class.serializer(),
                it
            )
        }
}

// Default serializer fails with
// InvalidMutabilityException: mutation attempt of frozen kotlin.collections.HashMap
// on native. Have tried the various workarounds from
// https://github.com/Kotlin/kotlinx.serialization/issues/1450
// but only one that works is manual invoking the deserializer
@OptIn(InternalSerializationApi::class)
private suspend inline fun <reified T : Any> HttpClient.typedListRequest(
    method: HttpMethod,
    url: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): List<T> {
    return this@typedListRequest.request(url) {
        this.method = method
        this.apply(block)
    }.bodyAsText()
        .let {
            Json { ignoreUnknownKeys = true }.decodeFromString(
                ListSerializer(T::class.serializer()),
                it
            )
        }
}
