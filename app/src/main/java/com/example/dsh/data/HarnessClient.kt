package com.example.dsh.data

import com.example.dsh.data.model.ClientRequest
import com.example.dsh.data.model.ClientResponse
import com.example.dsh.data.model.RpcResult
import com.example.dsh.data.model.ServerRequest
import com.example.dsh.data.model.ServerResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64
import java.util.UUID

/**
 * Thin Kotlin port of the deepseek-harness web client transport:
 *  - unary RPCs over `POST /api/<method>` (Typert RPC envelope)
 *  - two downlink-only WebSockets for streamed events
 *
 * Auth is injected via an OkHttp interceptor (Basic auth from the Caddy reverse proxy).
 * The same interceptor runs on the WebSocket upgrade handshake, so `wss://` also authenticates.
 */
class HarnessClient(
    private val baseUrl: String,
    username: String,
    password: String,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val mediaType = "application/json".toMediaType()
    private val authHeader = "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    private val http = okhttp3.OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", authHeader).build())
        }
        .build()

    /** Unary RPC. Returns the decoded `result` (success or error). */
    suspend fun call(method: String, payload: JsonElement): RpcResult {
        val rpcId = UUID.randomUUID().toString()
        val body = json.encodeToString(
            com.example.dsh.data.model.ClientRequest.serializer(),
            com.example.dsh.data.model.ClientRequest(rpcId = rpcId, method = method, payload = payload),
        )
        val request = okhttp3.Request.Builder()
            .url("$baseUrl/api/$method")
            .post(body.toRequestBody(mediaType))
            .build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            throw HarnessTransportException("HTTP ${response.code} on $method")
        }
        val text = response.body!!.string()
        val sr = json.decodeFromString(com.example.dsh.data.model.ServerResponse.serializer(), text)
        if (sr.rpcId != rpcId) {
            throw HarnessTransportException("rpcId mismatch on $method (sent $rpcId, got ${sr.rpcId})")
        }
        return sr.result
    }

    /** Opens both event downlinks and emits every decoded frame. */
    fun openEventStreams(): Flow<ServerRequest> = callbackFlow {
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    trySend(json.decodeFromString(ServerRequest.serializer(), text))
                } catch (_: Exception) {
                    // drop malformed frame
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // One socket failing should not kill the whole stream; log only.
            }
        }
        val mux = http.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl("/api/events.mux")).build(),
            listener,
        )
        val host = http.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl("/api/events.host")).build(),
            listener,
        )
        awaitClose {
            mux.close(1000, null)
            host.close(1000, null)
        }
    }

    /**
     * Answer a server-requested approval. `frameRpcId` must be the rpcId of the
     * original `approval/requested` frame; `outcome` is "allowed-once" | "rejected".
     */
    suspend fun respondApproval(frameRpcId: String, sessionId: String, approvalId: String, outcome: String): RpcResult {
        val value = buildJsonObject {
            put("sessionId", sessionId)
            put("approvalId", approvalId)
            put("outcome", outcome)
        }
        val body = json.encodeToString(
            ClientResponse.serializer(),
            ClientResponse(rpcId = frameRpcId, result = RpcResult(ok = true, value = value)),
        )
        return postRaw("$baseUrl/api/respond", body)
    }

    /**
     * Answer a server-requested question. `frameRpcId` must be the rpcId of the
     * original `question/requested` frame. Each answer selects option labels
     * (harness schema expects `selected: string[]`; we send the chosen option labels).
     */
    suspend fun respondQuestion(frameRpcId: String, sessionId: String, answers: List<QuestionAnswer>): RpcResult {
        val value = buildJsonObject {
            put("sessionId", sessionId)
            putJsonObject("answer") {
                putJsonArray("answers") {
                    answers.forEach { a ->
                        add(buildJsonObject {
                            put("id", a.id)
                            putJsonArray("selected") { a.selected.forEach { add(it) } }
                            a.custom?.let { put("custom", it) }
                        })
                    }
                }
            }
        }
        val body = json.encodeToString(
            ClientResponse.serializer(),
            ClientResponse(rpcId = frameRpcId, result = RpcResult(ok = true, value = value)),
        )
        return postRaw("$baseUrl/api/respond", body)
    }

    /** POST raw JSON to an arbitrary url; tolerant parse of the receipt. */
    private suspend fun postRaw(url: String, body: String): RpcResult {
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body.toRequestBody(mediaType))
            .build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            throw HarnessTransportException("HTTP ${response.code} on $url")
        }
        val text = response.body!!.string()
        return try {
            json.decodeFromString(ServerResponse.serializer(), text).result
        } catch (_: Exception) {
            RpcResult(ok = true) // receipt may not be a server-response envelope
        }
    }

    private fun wsUrl(path: String): String {
        val u = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        return u + path
    }
}

class HarnessTransportException(message: String) : Exception(message)

/** One answered question: the question item id plus the chosen option labels (and optional free text). */
data class QuestionAnswer(
    val id: String,
    val selected: List<String>,
    val custom: String? = null,
)
