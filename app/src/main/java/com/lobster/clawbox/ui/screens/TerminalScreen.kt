package com.lobster.clawbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lobster.clawbox.runtime.RuntimeExec
import com.lobster.clawbox.ui.ClawTopBar
import com.lobster.clawbox.ui.theme.Bg3
import com.lobster.clawbox.ui.theme.Green
import com.lobster.clawbox.ui.theme.Red
import com.lobster.clawbox.ui.theme.TextMuted
import com.lobster.clawbox.ui.theme.Warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TermBg = Color(0xFF06090D)

private data class TermLine(val text: String, val color: Color = Color(0xFFA8C7A9))

@Composable
fun TerminalScreen() {
    val scroll = rememberScrollState()
    var cmd by remember { mutableStateOf("") }
    var runningCmd by remember { mutableStateOf(false) }
    val lines = remember {
        mutableStateListOf(
            TermLine("clawbox@phone:~$ openclaw status", Green),
            TermLine("● 在这里输入 openclaw 命令，走真实运行时执行"),
            TermLine("  例如: status / agent -m \"你好\" / skills list"),
            TermLine(""),
        )
    }

    val scope = rememberCoroutineScope()
    val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun execute(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || runningCmd) return
        cmd = ""
        lines.add(TermLine("clawbox@phone:~$ $trimmed", Green))
        lines.add(TermLine("…运行中", Warn))
        runningCmd = true
        scope.launch(Dispatchers.IO) {
            try {
                RuntimeExec.run(
                    args = trimmed.split(" ").filter { it.isNotBlank() },
                    onLine = { l ->
                        if (l.isNotBlank()) {
                            main.post {
                                lines.removeAll { it.text == "…运行中" }
                                lines.add(TermLine(l, Color(0xFFB9C6A6)))
                            }
                        }
                    },
                    onExit = { _ ->
                        main.post {
                            lines.removeAll { it.text == "…运行中" }
                            lines.add(TermLine("", Color(0xFF3E4A44)))
                            runningCmd = false
                        }
                    },
                )
            } catch (e: Exception) {
                main.post {
                    lines.removeAll { it.text == "…运行中" }
                    lines.add(TermLine("错误: ${e.message}", Red))
                    lines.add(TermLine("", Color(0xFF3E4A44)))
                    runningCmd = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(TermBg)) {
        ClawTopBar("终端", "真实运行时 · openclaw CLI") {}

        // quick commands
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 13.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("status", "agents list", "skills list", "models list", "channels status").forEach { preset ->
                QuickChip(preset) { execute(preset) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            lines.forEach { l ->
                Text(
                    l.text,
                    color = l.color,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$", color = Green, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            BasicTextField(
                value = cmd,
                onValueChange = { cmd = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFC9E6CA), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                decorationBox = { inner ->
                    if (cmd.isEmpty()) Text("输入 openclaw 命令…", color = Color(0xFF3E4A44), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    inner()
                },
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Bg3)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .clickable { execute(cmd) },
            ) { Text("⏎", color = Color(0xFF9FD0A0), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF121829))
            .padding(horizontal = 11.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Text("openclaw $text", color = Color(0xFF93A1C9), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
    }
}
