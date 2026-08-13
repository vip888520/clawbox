package com.lobster.clawbox.gateway

import android.content.Context
import com.lobster.clawbox.config.ConfigManager
import com.lobster.clawbox.data.AppPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * WebSocket RPC client for the embedded OpenClaw gateway.
 *
 * Implements the gateway protocol (protocol v4):
 *  - connect (auth token + operator.admin scope via insecure local UI profile)
 *  - chat.history / chat.send / chat.abort / status / sessions.list / agents.list / models.list
 *  - subscribes to `chat` streaming events (delta / final / aborted / error)
 *
 * The app connects as `openclaw-tui` with `gateway.controlUi.allowInsecureAuth`
 * on loopback so the gateway keeps the requested operator scopes (verified).
 */
object GatewayApi {
    private const val TAG = "ClawBox-WS"
    enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

    data class ChatMsg(
        val role: String,          // "user" | "assistant"
        val text: String,
        val ts: Long,
        val messageId: String? = null,
    )

    data class ChatRunEvent(
        val state: String,         // delta | final | aborted | error
        val runId: String,
        val sessionKey: String,
        val deltaText: String = "",
        val fullText: String = "",
        val errorMessage: String? = null,
        val errorKind: String? = null,
    )

    val connState: StateFlow<ConnState> = MutableStateFlow(ConnState.DISCONNECTED)
    val serverVersion: StateFlow<String> = MutableStateFlow("")
    val chatEvents: SharedFlow<ChatRunEvent> = MutableSharedFlow(extraBufferCapacity = 64)

    private lateinit var prefs: AppPrefs
    private lateinit var client: OkHttpClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ws: WebSocket? = null
    private var connectJob: Job? = null
    private var running = false
    private var seq = 0
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()

    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        prefs = AppPrefs(context.applicationContext)
        // NOTE: no pingInterval. The bionic node ws server on-device does not
        // reliably answer OkHttp pings (pong timeout kills the link); loopback
        // connections don't need keepalive anyway.
        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Start the connect loop (call when gateway process starts). */
    fun start() {
        if (running) return
        running = true
        connectJob?.cancel()
        connectJob = scope.launch {
            var backoff = 500L
            while (running) {
                if (!GatewayProcess.isRunning) {
                    setState(ConnState.DISCONNECTED)
                    delay(1000)
                    continue
                }
                try {
                    connectOnce()
                    backoff = 500L
                    // keep connection alive; the listener handles drops
                    while (running && GatewayProcess.isRunning && ws != null) delay(2000)
                } catch (e: Exception) {
                    // connection failed, retry with backoff
                }
                if (running) {
                    setState(ConnState.DISCONNECTED)
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(5000)
                }
            }
        }
    }

    /** Stop the connect loop and close the socket. */
    fun stop() {
        running = false
        connectJob?.cancel()
        connectJob = null
        closeWs()
        setState(ConnState.DISCONNECTED)
    }

    private fun connectOnce() {
        val port = prefs.gatewayPort
        val token = ConfigManager.ensureToken()
        android.util.Log.d(TAG, "connectOnce ws://127.0.0.1:$port")
        val req = Request.Builder()
            .url("ws://127.0.0.1:$port")
            .build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ws = webSocket
                sendConnect(webSocket, token)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (ws === webSocket) ws = null
                setState(ConnState.DISCONNECTED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e(TAG, "WS onFailure: ${t.message} resp=${response?.code}", t)
                if (ws === webSocket) ws = null
                setState(ConnState.DISCONNECTED)
                failAllPending(t)
            }
        }
        ws = client.newWebSocket(req, listener)
        // wait until hello-ok or failure; loop continues on error
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            if (connState.value == ConnState.CONNECTED) return
            if (ws == null) return
            Thread.sleep(150)
        }
        android.util.Log.d(TAG, "connectOnce deadline hit, state=${connState.value}")
    }

    private fun sendConnect(socket: WebSocket, token: String) {
        setState(ConnState.CONNECTING)
        android.util.Log.d(TAG, "sendConnect token=${token.take(6)}…")
        val frame = buildJsonObject {
            put("type", "req")
            put("id", "c1")
            put("method", "connect")
            putJsonObject("params") {
                put("minProtocol", 4)
                put("maxProtocol", 4)
                putJsonObject("client") {
                    put("id", "openclaw-tui")
                    put("displayName", "ClawBox")
                    put("version", "1.0.0")
                    put("platform", "android")
                    put("mode", "ui")
                    put("instanceId", "clawbox-" + UUID.randomUUID().toString().take(8))
                }
                put("role", "operator")
                put("scopes", JsonArray(listOf(JsonPrimitive("operator.admin"))))
                putJsonObject("auth") {
                    put("token", token)
                }
            }
        }
        socket.send(frame.toString())
    }

    private fun handleFrame(text: String) {
        android.util.Log.d(TAG, "recv: ${text.take(200)}")
        val el = try { json.parseToJsonElement(text) } catch (e: Exception) {
            android.util.Log.e(TAG, "parse fail: ${text.take(200)}")
            return
        }
        if (el !is JsonObject) return
        val type = el["type"]?.jsonPrimitive?.contentOrNull ?: return
        when (type) {
            "res" -> handleResponse(el)
            "event" -> handleEvent(el)
        }
    }

    private fun handleResponse(obj: JsonObject) {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return
        val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        // c1 (connect) is sent directly in onOpen, never registered in pending.
        // Handle it BEFORE the pending lookup, otherwise the early return
        // swallows the CONNECTED transition (the whole link stays CONNECTING).
        if (id == "c1") {
            android.util.Log.d(TAG, "connect res ok=$ok")
            if (ok) {
                val payload = obj["payload"] as? JsonObject
                val ver = payload?.get("server")?.let { s -> (s as? JsonObject)?.get("version")?.jsonPrimitive?.contentOrNull }
                if (ver != null) (serverVersion as MutableStateFlow).value = ver
                setState(ConnState.CONNECTED)
            }
            return
        }
        val deferred = pending.remove(id) ?: return
        if (ok) {
            deferred.complete(obj["payload"] ?: JsonNull)
        } else {
            val err = obj["error"] ?: JsonNull
            val msg = (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: "gateway error"
            deferred.completeExceptionally(RpcException(msg, err))
        }
    }

    private fun handleEvent(obj: JsonObject) {
        val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: return
        if (event == "connect.challenge") return
        if (event == "chat") {
            val p = obj["payload"] as? JsonObject ?: return
            val state = p["state"]?.jsonPrimitive?.contentOrNull ?: return
            val runId = p["runId"]?.jsonPrimitive?.contentOrNull ?: ""
            val sessionKey = p["sessionKey"]?.jsonPrimitive?.contentOrNull ?: ""
            val delta = p["deltaText"]?.jsonPrimitive?.contentOrNull ?: ""
            val full = extractText(p["message"])
            val errMsg = p["errorMessage"]?.jsonPrimitive?.contentOrNull
            val errKind = p["errorKind"]?.jsonPrimitive?.contentOrNull
            (chatEvents as MutableSharedFlow).tryEmit(
                ChatRunEvent(state, runId, sessionKey, delta, full, errMsg, errKind),
            )
        }
    }

    private fun extractText(el: JsonElement?): String {
        if (el == null || el is JsonNull) return ""
        val obj = el as? JsonObject ?: return ""
        val content = obj["content"] ?: return ""
        return when (content) {
            is JsonPrimitive -> content.contentOrNull ?: ""
            is JsonArray -> content.mapNotNull { block ->
                val b = block as? JsonObject ?: return@mapNotNull null
                if (b["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    b["text"]?.jsonPrimitive?.contentOrNull
                } else null
            }.joinToString("")
            else -> ""
        }
    }

    // ── Public RPC ──────────────────────────────────────────

    suspend fun request(method: String, params: JsonElement? = null, timeoutMs: Long = 60_000): JsonElement {
        val socket = ws ?: throw RpcException("gateway not connected", null)
        val id = "r" + (++seq)
        val frame = buildJsonObject {
            put("type", "req")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        if (!socket.send(frame.toString())) {
            pending.remove(id)
            throw RpcException("gateway send failed", null)
        }
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: throw RpcException("gateway request timeout: $method", null)
    }

    suspend fun chatHistory(sessionKey: String, limit: Int = 50): Pair<String?, List<ChatMsg>> {
        val payload = request("chat.history", buildJsonObject {
            put("sessionKey", sessionKey)
            put("limit", limit)
        })
        val obj = payload as? JsonObject ?: return null to emptyList()
        val sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull
        val msgs = (obj["messages"] as? JsonArray)?.mapNotNull { m ->
            val mo = m as? JsonObject ?: return@mapNotNull null
            val role = mo["role"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val content = mo["content"] ?: return@mapNotNull null
            val text = when (content) {
                is JsonPrimitive -> content.contentOrNull ?: ""
                is JsonArray -> content.mapNotNull { block ->
                    val b = block as? JsonObject ?: return@mapNotNull null
                    if (b["type"]?.jsonPrimitive?.contentOrNull == "text") b["text"]?.jsonPrimitive?.contentOrNull else null
                }.joinToString("")
                else -> ""
            }
            if (text.isBlank() && role != "user") return@mapNotNull null
            val ts = mo["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
            val id = (mo["__openclaw"] as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            ChatMsg(role, text, ts, id)
        } ?: emptyList()
        return sessionId to msgs
    }

    suspend fun chatSend(sessionKey: String, sessionId: String?, message: String, idempotencyKey: String) {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            if (sessionId != null) put("sessionId", sessionId)
            put("message", message)
            put("idempotencyKey", idempotencyKey)
        }
        request("chat.send", params, timeoutMs = 30_000)
    }

    suspend fun chatAbort(sessionKey: String, runId: String? = null) {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            if (runId != null) put("runId", runId)
        }
        try { request("chat.abort", params, timeoutMs = 10_000) } catch (_: Exception) {}
    }

    suspend fun gatewayStatus(): JsonObject? =
        try { request("status", null, timeoutMs = 10_000) as? JsonObject } catch (_: Exception) { null }

    suspend fun listSessions(): List<JsonObject> {
        return try {
            val payload = request("sessions.list", null, timeoutMs = 10_000)
            ((payload as? JsonObject)?.get("sessions") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun listAgents(): List<JsonObject> {
        return try {
            val payload = request("agents.list", null, timeoutMs = 10_000)
            ((payload as? JsonObject)?.get("agents") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun listModels(): List<JsonObject> {
        return try {
            val payload = request("models.list", null, timeoutMs = 10_000)
            ((payload as? JsonObject)?.get("models") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun setState(s: ConnState) {
        (connState as MutableStateFlow).value = s
    }

    private fun closeWs() {
        ws?.close(1000, "app stop")
        ws = null
    }

    private fun failAllPending(t: Throwable) {
        pending.values.forEach { it.completeExceptionally(RpcException("gateway connection lost: ${t.message}", null)) }
        pending.clear()
    }

    class RpcException(message: String, val payload: JsonElement?) : Exception(message)
}
