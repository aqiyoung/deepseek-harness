package ai.deepseek.harness.dsh

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Native DSH client speaking the current harness protocol (verified live against dsh.threel.site):
 * - Uplink: HTTP `POST /api/<method>` with a `client-request` envelope; `POST /api/respond` for
 *   server→client requests (approvals / questions).
 * - Downlink: two independent **WebSocket** streams, `wss://.../api/events.mux` and
 *   `wss://.../api/events.host`, each delivering `server-request` JSON frames whose `payload` is a
 *   typed frame (MuxFrame / HostFrame).
 *
 * Authentication is the `dsh_session` cookie, sent on every HTTP request and WS upgrade.
 *
 * The DSH instance is self-hosted behind a certificate chain that some Android clients reject
 * (the WebView needed an SSL bypass), so this client trusts all certs for this host. Acceptable
 * for a personal deployment; tighten if the cert chain is ever fixed.
 */
class DshApiClient(
    private val baseUrl: String,
    private val cookie: String,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val mediaType = "application/json".toMediaType()

    private val okHttpClient =
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, manager)
            .hostnameVerifier { _, _ -> true }
            .build()

    private val _connectionState = MutableStateFlow(DshConnectionState.Disconnected)
    val connectionState: StateFlow<DshConnectionState> = _connectionState

    private val _events = MutableSharedFlow<DshEvent>(extraBufferCapacity = 1024)
    val events: SharedFlow<DshEvent> = _events.asSharedFlow()

    private val openStreams = AtomicInteger(0)
    private val webSockets = mutableListOf<WebSocket>()
    private val streamJobs = mutableListOf<Job>()

    /** Authenticate and return a `dsh_session` cookie value, or null on failure. */
    suspend fun login(user: String, password: String): String? {
        val body =
            buildJsonObject { put("user", JsonPrimitive(user)); put("password", JsonPrimitive(password)) }
                .toString()
                .toRequestBody(mediaType)
        val req =
            Request.Builder()
                .url("$baseUrl/api/session-login")
                .post(body)
                .header("content-type", "application/json")
                .build()
        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.headers("set-cookie")
                    .firstNotNullOfOrNull { raw ->
                        raw.split(';').firstOrNull { it.trim().startsWith("dsh_session=") }
                    }?.substringAfter("dsh_session=")
            }
        }.getOrNull()
    }

    fun connect() {
        scope.launch { doConnect() }
    }

    private fun doConnect() {
        _connectionState.value = DshConnectionState.Connecting
        openStreams.set(0)
        // The uplink uses http(s), but the downlink is a WebSocket: OkHttp requires a ws/wss
        // scheme. Convert so both LAN http:// and external https:// server URLs work.
        val wsBase = baseUrl.replaceFirst("^https://".toRegex(), "wss://").replaceFirst("^http://".toRegex(), "ws://")
        openWebSocket("mux", "$wsBase/api/events.mux")
        openWebSocket("host", "$wsBase/api/events.host")
    }

    private fun openWebSocket(stream: String, url: String) {
        val req =
            Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .build()
        val ws =
            okHttpClient.newWebSocket(
                req,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        Log.d(TAG, "$stream downlink opened")
                        if (openStreams.incrementAndGet() >= 2) {
                            _connectionState.value = DshConnectionState.Connected
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleFrame(stream, text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        handleFrame(stream, bytes.utf8())
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        Log.w(TAG, "$stream downlink failure: ${t.message}")
                        if (_connectionState.value == DshConnectionState.Connected) {
                            _connectionState.value = DshConnectionState.Error
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "$stream downlink closed ($code)")
                    }
                },
            )
        synchronized(webSockets) { webSockets.add(ws) }
    }

    private fun handleFrame(stream: String, text: String) {
        runCatching {
            val sr = json.decodeFromString(DshServerRequest.serializer(), text)
            val payload = sr.payload ?: return@runCatching
            val type =
                (payload as? JsonObject)
                    ?.get("type")
                    ?.jsonPrimitive
                    ?.contentOrNull ?: sr.method
            _events.tryEmit(DshEvent(stream = stream, type = type, payload = payload, rpcId = sr.rpcId))
        }.onFailure { Log.w(TAG, "drop malformed downlink frame: $it") }
    }

    /** Unary call. Returns the `value` of the result (JsonNull if void). */
    suspend fun call(method: String, payload: JsonElement? = JsonNull): JsonElement {
        val rpcId = UUID.randomUUID().toString()
        val req = DshClientRequest(rpcId = rpcId, method = method, payload = payload)
        val body = json.encodeToString(DshClientRequest.serializer(), req).toRequestBody(mediaType)
        val httpReq =
            Request.Builder()
                .url("$baseUrl/api/$method")
                .post(body)
                .header("content-type", "application/json")
                .header("Cookie", cookie)
                .build()
        okHttpClient.newCall(httpReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} for /api/$method")
            }
            val text = resp.body?.string().orEmpty()
            val sr = json.decodeFromString(DshServerResponse.serializer(), text)
            if (sr.rpcId != rpcId) throw IllegalStateException("rpcId mismatch for $method")
            if (!sr.result.ok) throw IllegalStateException("rpc error: ${sr.result.error}")
            return sr.result.value ?: JsonNull
        }
    }

    /** Respond to a server→client request (approvals / questions). */
    suspend fun respond(rpcId: String, result: JsonElement) {
        val body =
            json
                .encodeToString(
                    DshClientResponse.serializer(),
                    DshClientResponse(rpcId = rpcId, result = DshRpcResult(ok = true, value = result)),
                )
                .toRequestBody(mediaType)
        val httpReq =
            Request.Builder()
                .url("$baseUrl/api/respond")
                .post(body)
                .header("content-type", "application/json")
                .header("Cookie", cookie)
                .build()
        okHttpClient.newCall(httpReq).execute().use { }
    }

    fun disconnect() {
        synchronized(webSockets) {
            webSockets.forEach { it.cancel() }
            webSockets.clear()
        }
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()
        openStreams.set(0)
        _connectionState.value = DshConnectionState.Disconnected
    }

    private companion object {
        const val TAG = "DshApiClient"

        val manager =
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }

        val sslContext =
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), SecureRandom()) }
    }
}
