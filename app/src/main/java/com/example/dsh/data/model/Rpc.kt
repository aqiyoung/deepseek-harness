package com.example.dsh.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire envelopes verified against deepseek-harness `packages/client/connection/src/client/rpc.ts`
 * and `packages/host/apiproxy/src/api/rpc.schema.ts`.
 *
 * ClientRequest  -> POST /api/<method> body
 * ServerResponse -> POST /api/<method> response body
 * ServerRequest  -> WebSocket downlink frame on /api/events.mux | /api/events.host
 */

@Serializable
data class ClientRequest(
    val type: String = "client-request",
    val rpcId: String,
    val method: String,
    val payload: JsonElement,
)

@Serializable
data class ServerResponse(
    val type: String = "server-response",
    val rpcId: String,
    val result: RpcResult,
)

@Serializable
data class ServerRequest(
    val type: String = "server-request",
    val rpcId: String,
    val method: String,
    val payload: JsonElement,
)

@Serializable
data class RpcResult(
    val ok: Boolean,
    val value: JsonElement? = null,
    val error: RpcError? = null,
)

@Serializable
data class RpcError(
    val code: String,
    val message: String? = null,
    val details: JsonElement? = null,
)

/**
 * ClientResponse -> POST /api/respond body.
 * Used to answer server-requested approvals / questions. `rpcId` MUST echo the
 * rpcId of the original `server-request` frame (the host routes it via its pending table).
 * `result.value` carries the domain answer payload (see approvals/question schemas).
 */
@Serializable
data class ClientResponse(
    val type: String = "client-response",
    val rpcId: String,
    val result: RpcResult,
)
