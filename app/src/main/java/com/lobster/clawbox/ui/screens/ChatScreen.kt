package com.lobster.clawbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lobster.clawbox.data.AppPrefs
import com.lobster.clawbox.gateway.GatewayApi
import com.lobster.clawbox.ui.ClawTopBar
import com.lobster.clawbox.ui.Pill
import com.lobster.clawbox.ui.theme.Bg2
import com.lobster.clawbox.ui.theme.Bg3
import com.lobster.clawbox.ui.theme.Green
import com.lobster.clawbox.ui.theme.Purple
import com.lobster.clawbox.ui.theme.PurpleDeep
import com.lobster.clawbox.ui.theme.Red
import com.lobster.clawbox.ui.theme.TextDim
import com.lobster.clawbox.ui.theme.TextMuted
import com.lobster.clawbox.ui.theme.TextPrimary
import com.lobster.clawbox.ui.theme.Warn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class UiMsg(
    val role: String,          // user | assistant
    val text: String,
    val ts: Long,
    val messageId: String? = null,
    val error: Boolean = false,
)

@Composable
fun ChatScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var sessionKey by remember { mutableStateOf(prefs.sessionKey) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var connText by remember { mutableStateOf("未连接") }
    var connColor by remember { mutableStateOf(Red) }
    var streamingText by remember { mutableStateOf<String?>(null) }
    var activeRun by remember { mutableStateOf<String?>(null) }

    val msgs = remember { mutableStateListOf<UiMsg>() }

    fun formatTime(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    fun loadHistory() {
        scope.launch {
            loading = true
            loadError = null
            try {
                val (sid, list) = GatewayApi.chatHistory(sessionKey)
                sessionId = sid
                msgs.clear()
                msgs.addAll(list.map { UiMsg(it.role, it.text, it.ts, it.messageId) })
                if (msgs.isEmpty()) {
                    msgs.add(UiMsg("assistant", "老板，我是大龙虾 🦞，ClawBox 手机主机已就绪。直接说话就行。", System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                loadError = e.message ?: "加载历史失败"
            } finally {
                loading = false
            }
        }
    }

    // initial load + reload on session switch / reconnect
    LaunchedEffect(sessionKey, GatewayApi.connState.value) {
        if (GatewayApi.connState.value == GatewayApi.ConnState.CONNECTED) {
            loadHistory()
        }
    }

    // connection state ticker
    LaunchedEffect(GatewayApi.connState.value) {
        when (GatewayApi.connState.value) {
            GatewayApi.ConnState.CONNECTED -> { connText = "网关已连接"; connColor = Green }
            GatewayApi.ConnState.CONNECTING -> { connText = "连接网关…"; connColor = Warn }
            GatewayApi.ConnState.DISCONNECTED -> { connText = "网关未运行"; connColor = Red }
        }
    }

    // streaming events
    LaunchedEffect(Unit) {
        GatewayApi.chatEvents.collect { ev ->
            when (ev.state) {
                "delta" -> {
                    if (ev.runId == activeRun) {
                        streamingText = (streamingText ?: "") + ev.deltaText
                    }
                }
                "final" -> {
                    if (ev.runId == activeRun) {
                        val full = ev.fullText.ifBlank { streamingText ?: "" }
                        streamingText = null
                        activeRun = null
                        sending = false
                        if (full.isNotBlank()) {
                            msgs.add(UiMsg("assistant", full, System.currentTimeMillis()))
                        }
                    }
                }
                "aborted" -> {
                    if (ev.runId == activeRun) {
                        streamingText = null
                        activeRun = null
                        sending = false
                    }
                }
                "error" -> {
                    if (ev.runId == activeRun || ev.runId == "") {
                        streamingText = null
                        activeRun = null
                        sending = false
                        msgs.add(
                            UiMsg(
                                "assistant",
                                ev.errorMessage ?: "请求失败，请检查设置里的 API Key 和网络",
                                System.currentTimeMillis(),
                                error = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        sending = true
        activeRun = UUID.randomUUID().toString()
        streamingText = ""
        msgs.add(UiMsg("user", text, System.currentTimeMillis()))
        scope.launch {
            try {
                GatewayApi.chatSend(sessionKey, sessionId, text, activeRun!!)
                // streamed via events
            } catch (e: Exception) {
                streamingText = null
                activeRun = null
                sending = false
                msgs.add(UiMsg("assistant", e.message ?: "发送失败（网关未连接）", System.currentTimeMillis(), error = true))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ClawTopBar("大龙虾", "clawbox · 本机网关") {
            Pill(connText, connColor)
        }

        // session chips
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("main", "任务", "笔记").forEach { key ->
                SessionChip(key, key == sessionKey) {
                    sessionKey = key
                    prefs.sessionKey = key
                    sessionId = null
                    msgs.clear()
                    loadHistory()
                }
            }
        }

        if (loadError != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Red.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable { loadHistory() },
            ) {
                Text("⚠ $loadError（点按重试）", color = Red, fontSize = 9.5.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                item { Text("加载会话…", color = TextMuted, fontSize = 10.sp) }
            }
            items(msgs) { m ->
                ChatBubble(m, formatTime(m.ts))
            }
            streamingText?.let { st ->
                item { StreamingBubble(st) }
            }
            if (sending && streamingText.isNullOrEmpty()) {
                item { TypingBubble() }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Bg3)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text("给大龙虾发消息…", color = TextMuted, fontSize = 11.sp)
                        }
                        inner()
                    },
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (input.isBlank() || sending) SolidColor(Bg3) else Brush.linearGradient(listOf(Purple, PurpleDeep)))
                    .clickable(enabled = input.isNotBlank() && !sending) { send() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("发送", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SessionChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Brush.linearGradient(listOf(Purple.copy(alpha = 0.22f), PurpleDeep.copy(alpha = 0.14f))) else SolidColor(Bg3))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (on) Purple else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChatBubble(m: UiMsg, time: String) {
    val fromMe = m.role == "user"
    val align: Alignment.Horizontal = if (fromMe) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (fromMe) 16.dp else 5.dp,
                        bottomEnd = if (fromMe) 5.dp else 16.dp,
                    ),
                )
                .background(
                    if (m.error) SolidColor(Color(0xFF3A2026))
                    else if (fromMe) Brush.linearGradient(listOf(Color(0xFF332A66), Color(0xFF2A2156)))
                    else Brush.linearGradient(listOf(Bg2, Color(0xFF121625))),
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            Text(
                if (m.error) "⚠ 错误" else if (fromMe) "你" else "大龙虾 🦞",
                color = if (m.error) Warn else if (fromMe) Color(0xFFB9A6FF) else Purple,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
            )
            Text(
                m.text,
                color = if (m.error) Color(0xFFE8C4C4) else if (fromMe) Color(0xFFE8E1FF) else Color(0xFFD3D9EA),
                fontSize = 11.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(time, color = TextDim, fontSize = 7.5.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 5.dp, 16.dp))
                .background(Brush.linearGradient(listOf(Bg2, Color(0xFF121625))))
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            Text("大龙虾 🦞", color = Purple, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
            Text(
                text + "▌",
                color = Color(0xFFD3D9EA), fontSize = 11.sp, lineHeight = 18.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 5.dp, 16.dp))
                .background(Bg2)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text("● ● ●", color = Purple, fontSize = 10.sp, letterSpacing = 3.sp)
        }
    }
}
