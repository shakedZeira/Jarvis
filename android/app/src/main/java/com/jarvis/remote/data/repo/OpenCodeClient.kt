package com.jarvis.remote.data.repo

import com.jarvis.remote.data.model.Health
import com.jarvis.remote.data.model.JarvisJson
import com.jarvis.remote.data.model.Message
import com.jarvis.remote.data.model.Project
import com.jarvis.remote.data.model.Session
import com.jarvis.remote.data.model.SessionStatus
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA: MediaType = "application/json; charset=utf-8".toMediaType()

class ApiException(val code: Int, message: String, val detail: String? = null) : Exception(message)

class OpenCodeClient(
    val baseUrl: String,
    val username: String,
    val password: String,
    private val client: OkHttpClient
) {
    private val base: String = baseUrl.trimEnd('/')

    private val okHttp: OkHttpClient = client.newBuilder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", basicAuthHeader(username, password))
                .build()
            chain.proceed(request)
        })
        .build()

    suspend fun health(): Health = getJson("/global/health")

    suspend fun projects(): List<Project> = getJson("/project")

    suspend fun sessions(directory: String? = null, limit: Int = 500): List<Session> {
        if (directory == null) return getJson("/session")
        val query = buildString {
            append("directory=")
            append(URLEncoder.encode(directory, "UTF-8"))
            append("&limit=$limit")
        }
        return getJson("/session", query = query)
    }

    suspend fun sessionStatus(): Map<String, SessionStatus> = withContext(Dispatchers.IO) {
        val body = execute(Request.Builder().url("$base/session/status").get().build())
        val root = JarvisJson.parseToJsonElement(body) as? JsonObject ?: JsonObject(emptyMap())
        root.mapValues { (_, value) ->
            SessionStatus(value as? JsonObject ?: JsonObject(emptyMap()))
        }
    }

    suspend fun messages(sessionID: String, limit: Int = 100): List<Message> =
        getJson("/session/$sessionID/message", query = "limit=$limit")

    suspend fun sendPrompt(sessionID: String, text: String): Unit = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("parts", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        }.toString()
        val request = Request.Builder()
            .url("$base/session/$sessionID/prompt_async")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        execute(request)
    }

    suspend fun abort(sessionID: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/session/$sessionID/abort")
            .post("".toRequestBody(JSON_MEDIA))
            .build()
        execute(request)
    }

    suspend fun respondPermission(sessionID: String, permissionID: String, allowed: Boolean): Unit =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject { put("value", allowed) }.toString()
            val request = Request.Builder()
                .url("$base/session/$sessionID/permissions/$permissionID")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            execute(request)
        }

    private suspend inline fun <reified T> getJson(path: String, query: String? = null): T =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(base)
                append(path)
                if (query != null) {
                    append('?')
                    append(query)
                }
            }
            val body = execute(Request.Builder().url(url).get().build())
            JarvisJson.decodeFromString<T>(body)
        }

    private fun execute(request: Request): String {
        okHttp.newCall(request).execute().use { response ->
            val code = response.code
            if (code !in 200..299) {
                val message = response.body?.string().orEmpty()
                throw ApiException(code, "HTTP $code: ${message.take(300)}", parseErrorDetail(message))
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseErrorDetail(body: String): String? = runCatching {
        val root = JarvisJson.parseToJsonElement(body) as? JsonObject ?: return null
        val data = root["data"] as? JsonObject ?: return null
        (data["message"] as? JsonPrimitive)?.content
    }.getOrNull()

    companion object {
        fun build(baseUrl: String, username: String, password: String): OpenCodeClient =
            OpenCodeClient(baseUrl.trimEnd('/'), username, password, OkHttpClient.Builder().build())
    }
}