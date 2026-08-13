package com.lobster.clawbox

import android.content.Context
import com.lobster.clawbox.runtime.RuntimeManager
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash catcher: writes stack traces to logs/crash.log so field
 * issues can be diagnosed without a debugger.
 */
object CrashLog {

    private var prevHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(context, thread, throwable)
            } catch (_: Exception) {
            }
            prevHandler?.uncaughtException(thread, throwable)
        }
    }

    fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = RuntimeManager.logsDir()
        dir.mkdirs()
        val file = File(dir, "crash.log")
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        file.appendText(
            "===== $ts | thread=${thread.name} =====\n" +
                "${throwable.javaClass.name}: ${throwable.message}\n" +
                "$sw\n",
        )
    }

    fun read(): String {
        val f = File(RuntimeManager.logsDir(), "crash.log")
        return if (f.exists()) f.readText() else "(无崩溃记录)"
    }
}
