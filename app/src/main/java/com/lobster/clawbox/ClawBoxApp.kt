package com.lobster.clawbox

import android.app.Application
import com.lobster.clawbox.config.ConfigManager
import com.lobster.clawbox.gateway.GatewayApi
import com.lobster.clawbox.runtime.RuntimeManager

class ClawBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        RuntimeManager.init(this)
        ConfigManager.init(this)
        GatewayApi.init(this)
        // seed the 大龙虾 persona workspace once
        try {
            ConfigManager.seedWorkspace()
        } catch (_: Exception) {
        }
    }
}
