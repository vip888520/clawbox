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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.lobster.clawbox.config.ConfigManager
import com.lobster.clawbox.gateway.GatewayApi
import com.lobster.clawbox.runtime.RuntimeManager
import com.lobster.clawbox.ui.ClawTopBar
import com.lobster.clawbox.ui.theme.Bg2
import com.lobster.clawbox.ui.theme.Bg3
import com.lobster.clawbox.ui.theme.Green
import com.lobster.clawbox.ui.theme.Purple
import com.lobster.clawbox.ui.theme.PurpleDeep
import com.lobster.clawbox.ui.theme.TextDim
import com.lobster.clawbox.ui.theme.TextMuted
import com.lobster.clawbox.ui.theme.TextPrimary
import com.lobster.clawbox.ui.theme.Warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Composable
fun ManageScreen() {
    var tab by remember { mutableStateOf(0) }
    val tabNames = listOf("💬 会话", "🧩 技能", "🧠 模型", "📁 文件")

    val sessions = remember { mutableStateListOf<String>() }
    val models = remember { mutableStateListOf<String>() }
    val files = remember { mutableStateListOf<Pair<String, String>>() } // name, size
    var skillsLoaded by remember { mutableStateOf(false) }
    val skills = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(tab, GatewayApi.connState.value) {
        when (tab) {
            0 -> {
                if (GatewayApi.connState.value != GatewayApi.ConnState.CONNECTED) return@LaunchedEffect
                loading = true
                sessions.clear()
                val list = GatewayApi.listSessions()
                list.take(30).forEach { s ->
                    val key = s["key"]?.jsonPrimitive?.contentOrNull
                    val label = s["label"]?.jsonPrimitive?.contentOrNull
                    val agent = s["agentId"]?.jsonPrimitive?.contentOrNull
                    if (key != null) sessions.add(if (label != null) "$key · $label" else key + (agent?.let { " · $it" } ?: ""))
                }
                if (sessions.isEmpty()) sessions.add("（暂无历史会话，先去聊几句）")
                loading = false
            }
            2 -> {
                if (GatewayApi.connState.value != GatewayApi.ConnState.CONNECTED) return@LaunchedEffect
                loading = true
                models.clear()
                val list = GatewayApi.listModels()
                list.take(30).forEach { m ->
                    val id = m["id"]?.jsonPrimitive?.contentOrNull
                    val name = m["name"]?.jsonPrimitive?.contentOrNull
                    if (id != null) models.add(name?.let { "$id · $it" } ?: id)
                }
                loading = false
            }
            3 -> {
                files.clear()
                val ws = RuntimeManager.workspaceDir()
                fun walk(dir: File, depth: Int) {
                    if (depth > 2) return
                    dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        if (f.isDirectory) {
                            val n = f.listFiles()?.size ?: 0
                            files.add(f.name + "/" to "$n 项")
                            walk(f, depth + 1)
                        } else {
                            files.add(f.name to formatSize(f.length()))
                        }
                    }
                }
                walk(ws, 0)
                if (files.isEmpty()) files.add("（工作区为空）" to "")
            }
        }
    }

    // bundled skills from the runtime package (real filesystem)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val skillsDir = File(RuntimeManager.rootDir, "openclaw/node_modules/openclaw/skills")
            val names = skillsDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
            skills.clear()
            skills.addAll(names)
            skillsLoaded = true
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ClawTopBar("管理", "会话 · 技能 · 模型 · 文件") {}

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            tabNames.forEachIndexed { i, name ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (i == tab) Brush.linearGradient(
                                listOf(Purple.copy(alpha = 0.22f), PurpleDeep.copy(alpha = 0.14f)),
                            ) else SolidColor(Bg3),
                        )
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                        .clickable { tab = i },
                ) {
                    Text(name, color = if (i == tab) Purple else TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 13.dp),
        ) {
            if (loading) {
                Text("加载中…", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            when (tab) {
                0 -> {
                    if (GatewayApi.connState.value != GatewayApi.ConnState.CONNECTED) {
                        HintCard("网关未运行，启动后这里会显示真实会话列表")
                    } else {
                        sessions.forEach { s -> SimpleCard(s, "会话") }
                    }
                }
                1 -> {
                    if (!skillsLoaded) {
                        Text("加载技能…", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 10.dp))
                    } else {
                        Text("运行时内置技能（共 ${skills.size} 个，位于运行时 skills 目录）", color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 8.dp))
                        skills.forEach { s ->
                            val desc = when (s) {
                                "weather" -> "天气查询与预报"
                                "notion" -> "Notion 页面与数据库操作"
                                "github" -> "GitHub CLI 封装"
                                "diagram-maker" -> "流程图 / 架构图生成"
                                "meme-maker" -> "表情包生成"
                                "skill-creator" -> "创建和管理技能"
                                else -> "OpenClaw 内置技能"
                            }
                            SimpleCard(s, desc)
                        }
                        Text("更多技能可在终端执行 openclaw skills install <名称> 安装", color = TextDim, fontSize = 8.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                    }
                }
                2 -> {
                    val modelRef = remember { ConfigManager.modelRef() }
                    val hasKey = remember { ConfigManager.hasApiKey() }
                    SimpleCard("当前主模型: $modelRef", if (hasKey) "API Key 已配置" else "未配置 API Key（设置页填写）")
                    if (GatewayApi.connState.value != GatewayApi.ConnState.CONNECTED) {
                        HintCard("网关未运行，无法拉取模型目录")
                    } else {
                        models.forEach { m -> SimpleCard(m, "可用模型") }
                    }
                }
                3 -> {
                    files.forEach { (n, d) -> SimpleCard(n, d) }
                }
            }
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Bg3)
            .padding(12.dp),
    ) {
        Text(text, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SimpleCard(title: String, desc: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Bg2)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Purple.copy(alpha = 0.16f), Green.copy(alpha = 0.10f)))),
            contentAlignment = Alignment.Center,
        ) { Text("▸", color = Purple, fontSize = 12.sp) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            if (desc.isNotBlank()) {
                Text(desc, color = TextMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024)
}
