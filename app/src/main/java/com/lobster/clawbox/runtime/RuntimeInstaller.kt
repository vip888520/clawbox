package com.lobster.clawbox.runtime

import android.content.Context
import android.net.Uri
import android.os.Build
import com.lobster.clawbox.data.AppPrefs
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Installs the embedded runtime bundle (node + glibc + openclaw).
 *
 * Bundle layout (tar.xz, arcname "runtime/"):
 *   runtime/node/bin/node
 *   runtime/glibc/lib/...
 *   runtime/openclaw/...
 *   runtime/patches/glibc-compat.js
 *   runtime/etc/hosts
 *   runtime/manifest.json
 */
class RuntimeInstaller(private val context: Context) {

    companion object {
        const val BUNDLE_ARCNAME = "runtime"
        const val DEFAULT_BUNDLE_URL =
            "https://github.com/lobster-claw/clawbox-runtime/releases/latest/download/runtime-arm64.tar.xz"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString(" ") { "%02x".format(it) }
        }
    }

    data class InstallProgress(val percent: Int, val stage: String)

    /** Install from a local file (SAF import or downloads dir). */
    fun installFromFile(uri: Uri, onProgress: (InstallProgress) -> Unit) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            installFromStream(input, onProgress)
        } ?: throw IllegalStateException("无法打开文件")
    }

    /** Install from a URL. */
    fun installFromUrl(url: String, onProgress: (InstallProgress) -> Unit) {
        val conn = java.net.URL(url).openConnection()
        conn.setRequestProperty("User-Agent", "ClawBox")
        conn.connect()
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            val counting = CountingInputStream(input) { read ->
                if (total > 0) onProgress(InstallProgress((read * 100 / total).toInt(), "下载运行时"))
            }
            installFromStream(counting, onProgress)
        }
    }

    /** Stream must be a .tar.xz archive with a single "runtime/" top dir. */
    private fun installFromStream(input: java.io.InputStream, onProgress: (InstallProgress) -> Unit) {
        val root = RuntimeManager.rootDir
        // wipe previous partial install
        listOf("node", "glibc", "openclaw", "patches", "etc", "tmp").forEach { sub ->
            File(root, sub).deleteRecursively()
        }
        onProgress(InstallProgress(5, "准备解压…"))

        val xz = XZCompressorInputStream(input)
        val tar = TarArchiveInputStream(xz)
        var count = 0
        var entry = tar.nextEntry
        while (entry != null) {
            val name = entry.name.removePrefix("$BUNDLE_ARCNAME/")
            if (name.isBlank() || entry.isDirectory) {
                entry = tar.nextEntry
                continue
            }
            val target = File(root, name)
            // safety: prevent path traversal
            if (!target.canonicalPath.startsWith(root.canonicalPath)) {
                entry = tar.nextEntry
                continue
            }
            target.parentFile?.mkdirs()
            if (entry.isSymbolicLink) {
                // materialize symlink target as regular file where possible;
                // for ld.so → libc.so.6 we just copy libc
                val linkDest = entry.linkName.removePrefix("$BUNDLE_ARCNAME/")
                val resolved = File(root, linkDest)
                if (resolved.exists() && resolved.isFile) {
                    resolved.copyTo(target, overwrite = true)
                } else {
                    target.writeBytes(ByteArray(0))
                }
            } else {
                target.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = tar.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            if (entry.mode and 0x100 != 0) {
                target.setExecutable(true)
            }
            count++
            if (count % 100 == 0) onProgress(InstallProgress(50 + (count / 120).coerceAtMost(45), "解压运行时 ($count 文件)"))
            entry = tar.nextEntry
        }
        tar.close()

        // verify
        val node = RuntimeManager.nodeBinary()
        if (!node.exists()) throw IllegalStateException("运行时包不完整：缺少 node 二进制")
        val ld = File(root, "glibc/lib/ld-linux-aarch64.so.1")
        if (!ld.exists()) throw IllegalStateException("运行时包不完整：缺少 glibc loader")

        RuntimeManager.markInstalled()
        // ensure dirs the gateway needs (tar skips empty dir entries)
        listOf("tmp", "logs", "workspace").forEach { sub -> File(root, sub).mkdirs() }
        onProgress(InstallProgress(100, "安装完成"))
    }

    private class CountingInputStream(
        private val delegate: java.io.InputStream,
        private val onRead: (Long) -> Unit,
    ) : java.io.InputStream() {
        private var total = 0L
        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) { total++; onRead(total) }
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) { total += n; onRead(total) }
            return n
        }
        override fun close() = delegate.close()
    }
}
