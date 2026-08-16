package ai.deepseek.harness.dsh

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Lifecycle of the DSH native connection. */
enum class DshConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

/** C→S unary request envelope (POST /api/<method>). */
@Serializable
data class DshClientRequest(
    val type: String = "client-request",
    val rpcId: String,
    val method: String,
    val payload: JsonElement? = null,
)

/** S→C unary result envelope. */
@Serializable
data class DshRpcResult(
    val ok: Boolean,
    val value: JsonElement? = null,
    val error: JsonElement? = null,
)

/** S→C unary response envelope (response to a client-request). */
@Serializable
data class DshServerResponse(
    val type: String = "server-response",
    val rpcId: String,
    val result: DshRpcResult,
)

/** S→C downlink frame envelope (mux / host event stream message). */
@Serializable
data class DshServerRequest(
    val type: String = "server-request",
    val rpcId: String,
    val method: String,
    val payload: JsonElement? = null,
)

/** C→S response to a server-request (approvals / questions). */
@Serializable
data class DshClientResponse(
    val type: String = "client-response",
    val rpcId: String,
    val result: DshRpcResult,
)

/**
 * A single downlink frame from the mux or host stream.
 * `payload` is the raw frame object (discriminated by `type`, e.g. `session/event`).
 */
data class DshEvent(
    val stream: String, // "mux" | "host"
    val type: String,
    val payload: JsonElement,
    val rpcId: String,
)

/** Convenience accessors for downlink frame payloads. */
val DshEvent.payloadObject: JsonObject? get() = payload as? JsonObject
val DshEvent.payloadArray: JsonArray? get() = payload as? JsonArray
val JsonElement.isNullOrNull: Boolean get() = this is JsonNull
