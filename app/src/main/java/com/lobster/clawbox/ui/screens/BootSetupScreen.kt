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
import androidx.compose.foundation.layout.height
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
import com.lobster.clawbox.gateway.GatewayService
import com.lobster.clawbox.runtime.RuntimeInstaller
import com.lobster.clawbox.runtime.RuntimeManager
import com.lobster.clawbox.ui.ClawLogo
import com.lobster.clawbox.ui.theme.Bg
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

/**
 * First-run setup (real):
 *   1. 检查设备兼容性
 *   2. 导入 Node.js 运行时（文件选择）
 *   3. 配置模型 API Key
 *   4. 完成 → 启动网关
 */
@Composable
fun BootSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val main = android.os.Handler(android.os.Looper.getMainLooper())

    var step by remember { mutableIntStateOf(1) }   // 1 compat, 2 runtime, 3 model, 4 done
    var compatOk by remember { mutableStateOf(true) }
    var compatMsg by remember { mutableStateOf("") }

    var installing by remember { mutableStateOf(false) }
    var installProgress by remember { mutableIntStateOf(0) }
    var installStage by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var starting by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            installing = true
            installProgress = 0
            installError = null
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        RuntimeInstaller(context).installFromFile(uri) { p ->
                            main.post {
                                installProgress = p.percent
                                installStage = p.stage
                            }
                        }
                        main.post {
                            installStage = "安装完成 ✓"
                            step = 3
                        }
                    } catch (e: Exception) {
                        main.post { installError = e.message ?: "安装失败" }
                    } finally {
                        main.post { installing = false }
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(48.dp))
        ClawLogo(size = 64)
        Text("ClawBox", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp))
        Text("手机版 OpenClaw 主机 · 首次启动", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

        Column(Modifier.fillMaxWidth().padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupStep(1, step, "检查设备兼容性（Android 8.0+ / arm64）")
            SetupStep(2, step, "导入 Node.js 运行时（runtime-arm64.tar.xz）")
            SetupStep(3, step, "配置模型 API Key")
            SetupStep(4, step, "完成 · 启动网关")
        }

        Box(Modifier.height(22.dp))

        when {
            step == 1 -> {
                Text("正在检查…", color = TextMuted, fontSize = 11.sp)
                Box(Modifier.height(10.dp))
                ActionBtn("重新检测", Purple) {
                    val sdk = android.os.Build.VERSION.SDK_INT
                    val abiOk = listOf("arm64-v8a", "armeabi-v7a").any { android.os.Build.SUPPORTED_ABIS.contains(it) }
                    compatOk = sdk >= 26 && abiOk
                    compatMsg = when {
                        sdk < 26 -> "需要 Android 8.0+（当前 ${android.os.Build.VERSION.RELEASE}）"
                        !abiOk -> "需要 arm64 处理器"
                        else -> ""
                    }
                    if (compatOk) step = 2
                }
                if (compatMsg.isNotEmpty()) {
                    Text(compatMsg, color = Red, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            step == 2 -> {
                if (RuntimeManager.isInstalled()) {
                    Text("✓ 运行时已安装，可直接继续", color = Green, fontSize = 11.sp)
                    Box(Modifier.height(10.dp))
                    ActionBtn("下一步 →", Purple) { step = 3 }
                    ActionBtn("重新导入", Bg3) { filePicker.launch(arrayOf("*/*")) }
                } else {
                    Text("选择之前下载的 runtime-arm64.tar.xz 文件", color = TextMuted, fontSize = 11.sp)
                    Text("（把该文件传到手机 Download 目录，或通过分享/文件管理器选择）", color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                    Box(Modifier.height(10.dp))
                    ActionBtn("📂 选择运行时文件", Purple) { filePicker.launch(arrayOf("*/*")) }
                    installError?.let {
                        Text(it, color = Warn, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            step == 3 -> {
                Text("填 DeepSeek（或 OpenAI 兼容）API Key", color = TextMuted, fontSize = 11.sp)
                Box(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text("API Key", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg3)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (apiKey.isEmpty()) Text("sk-…", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                inner()
                            },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                }
                Text("没有 Key？去 platform.deepseek.com 注册获取", color = TextDim, fontSize = 8.5.sp, modifier = Modifier.padding(top = 6.dp))
                Box(Modifier.height(12.dp))
                ActionBtn("下一步 →", Purple) {
                    prefs.apiKey = apiKey.trim()
                    ConfigManager.writeConfig()
                    step = 4
                }
            }
            step == 4 -> {
                Text("全部就绪 🦞", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("将启动本地网关（首次启动会自动安装 DeepSeek 插件，需联网，约 10-30 秒）", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                Box(Modifier.height(14.dp))
                if (starting) {
                    Text("正在启动网关…", color = Purple, fontSize = 11.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                } else {
                    ActionBtn("🚀 启动网关", Purple) {
                        starting = true
                        scope.launch {
                            ConfigManager.seedWorkspace()
                            GatewayService.start(context)
                            kotlinx.coroutines.delay(1500)
                            prefs.setupDone = true
                            onDone()
                        }
                    }
                }
            }
        }

        Box(Modifier.height(40.dp))
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
}

@Composable
private fun SetupStep(no: Int, current: Int, text: String) {
    val done = current > no
    val run = current == no
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        done -> Green.copy(alpha = 0.16f)
                        run -> Purple.copy(alpha = 0.14f)
                        else -> Bg3
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else "$no",
                color = when { done -> Green; run -> Purple; else -> TextMuted },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text,
            color = when { done -> Green; run -> TextPrimary; else -> TextMuted },
            fontSize = 11.5.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ActionBtn(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (color == Bg3) SolidColor(color) else Brush.linearGradient(listOf(Purple, PurpleDeep)))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (color == Bg3) TextMuted else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
