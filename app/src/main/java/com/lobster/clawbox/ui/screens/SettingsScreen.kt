package com.lobster.clawbox.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lobster.clawbox.config.ConfigManager
import com.lobster.clawbox.data.AppPrefs
import com.lobster.clawbox.gateway.GatewayProcess
import com.lobster.clawbox.gateway.GatewayService
import com.lobster.clawbox.runtime.RuntimeInstaller
import com.lobster.clawbox.runtime.RuntimeManager
import com.lobster.clawbox.ui.ClawTopBar
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()

    // ── model config state ──
    var provider by remember { mutableStateOf(prefs.modelProvider) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var modelName by remember { mutableStateOf(prefs.modelName) }
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var saveMsg by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    // ── runtime install state ──
    var installing by remember { mutableStateOf(false) }
    var installProgress by remember { mutableIntStateOf(0) }
    var installStage by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }

    // ── logs ──
    var showLogs by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            installing = true
            installProgress = 0
            installError = null
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        RuntimeInstaller(context).installFromFile(uri) { p ->
                            mainHandler.post {
                                installProgress = p.percent
                                installStage = p.stage
                            }
                        }
                        mainHandler.post { installStage = "安装完成 ✓" }
                    } catch (e: Exception) {
                        mainHandler.post { installError = e.message ?: "安装失败" }
                    } finally {
                        mainHandler.post { installing = false }
                    }
                }
            }
        }
    }

    fun saveSettings() {
        prefs.modelProvider = provider
        prefs.apiKey = apiKey.trim()
        prefs.modelName = modelName.trim().ifEmpty { "deepseek-chat" }
        prefs.baseUrl = baseUrl.trim().ifEmpty { "https://api.deepseek.com" }
        try {
            ConfigManager.writeConfig()
            // apply: restart gateway if running
            if (GatewayService.running) {
                GatewayService.stop(context)
                GatewayService.start(context)
            }
            saveMsg = "已保存 ✓"
        } catch (e: Exception) {
            saveMsg = "保存失败: ${e.message}"
        }
        scope.launch { kotlinx.coroutines.delay(2500); saveMsg = null }
    }

    fun testConnection() {
        testing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                    val key = apiKey.trim()
                    val url = baseUrl.trim().trimEnd('/')
                    if (key.isEmpty()) return@withContext "未填写 API Key"
                    val req = Request.Builder()
                        .url("$url/models")
                        .header("Authorization", "Bearer $key")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) "连接成功 ✓（${resp.code}）"
                        else "请求失败（${resp.code}）：${resp.body?.string()?.take(120)}"
                    }
                } catch (e: Exception) {
                    "无法连接: ${e.message?.take(100)}"
                }
            }
            saveMsg = result
            testing = false
            scope.launch { kotlinx.coroutines.delay(4000); saveMsg = null }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ClawTopBar("设置", "模型 · 网关 · 运行时") {}

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Section("🧠 模型配置")

            // provider selector
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("deepseek" to "DeepSeek", "openai" to "OpenAI", "custom" to "自定义").forEach { (id, label) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (provider == id) Brush.linearGradient(listOf(Purple.copy(alpha = 0.25f), PurpleDeep.copy(alpha = 0.15f))) else SolidColor(Bg3))
                            .clickable { provider = id }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(label, color = if (provider == id) Purple else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LabeledField("API Key", apiKey, { apiKey = it }, "sk-…", password = true)
            LabeledField("模型名", modelName, { modelName = it }, "deepseek-chat")
            LabeledField(
                "Base URL",
                baseUrl,
                { baseUrl = it },
                if (provider == "deepseek") "https://api.deepseek.com" else "https://api.openai.com/v1",
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                ActionBtn("💾 保存", Purple) { saveSettings() }
                ActionBtn(if (testing) "测试中…" else "🔌 测试连接", Pink) { if (!testing) testConnection() }
            }
            saveMsg?.let {
                Text(it, color = if (it.startsWith("保存失败") || it.startsWith("无法连接") || it.startsWith("请求失败")) Warn else Green, fontSize = 10.sp)
            }
            Text("保存后若网关正在运行会自动重启生效", color = TextDim, fontSize = 8.5.sp)

            Section("🛰 网关")
            var lanAccess by remember { mutableStateOf(prefs.lanAccess) }
            var pairRequire by remember { mutableStateOf(prefs.pairRequire) }
            val gwRunning = GatewayService.running

            InfoRow("🌐", "端口", "${prefs.gatewayPort} · 127.0.0.1", right = {
                Text(if (gwRunning) "● 运行中" else "已停止", color = if (gwRunning) Green else Red, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            })
            ToggleRow("📶", "局域网访问", "同一 WiFi 下其他设备可连接（改后重启网关）", lanAccess) {
                lanAccess = it
                prefs.lanAccess = it
                ConfigManager.writeConfig()
                if (GatewayService.running) {
                    GatewayService.stop(context)
                    GatewayService.start(context)
                }
            }
            ToggleRow("🔐", "配对授权", "新设备连接需手动批准（预留）", pairRequire) {
                pairRequire = it
                prefs.pairRequire = it
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBtn("▶ 启动网关", Purple) { GatewayService.start(context) }
                ActionBtn("⏹ 停止网关", Bg3Color) { GatewayService.stop(context) }
                ActionBtn("📄 网关日志", Bg3Color) { showLogs = true }
            }

            Section("📦 运行时")
            val runtimeOk = RuntimeManager.isInstalled()
            val runtimeVer = remember {
                runCatching {
                    val mf = java.io.File(RuntimeManager.rootDir, "manifest.json")
                    if (mf.exists()) {
                        val raw = mf.readText()
                        val node = Regex("\"node\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                        val oc = Regex("\"openclaw\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                        listOfNotNull(node?.let { "Node $it" }, oc?.let { "OpenClaw $it" }).joinToString(" · ")
                    } else "未知"
                }.getOrDefault("未知")
            }
            val diskMb = remember { RuntimeManager.diskUsageMb() }
            InfoRow("🟢", "Node.js 运行时", if (runtimeOk) "已安装 · ${RuntimeManager.deviceAbi() ?: "arm64"}" else "未安装", right = {
                if (runtimeOk) {
                    Text("重新导入", color = Purple, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bg3)
                        .clickable { filePicker.launch(arrayOf("*/*")) }
                        .padding(horizontal = 10.dp, vertical = 6.dp))
                } else {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Purple, Pink)))
                            .clickable { filePicker.launch(arrayOf("*/*")) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("导入运行时", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            })
            InfoRow("🧩", "运行时版本", runtimeVer)
            val hasDs = java.io.File(RuntimeManager.rootDir, "openclaw/node_modules/openclaw/dist/extensions/deepseek/openclaw.plugin.json").exists()
            InfoRow("🦞", "DeepSeek 插件", if (hasDs) "已内置 ✓（离线可用）" else "缺失！请重新导入最新 runtime 包", right = {
                if (!hasDs) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Purple, Pink)))
                            .clickable { filePicker.launch(arrayOf("*/*")) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("重新导入", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            })
            InfoRow("💾", "占用空间", "运行时 + 数据 ${diskMb} MB")

            Section("⚡ 启动与保活")
            var bootStart by remember { mutableStateOf(prefs.bootStart) }
            ToggleRow("⚡", "开机自启", "手机重启后自动拉起 Gateway", bootStart) { bootStart = it; prefs.bootStart = it }

            Section("关于")
            InfoRow("🦞", "ClawBox v1.0.0", "手机版 OpenClaw 主机 · 完整版")
            InfoRow(
                "🩹", "崩溃日志", "导入/启动出问题时可查看",
                right = {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg3)
                            .clickable { showCrash = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("查看", color = Purple, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                },
            )
        }
    }

    if (showLogs) {
        val lines = remember { GatewayProcess.logTail(300) }
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("📄 网关日志") },
            text = {
                Column {
                    Text("最近 ${lines.size} 行", color = TextMuted, fontSize = 9.sp)
                    Text(
                        lines.joinToString("\n"),
                        color = TextMuted, fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 26,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showLogs = false }) { Text("关闭") } },
        )
    }

    if (showCrash) {
        val log = remember { com.lobster.clawbox.CrashLog.read() }
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("🩹 崩溃日志") },
            text = {
                Text(
                    log,
                    color = TextMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 20,
                )
            },
            confirmButton = { TextButton(onClick = { showCrash = false }) { Text("关闭") } },
        )
    }

    if (installing) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("🦞 安装运行时") },
            text = {
                Column {
                    Text(installStage.ifEmpty { "准备中…" }, color = TextMuted, fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { installProgress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text("$installProgress%", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                if (installError != null || installStage.contains("完成")) {
                    TextButton(onClick = { installing = false }) { Text("关闭") }
                }
            },
        )
    }

    installError?.let { err ->
        AlertDialog(
            onDismissRequest = { installError = null },
            title = { Text("安装失败") },
            text = { Text(err, color = Warn, fontSize = 12.sp) },
            confirmButton = { TextButton(onClick = { installError = null }) { Text("知道了") } },
        )
    }
}

private val Bg3Color = Color(0xFF1B2130)

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 7.dp),
    )
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit, placeholder: String, password: Boolean = false) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Bg2)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    inner()
                },
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ActionBtn(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SolidColor(if (color == Bg3Color) color else color.copy(alpha = 0.9f)))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(text, color = if (color == Bg3Color) TextPrimary else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope_Icon(icon: String) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Brush.linearGradient(listOf(Purple.copy(alpha = 0.16f), Pink.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 14.sp) }
}

@Composable
private fun InfoRow(
    icon: String, title: String, desc: String,
    right: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Bg2)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowScope_Icon(icon)
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = TextMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
        if (right != null) {
            right()
        }
    }
}

@Composable
private fun ToggleRow(
    icon: String, title: String, desc: String, value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Bg2)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowScope_Icon(icon)
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = TextMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
        Toggle(value, onChange)
    }
}

@Composable
fun Toggle(value: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 38.dp, height = 21.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (value) Brush.linearGradient(listOf(Purple, com.lobster.clawbox.ui.theme.Violet)) else SolidColor(Bg3))
            .clickable { onChange(!value) }
            .padding(2.5.dp),
        contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
    }
}
