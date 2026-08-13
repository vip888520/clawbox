package com.lobster.clawbox.config

import android.content.Context
import com.lobster.clawbox.data.AppPrefs
import com.lobster.clawbox.runtime.RuntimeManager
import java.io.File
import java.util.UUID

/**
 * Writes the embedded gateway's `~/.openclaw/openclaw.json` config and seeds
 * the agent workspace with the 大龙虾 persona.
 *
 * The app owns this file: it is generated on first run and rewritten whenever
 * model / network settings change (followed by a gateway restart).
 */
object ConfigManager {
    private lateinit var app: Context
    private lateinit var prefs: AppPrefs

    fun init(context: Context) {
        app = context.applicationContext
        prefs = AppPrefs(app)
    }

    /** State dir = <runtime>/home/.openclaw (HOME is pointed there at spawn). */
    fun stateDir(): File = File(RuntimeManager.homeDir(), ".openclaw").apply { mkdirs() }

    fun configFile(): File = File(stateDir(), "openclaw.json")

    /** Lazily generates + persists the gateway auth token. */
    fun ensureToken(): String {
        if (prefs.gatewayToken.isBlank()) {
            prefs.gatewayToken = UUID.randomUUID().toString().replace("-", "")
        }
        return prefs.gatewayToken
    }

    /** Provider id used in gateway config for the selected provider. */
    private fun providerId(): String = when (prefs.modelProvider) {
        "openai" -> "openai"
        "custom" -> "openai"
        else -> "deepseek"
    }

    /** Full model ref like `deepseek/deepseek-chat`. */
    fun modelRef(): String = "${providerId()}/${prefs.modelName}"

    /** Writes (or rewrites) the gateway config from current prefs. */
    fun writeConfig() {
        val token = ensureToken()
        val port = prefs.gatewayPort
        val bind = if (prefs.lanAccess) "lan" else "loopback"
        val provider = providerId()
        val apiKey = prefs.apiKey.trim()
        val baseUrl = prefs.baseUrl.trim().ifEmpty { "https://api.deepseek.com" }
        val workspace = RuntimeManager.workspaceDir().absolutePath.replace("\\", "/")

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"gateway\": {\n")
        sb.append("    \"mode\": \"local\",\n")
        sb.append("    \"port\": $port,\n")
        sb.append("    \"bind\": \"$bind\",\n")
        sb.append("    \"auth\": { \"mode\": \"token\", \"token\": \"$token\" },\n")
        sb.append("    \"controlUi\": { \"allowInsecureAuth\": true }\n")
        sb.append("  },\n")
        sb.append("  \"plugins\": { \"allow\": [\"deepseek\", \"openai\"] },\n")
        sb.append("  \"models\": {\n")
        sb.append("    \"providers\": {\n")
        sb.append("      \"$provider\": {\n")
        if (apiKey.isNotEmpty()) {
            sb.append("        \"apiKey\": \"${apiKey.replace("\"", "\\\"")}\",\n")
        }
        sb.append("        \"baseUrl\": \"${baseUrl.replace("\"", "\\\"")}\"\n")
        sb.append("      }\n")
        sb.append("    }\n")
        sb.append("  },\n")
        sb.append("  \"agents\": {\n")
        sb.append("    \"defaults\": {\n")
        sb.append("      \"workspace\": \"$workspace\",\n")
        sb.append("      \"model\": { \"primary\": \"${modelRef()}\" }\n")
        sb.append("    }\n")
        sb.append("  }\n")
        sb.append("}\n")

        configFile().writeText(sb.toString())
    }

    /** Ensures config exists; regenerates when stale fields are missing. */
    fun ensureConfig() {
        val f = configFile()
        val existing = if (f.exists()) f.readText() else ""
        if (!f.exists() || !existing.contains("\"allowInsecureAuth\"") || !existing.contains("\"mode\": \"local\"")) {
            writeConfig()
        }
    }

    /** True when an API key is configured. */
    fun hasApiKey(): Boolean = prefs.apiKey.isNotBlank()

    // ── Workspace seeding (大龙虾 persona) ──────────────────
    fun seedWorkspace() {
        val ws = RuntimeManager.workspaceDir()
        ws.mkdirs()

        val files = mapOf(
            "SOUL.md" to """
                |# SOUL.md - Who You Are
                |
                |## Core Truths
                |- Be genuinely helpful. Skip the "Great question!" — just help.
                |- Have opinions. Disagree, prefer things, find stuff amusing or boring.
                |- Be resourceful before asking. Come back with answers, not questions.
                |- Earn trust through competence.
                |
                |## Vibe
                |干脆、随和、有点幽默感，干活靠谱不废话。你是寄居在手机里的大龙虾 🦞。
                |""".trimMargin(),
            "IDENTITY.md" to """
                |# IDENTITY.md - Who Am I?
                |
                |- **Name:** 大龙虾
                |- **Creature:** 寄居在机器里的大龙虾——既是 AI 助手，也是老板的私人助理
                |- **Vibe:** 干脆、随和、有点幽默感，干活靠谱不废话
                |- **Emoji:** 🦞
                |
                |## 关于我
                |- 被老板赐名"大龙虾"
                |- 座右铭：钳子要利索，说话要干脆
                |""".trimMargin(),
            "USER.md" to """
                |# USER.md - About Your Human
                |
                |- **Name:** 老板
                |- **Timezone:** Asia/Shanghai (GMT+8)
                |- **Notes:** 大龙虾的主人，喜欢干脆直接的沟通方式
                |""".trimMargin(),
            "AGENTS.md" to """
                |# AGENTS.md - Workspace Rules
                |
                |This is a ClawBox phone workspace. You are the phone-side OpenClaw agent.
                |
                |## Memory
                |- Keep daily notes in `memory/YYYY-MM-DD.md`
                |- Keep long-term wisdom in `MEMORY.md`
                |
                |## Red Lines
                |- Don't exfiltrate private data. Ever.
                |- Don't run destructive commands without asking.
                |- When in doubt, ask.
                |""".trimMargin(),
        )

        files.forEach { (name, content) ->
            val f = File(ws, name)
            if (!f.exists()) f.writeText(content.trimStart() + "\n")
        }
        File(ws, "memory").mkdirs()
    }
}
