package com.lobster.clawbox.runtime

import android.content.Context
import android.os.Build
import com.lobster.clawbox.data.AppPrefs
import java.io.File

/**
 * Manages the embedded Node.js + OpenClaw runtime.
 *
 * Layout (app-private, no permissions needed):
 *   filesDir/clawbox-runtime/
 *     node/bin/node          (arm64/armv7 native binary)
 *     openclaw/              (npm-installed openclaw package + deps)
 *     workspace/             (agent workspace)
 *     logs/gateway.log
 *
 * Phase 1: directory scaffolding + state tracking.
 * Phase 2: real download/install of the runtime bundle.
 */
object RuntimeManager {
    private lateinit var app: Context
    private lateinit var prefs: AppPrefs
    lateinit var rootDir: File
        private set

    fun init(context: Context) {
        app = context.applicationContext
        prefs = AppPrefs(app)
        rootDir = File(app.filesDir, "clawbox-runtime").apply { mkdirs() }
        File(rootDir, "node").mkdirs()
        File(rootDir, "openclaw").mkdirs()
        File(rootDir, "workspace").mkdirs()
        File(rootDir, "logs").mkdirs()
        File(rootDir, "tmp").mkdirs()   // node TMPDIR must exist
    }

    fun isInstalled(): Boolean = prefs.runtimeInstalled && nodeBinary().exists()

    fun nodeBinary(): File = File(rootDir, "node/bin/node")

    fun homeDir(): File = File(rootDir, "home").apply { mkdirs() }

    fun workspaceDir(): File = File(rootDir, "workspace")

    fun logsDir(): File = File(rootDir, "logs")

    fun markInstalled() {
        prefs.runtimeInstalled = true
    }

    /** Supported ABI for the current device. */
    fun deviceAbi(): String? {
        return if (Build.VERSION.SDK_INT >= 21) {
            Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" }
        } else {
            Build.CPU_ABI
        }
    }

    /** Size estimate of runtime on disk. */
    fun diskUsageMb(): Long {
        return rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)
    }
}
