package com.lobster.clawbox.runtime

import java.io.File

/**
 * Runs `openclaw ...` CLI commands through the embedded runtime
 * (glibc loader + node), streaming output line by line.
 *
 * Used by the Terminal screen so commands execute against the REAL runtime.
 */
object RuntimeExec {

    data class Handle(val process: Process) {
        fun stop() {
            try {
                process.destroy()
                if (!process.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Run a CLI command. `args` are passed to openclaw CLI, e.g. ["status"].
     * Returns a handle; lines are streamed to onLine.
     */
    fun run(
        args: List<String>,
        onLine: (String) -> Unit,
        onExit: (Int) -> Unit,
        env: Map<String, String> = emptyMap(),
    ): Handle {
        val root = RuntimeManager.rootDir
        val ld = File(root, "glibc/lib/ld-linux-aarch64.so.1")
        val node = RuntimeManager.nodeBinary()
        val compat = File(root, "patches/glibc-compat.js")
        val cli = File(root, "openclaw/node_modules/openclaw/openclaw.mjs")

        require(ld.exists() && node.exists() && cli.exists()) { "运行时未安装" }

        val cmd = mutableListOf(
            ld.absolutePath,
            "--library-path", File(root, "glibc/lib").absolutePath,
            node.absolutePath,
            "--require", compat.absolutePath,
            cli.absolutePath,
        )
        cmd.addAll(args)

        val pb = ProcessBuilder(cmd)
        val e = pb.environment()
        e["HOME"] = RuntimeManager.homeDir().absolutePath
        e["TMPDIR"] = File(root, "tmp").absolutePath
        e["LD_LIBRARY_PATH"] = File(root, "glibc/lib").absolutePath
        e["PATH"] = "/system/bin:/vendor/bin:/system/xbin"
        e["NO_COLOR"] = "1"
        e["OPENCLAW_NO_COLOR"] = "1"
        e["GLIBC_TUNABLES"] = "glibc.pthread.rseq=0"
        env.forEach { (k, v) -> e[k] = v }

        pb.redirectErrorStream(true)
        val proc = pb.start()

        Thread {
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { onLine(it) }
                }
            } catch (_: Exception) {
            }
            try {
                onExit(proc.exitValue())
            } catch (_: Exception) {
                onExit(-1)
            }
        }.apply { isDaemon = true; name = "clawbox-exec"; start() }

        return Handle(proc)
    }

    /** One-shot run, returns all output (blocking). For quick queries. */
    fun runSync(args: List<String>, timeoutMs: Long = 30_000): Pair<Int, String> {
        val sb = StringBuilder()
        val latch = java.util.concurrent.CountDownLatch(1)
        var code = -1
        val h = run(args, onLine = { sb.appendLine(it) }, onExit = { code = it; latch.countDown() })
        latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        h.stop()
        return code to sb.toString().trim()
    }
}
