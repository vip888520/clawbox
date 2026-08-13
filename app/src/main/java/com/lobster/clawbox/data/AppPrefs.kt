package com.lobster.clawbox.data

import android.content.Context
import android.content.SharedPreferences

/**
 * ClawBox settings/preferences store (v1.0 real config).
 */
class AppPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("clawbox", Context.MODE_PRIVATE)

    // ── 初始化 ──────────────────────────────────────────────
    var initialized: Boolean
        get() = sp.getBoolean("initialized", false)
        set(v) = sp.edit().putBoolean("initialized", v).apply()

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()

    // ── 网关 ───────────────────────────────────────────────
    var gatewayEnabled: Boolean
        get() = sp.getBoolean("gateway_enabled", false)
        set(v) = sp.edit().putBoolean("gateway_enabled", v).apply()

    var bootStart: Boolean
        get() = sp.getBoolean("boot_start", true)
        set(v) = sp.edit().putBoolean("boot_start", v).apply()

    var lanAccess: Boolean
        get() = sp.getBoolean("lan_access", false)
        set(v) = sp.edit().putBoolean("lan_access", v).apply()

    var pairRequire: Boolean
        get() = sp.getBoolean("pair_require", true)
        set(v) = sp.edit().putBoolean("pair_require", v).apply()

    var runtimeInstalled: Boolean
        get() = sp.getBoolean("runtime_installed", false)
        set(v) = sp.edit().putBoolean("runtime_installed", v).apply()

    var gatewayPort: Int
        get() = sp.getInt("gateway_port", 18789)
        set(v) = sp.edit().putInt("gateway_port", v).apply()

    /** Gateway shared-secret token, generated on first run. */
    var gatewayToken: String
        get() = sp.getString("gateway_token", "") ?: ""
        set(v) = sp.edit().putString("gateway_token", v).apply()

    /** Last chat session key used. */
    var sessionKey: String
        get() = sp.getString("session_key", "main") ?: "main"
        set(v) = sp.edit().putString("session_key", v).apply()

    // ── 模型 ───────────────────────────────────────────────
    var modelProvider: String
        get() = sp.getString("model_provider", "deepseek") ?: "deepseek"
        set(v) = sp.edit().putString("model_provider", v).apply()

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v).apply()

    /** Model id, e.g. deepseek-chat / deepseek-v4-flash. */
    var modelName: String
        get() = sp.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
        set(v) = sp.edit().putString("model_name", v).apply()

    /** OpenAI-compatible base URL. */
    var baseUrl: String
        get() = sp.getString("base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        set(v) = sp.edit().putString("base_url", v).apply()
}
