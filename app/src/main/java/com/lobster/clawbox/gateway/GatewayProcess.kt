package com.lobster.clawbox.gateway

import android.util.Log
import com.lobster.clawbox.config.ConfigManager
import com.lobster.clawbox.runtime.RuntimeManager
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

/**
 * Spawns and manages the embedded OpenClaw gateway process.
 *
 * Executes via glibc loader:
 *   ld-linux-aarch64.so.1 --library-path <bundle>/glibc/lib \
 *     <bundle>/node/bin/node --require <bundle>/patches/glibc-compat.js \
 *     <bundle>/openclaw/node_modules/openclaw/bin/openclaw.js gateway
 */
object GatewayProcess {

    private const val TAG = "ClawBox-GW"

    @Volatile
    var process: Process? = null
        private set

    val isRunning: Boolean
        get() = process?.isAlive == true

    /** Log tail cache (last N lines) for the UI. */
    private val logLines = ArrayDeque<String>()
    private val logLock = Any()

    fun logTail(max: Int = 200): List<String> = synchronized(logLock) {
        logLines.toList().takeLast(max)
    }

    fun start(port: Int): Process {
        stop()
        Log.d(TAG, "start(port=$port) root=${RuntimeManager.rootDir}")
        // ensure the gateway config (auth token / model / workspace) exists
        ConfigManager.ensureConfig()
        val root = RuntimeManager.rootDir
        val ld = File(root, "glibc/lib/ld-linux-aarch64.so.1")
        val node = RuntimeManager.nodeBinary()
        val compat = File(root, "patches/glibc-compat.js")
        val cli = File(root, "openclaw/node_modules/openclaw/openclaw.mjs")

        require(ld.exists()) { "运行时未安装或损坏（缺少 ld.so）" }
        require(node.exists()) { "运行时未安装或损坏（缺少 node）" }
        require(cli.exists()) { "运行时未安装或损坏（缺少 openclaw）" }
        Log.d(TAG, "自检: ld=${ld.exists()} node=${node.exists()} cli=${cli.exists()}")

        // v1.0.2: fail fast when the runtime bundle is too old (missing the
        // pre-bundled DeepSeek provider). Without it the gateway would try to
        // npm-install the plugin over the network and hang on startup.
        val dsPlugin = File(root, "openclaw/node_modules/openclaw/dist/extensions/deepseek/openclaw.plugin.json")
        Log.d(TAG, "自检: deepseek-plugin=${dsPlugin.exists()} libstdc++=${File(root, "glibc/lib/libstdc++.so.6").exists()} libgcc=${File(root, "glibc/lib/libgcc_s.so.1").exists()}")
        if (!dsPlugin.exists()) {
            throw IllegalStateException("运行时版本过旧（缺少 DeepSeek 插件），请到 设置 → 运行时 → 重新导入 最新的 runtime-arm64.tar.xz")
        }

        // v1.0.2: verify essential shared libs (node crashes without libstdc++)
        val libStdCpp = File(root, "glibc/lib/libstdc++.so.6")
        val libGcc = File(root, "glibc/lib/libgcc_s.so.1")
        if (!libStdCpp.exists() || !libGcc.exists()) {
            throw IllegalStateException("运行时库不完整（缺 libstdc++/libgcc_s），请到 设置 → 运行时 → 重新导入 最新的 runtime-arm64.tar.xz")
        }

        // v1.0.8: prefer bionic node (Termux-style, direct exec via
        // /system/bin/linker64). glibc node is killed by Android app seccomp
        // (SIGSYS exit 159) on untrusted_app_27 domain on Android 16/MIUI.
        val bionicNode = File(root, "bionic/bin/node")
        val useBionic = bionicNode.exists()
        Log.d(TAG, "exec mode: ${if (useBionic) "bionic" else "glibc"}")
        val pb: ProcessBuilder
        if (useBionic) {
            pb = ProcessBuilder(
                bionicNode.absolutePath,
                cli.absolutePath,
                "gateway",
                "--port", port.toString(),
            )
        } else {
            pb = ProcessBuilder(
                ld.absolutePath,
                "--library-path", File(root, "glibc/lib").absolutePath,
                node.absolutePath,
                "--require", compat.absolutePath,
                cli.absolutePath,
                "gateway",
                "--port", port.toString(),
            )
        }

        val env = pb.environment()
        env["HOME"] = RuntimeManager.homeDir().absolutePath
        env["TMPDIR"] = File(root, "tmp").absolutePath
        env["LD_LIBRARY_PATH"] = if (useBionic) File(root, "bionic/lib").absolutePath else File(root, "glibc/lib").absolutePath
        env["PATH"] = "/system/bin:/vendor/bin:/system/xbin"
        env["CLAWBOX_NODE_WRAPPER"] = node.absolutePath
        env["CLAWBOX_HOSTNAME"] = "clawbox-phone"
        env["NO_COLOR"] = "1"
        env["OPENCLAW_NO_COLOR"] = "1"
        if (useBionic) {
            // Termux-built node/openssl default to the Termux config path
            // which this app cannot read -> point at our own minimal conf.
            val osslConf = File(root, "bionic/etc/openssl.cnf")
            if (osslConf.exists()) env["OPENSSL_CONF"] = osslConf.absolutePath
        } else {
            // Android app seccomp may kill rseq registration (SIGSYS 159);
            // Ubuntu glibc 2.35 registers rseq per-thread by default.
            env["GLIBC_TUNABLES"] = "glibc.pthread.rseq=0"
        }

        pb.redirectErrorStream(true)
        val logFile = File(RuntimeManager.logsDir(), "gateway.log")

        val proc = pb.start()
        process = proc
        Log.d(TAG, "node 进程已启动 (pid via hash: ${proc.hashCode()})")
        // background log tailer: tee stream to file + memory cache
        Thread {
            try {
                FileOutputStream(logFile, false).use { fos ->
                    val writer = PrintWriter(fos, true)
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            if (idx < 40) Log.d(TAG, "node> $line")
                            synchronized(logLock) {
                                logLines.addLast(line)
                                while (logLines.size > 500) logLines.removeFirst()
                            }
                            writer.println(line)
                        }
                    }
                    writer.flush()
                }
            } catch (_: Exception) {
            }
            if (process === proc) {
                Log.d(TAG, "node 进程退出 exitCode=${proc.exitValue()}")
            }
        }.apply { isDaemon = true; name = "clawbox-logtail"; start() }

        return proc
    }

    fun stop() {
        val p = process ?: return
        process = null
        try {
            p.destroy()
            if (!p.waitFor(3000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                p.destroyForcibly()
            }
        } catch (_: Exception) {
        }
    }
}
