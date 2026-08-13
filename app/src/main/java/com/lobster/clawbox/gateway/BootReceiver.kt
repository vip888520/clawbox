package com.lobster.clawbox.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lobster.clawbox.data.AppPrefs

/** Auto-starts the Gateway after device reboot when enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = AppPrefs(context)
        if (prefs.bootStart && prefs.runtimeInstalled) {
            GatewayService.start(context)
        }
    }
}
