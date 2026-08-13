package com.lobster.clawbox.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lobster.clawbox.config.ConfigManager
import com.lobster.clawbox.data.AppPrefs
import com.lobster.clawbox.gateway.GatewayApi
import com.lobster.clawbox.gateway.GatewayProcess
import com.lobster.clawbox.gateway.GatewayService
import com.lobster.clawbox.runtime.RuntimeManager
import com.lobster.clawbox.ui.ClawTopBar
import com.lobster.clawbox.ui.Pill
import com.lobster.clawbox.ui.SectionLabel
import com.lobster.clawbox.ui.StatusDot
import com.lobster.clawbox.ui.theme.Bg2
import com.lobster.clawbox.ui.theme.Bg3
import com.lobster.clawbox.ui.theme.Green
import com.lobster.clawbox.ui.theme.Pink
import com.lobster.clawbox.ui.theme.Purple
import com.lobster.clawbox.ui.theme.PurpleDeep
import com.lobster.clawbox.ui.theme.Red
import com.lobster.clawbox.ui.theme.TextDim
import com.lobster.clawbox.ui.theme.TextMuted
import com.lobster.clawbox.ui.theme.TextPrimary
import com.lobster.clawbox.ui.theme.Warn
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var running by remember { mutableStateOf(GatewayService.running) }
    var startMs by remember { mutableLongStateOf(GatewayService.startedAtMs) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wsState by remember { mutableStateOf(GatewayApi.connState.value) }

    LaunchedEffect(Unit) {
        while (true) {
            running = GatewayService.running
            startMs = GatewayService.startedAtMs
            now = System.currentTimeMillis()
            wsState = GatewayApi.connState.value
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ClawTopBar("ClawBox", "手机即主机 · 本机运行") {
            Pill(
                when {
                    running && wsState == GatewayApi.ConnState.CONNECTED -> "● 运行中"
                    running -> "启动中…"
                    else -> "已停止"
                },
                when {
                    running && wsState == GatewayApi.ConnState.CONNECTED -> Green
                    running -> Warn
                    else -> Red
                },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 14.dp),
        ) {
            HeroCard(running, startMs, now, onOpenChat, context, prefs)
            SectionLabel("✦ 组件状态")
            ComponentList(prefs, running)
            SectionLabel("✦ 最近日志")
            LogLines()
        }
    }
}

@Composable
private fun HeroCard(
    running: Boolean,
    startMs: Long,
    now: Long,
    onOpenChat: () -> Unit,
    context: Context,
    prefs: AppPrefs,
) {
    Column(
        Modifier
            .padding(14.dp, 14.dp, 14.dp, 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1D1440), Color(0xFF161033), Color(0xFF121024))),
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(if (running) Green else Red, size = 9)
            Text(
                if (running) "OpenClaw Gateway 运行中" else "Gateway 已停止",
                color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            )
        }
        Text(
            "127.0.0.1:${prefs.gatewayPort}" + if (prefs.lanAccess) " · 局域网可访问" else " · 仅本机",
            color = Color(0xFFA9A2D4), fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp),
        )
        val uptime = if (running && startMs > 0) {
            val s = (now - startMs) / 1000
            "⏱ 已运行 ${TimeUnit.SECONDS.toMinutes(s)} 分 ${s % 60} 秒"
        } else "⏱ 未运行"
        Text(uptime, color = Color(0xFF6F6899), fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))

        val modelRef = remember { ConfigManager.modelRef() }
        val hasKey = remember { ConfigManager.hasApiKey() }
        Text(
            if (hasKey) "🧠 模型: $modelRef ✓" else "🧠 模型: 未配置 API Key（去设置页填写）",
            color = if (hasKey) Color(0xFF9FE8B0) else Warn,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp),
        )

        Row(Modifier.padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(
                onClick = onOpenChat,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleDeep),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text("💬 打开聊天", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { GatewayService.start(context) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text("🔄 启动", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { GatewayService.stop(context) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text("⏹ 停止", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ComponentList(prefs: AppPrefs, running: Boolean) {
    val runtimeOk = RuntimeManager.isInstalled()
    val wsConnected = GatewayApi.connState.value == GatewayApi.ConnState.CONNECTED
    val keyOk = ConfigManager.hasApiKey()
    CompRow("🟢", "Node.js 运行时", if (runtimeOk) "已安装" else "未安装（去设置导入）", runtimeOk)
    CompRow("🔑", "模型 API Key", if (keyOk) "已配置" else "未配置", keyOk)
    CompRow("🛰", "Gateway 进程", if (running) "运行中 · 端口 ${prefs.gatewayPort}" else "已停止", running)
    // wsConnected can lag behind the live socket (reconnect churn); when the
    // gateway is running treat non-connected as reconnecting, not dead.
    val wsDesc = when {
        wsConnected -> "已连接（实时）"
        running -> "重连中…"
        else -> "未连接"
    }
    CompRow("🔌", "App 连接", wsDesc, wsConnected || running)
}

@Composable
private fun CompRow(icon: String, title: String, desc: String, ok: Boolean) {
    Row(
        Modifier
            .padding(14.dp, 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bg2)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Purple.copy(alpha = 0.16f), Pink.copy(alpha = 0.10f)))),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 13.sp) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = TextMuted, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
        }
        Box(Modifier.weight(1f))
        Text(if (ok) "✓" else "✕", color = if (ok) Green else Red, fontSize = 12.sp)
    }
}

@Composable
private fun LogLines() {
    var lines by remember { mutableStateOf(GatewayProcess.logTail(20)) }
    LaunchedEffect(Unit) {
        while (true) {
            lines = GatewayProcess.logTail(20)
            kotlinx.coroutines.delay(2000)
        }
    }
    if (lines.isEmpty()) {
        Text("（暂无日志，启动网关后这里会显示实时输出）", color = TextDim, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        return
    }
    lines.takeLast(14).forEach { line ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            Text(line, color = Color(0xFF6A7390), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}
