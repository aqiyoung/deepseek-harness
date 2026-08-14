package com.deepseek.dsh.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.dsh.data.HarnessClient
import com.deepseek.dsh.data.QuestionAnswer
import com.deepseek.dsh.data.SettingsStore
import com.deepseek.dsh.data.model.ServerRequest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val streaming: Boolean = false,
)

/** A pending tool-call approval surfaced by the agent (approval/requested frame). */
@Immutable
data class PendingApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val reason: String?,
)

/** A single question option as received from the host. */
@Immutable
data class QuestionOption(
    val label: String,
    val description: String?,
)

/** A single question item (question/requested frame). */
@Immutable
data class QuestionItem(
    val id: String,
    val question: String,
    val header: String?,
    val detail: String?,
    val options: List<QuestionOption>?,
    val multiSelect: Boolean,
)

/** A pending batch of questions surfaced by the agent. */
@Immutable
data class PendingQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<QuestionItem>,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private var client: HarnessClient? = null

    // ---- chat UI state ----
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    var input by mutableStateOf("")
    var status by mutableStateOf("未连接")
    var connected by mutableStateOf(false)
    var showSettings by mutableStateOf(false)

    var baseUrl by mutableStateOf(settings.load().baseUrl)
    var username by mutableStateOf(settings.load().username)
    var password by mutableStateOf(settings.load().password)

    // ---- approval / question state ----
    private val _approvals = mutableStateListOf<PendingApproval>()
    val approvals: List<PendingApproval> get() = _approvals

    private val _questions = mutableStateListOf<PendingQuestion>()
    val questions: List<PendingQuestion> get() = _questions

    /** question item id -> selected option labels */
    val selections = mutableStateMapOf<String, List<String>>()
    /** question item id -> free-text answer */
    val customText = mutableStateMapOf<String, String>()

    val hasPending: Boolean get() = _approvals.isNotEmpty() || _questions.isNotEmpty()

    private var currentSessionId: String? = null
    private var streamingId: String? = null

    // ---- connection ----
    fun connect() {
        val cfg = settings.load()
        if (cfg.baseUrl.isBlank()) {
            status = "请先在设置里填写域名"
            showSettings = true
            return
        }
        client = HarnessClient(cfg.baseUrl, cfg.username, cfg.password)
        status = "连接中…"
        client!!.openEventStreams()
            .onEach { handleFrame(it) }
            .catch { e -> status = "事件流错误: ${e.message}" }
            .launchIn(viewModelScope)
        connected = true
        status = "已连接 · 等待会话"
    }

    fun saveSettings() {
        settings.save(baseUrl, username, password)
        showSettings = false
        status = "设置已保存,点“保存并连接”"
    }

    fun newChat() {
        currentSessionId = null
        streamingId = null
        _messages.clear()
        _approvals.clear()
        _questions.clear()
        status = "新会话已就绪"
    }

    // ---- send a message ----
    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        val c = client ?: run { status = "尚未连接"; return }
        viewModelScope.launch {
            var sid = currentSessionId
            if (sid == null) {
                val r = c.call("session.create", buildJsonObject { })
                if (!r.ok) {
                    status = "创建会话失败: ${r.error?.code}"
                    return@launch
                }
                sid = r.value!!.jsonObject["sessionId"]!!.jsonPrimitive.content
                currentSessionId = sid
            }
            _messages.add(ChatMessage(UUID.randomUUID().toString(), "user", text))
            input = ""
            streamingId = null

            val r = c.call(
                "session.prompt",
                buildJsonObject {
                    put("sessionId", sid)
                    put("mode", "queue")
                    putJsonArray("content") {
                        add(buildJsonObject { put("type", "text"); put("text", text) })
                    }
                },
            )
            if (!r.ok) {
                status = "发送失败: ${r.error?.code} ${r.error?.message.orEmpty()}"
            }
        }
    }

    // ---- frame dispatch ----
    private fun handleFrame(frame: ServerRequest) {
        when (frame.method) {
            "session/event" -> handleSessionEvent(frame.payload as? JsonObject)
            "session/subscribed" -> { /* subscription confirmed */ }
            "approval/requested" -> handleApprovalRequested(frame)
            "approval/resolved" -> handleApprovalResolved(frame.payload as? JsonObject)
            "question/requested" -> handleQuestionRequested(frame)
            "question/resolved" -> handleQuestionResolved(frame.payload as? JsonObject)
            "stream/error" -> {
                val e = (frame.payload as? JsonObject)?.get("error")
                status = "流错误: $e"
            }
            // host/* frames and anything else: ignored for the MVP
        }
    }

    private fun handleApprovalRequested(frame: ServerRequest) {
        val p = frame.payload as? JsonObject ?: return
        val sid = p["sessionId"]?.jsonPrimitive?.content ?: return
        val aid = p["approvalId"]?.jsonPrimitive?.content ?: return
        val tool = p["toolName"]?.jsonPrimitive?.content ?: "?"
        val reason = p["reason"]?.jsonPrimitive?.content
        if (currentSessionId != null && sid != currentSessionId) return
        _approvals.add(PendingApproval(frame.rpcId, sid, aid, tool, reason))
    }

    private fun handleApprovalResolved(p: JsonObject?) {
        val aid = p?.get("approvalId")?.jsonPrimitive?.content ?: return
        _approvals.removeAll { it.approvalId == aid }
    }

    private fun handleQuestionRequested(frame: ServerRequest) {
        val p = frame.payload as? JsonObject ?: return
        val sid = p["sessionId"]?.jsonPrimitive?.content ?: return
        val items = (p["questions"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { parseQuestionItem(it) }
            ?: return
        if (currentSessionId != null && sid != currentSessionId) return
        _questions.add(PendingQuestion(frame.rpcId, sid, items))
    }

    private fun handleQuestionResolved(p: JsonObject?) {
        val qRpc = p?.get("questionRpcId")?.jsonPrimitive?.content ?: return
        _questions.firstOrNull { it.rpcId == qRpc }?.let { removeQuestionState(it) }
        _questions.removeAll { it.rpcId == qRpc }
    }

    private fun parseQuestionItem(o: JsonObject): QuestionItem? {
        val id = o["id"]?.jsonPrimitive?.content ?: return null
        val q = o["question"]?.jsonPrimitive?.content ?: return null
        val header = o["header"]?.jsonPrimitive?.content
        val detail = o["detail"]?.jsonPrimitive?.content
        val options = (o["options"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { ob ->
                val label = ob["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
                QuestionOption(label, ob["description"]?.jsonPrimitive?.content)
            }
        val multi = o["multiSelect"]?.jsonPrimitive?.boolean ?: false
        return QuestionItem(id, q, header, detail, options, multi)
    }

    // ---- respond to approvals ----
    fun respondApproval(item: PendingApproval, outcome: String) {
        val c = client ?: return
        viewModelScope.launch {
            runCatching {
                c.respondApproval(item.rpcId, item.sessionId, item.approvalId, outcome)
            }.onFailure { e -> status = "回应失败: ${e.message}" }
            _approvals.remove(item)
        }
    }

    // ---- respond to questions ----
    fun toggleOption(questionId: String, label: String, multi: Boolean) {
        val cur = selections[questionId].orEmpty()
        selections[questionId] = if (multi) {
            if (cur.contains(label)) cur - label else cur + label
        } else {
            listOf(label)
        }
    }

    fun setCustom(questionId: String, text: String) {
        customText[questionId] = text
    }

    fun respondQuestion(item: PendingQuestion) {
        val c = client ?: return
        val answers = item.questions.mapNotNull { qi ->
            val sel = selections[qi.id].orEmpty()
            val cus = customText[qi.id]?.takeIf { it.isNotBlank() }
            if (sel.isEmpty() && cus == null) null
            else QuestionAnswer(qi.id, sel, cus)
        }
        if (answers.isEmpty()) {
            status = "请先选择或填写至少一个问题的答案"
            return
        }
        viewModelScope.launch {
            runCatching {
                c.respondQuestion(item.rpcId, item.sessionId, answers)
            }.onFailure { e -> status = "回应失败: ${e.message}" }
            removeQuestionState(item)
            _questions.remove(item)
        }
    }

    private fun removeQuestionState(item: PendingQuestion) {
        item.questions.forEach {
            selections.remove(it.id)
            customText.remove(it.id)
        }
    }

    // ---- session event rendering ----
    private fun handleSessionEvent(obj: JsonObject?) {
        obj ?: return
        val sid = obj["sessionId"]?.jsonPrimitive?.content
        if (sid != null && currentSessionId != null && sid != currentSessionId) return
        val event = obj["event"]?.jsonObject ?: return
        val type = event["type"]?.jsonPrimitive?.content ?: return
        when (type) {
            "assistant/chunk" -> appendChunk(event["data"])
            "assistant/message" -> setFinal(contentText(event["data"]))
            "user/message" -> { /* already shown optimistically on send */ }
            else -> { /* unknown event type; extend here as needed */ }
        }
    }

    private fun appendChunk(data: kotlinx.serialization.json.JsonElement?) {
        val t = pickText(data, listOf("delta", "text")) ?: return
        val id = streamingId ?: run {
            val n = UUID.randomUUID().toString()
            _messages.add(ChatMessage(n, "assistant", ""))
            streamingId = n
            n
        }
        val idx = _messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _messages[idx] = _messages[idx].copy(text = _messages[idx].text + t)
        }
    }

    private fun setFinal(text: String) {
        val id = streamingId ?: UUID.randomUUID().toString()
        val idx = _messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _messages[idx] = _messages[idx].copy(text = text, streaming = false)
        } else {
            _messages.add(ChatMessage(id, "assistant", text))
        }
        streamingId = null
    }

    /** Try the listed keys for a plain string field (assistant/chunk text carrier). */
    private fun pickText(data: kotlinx.serialization.json.JsonElement?, keys: List<String>): String? {
        val o = (data as? JsonObject) ?: return null
        for (k in keys) {
            (o[k] as? JsonPrimitive)?.content?.let { return it }
        }
        return null
    }

    /** Extract text from a message content block array: [{type:'text', text:'...'}]. */
    private fun contentText(data: kotlinx.serialization.json.JsonElement?): String {
        val o = (data as? JsonObject) ?: return ""
        val arr = (o["content"] as? JsonArray) ?: return ""
        return arr.joinToString("") { blk ->
            val b = blk as? JsonObject ?: return@joinToString ""
            (b["text"] as? JsonPrimitive)?.content ?: ""
        }
    }
}
