package com.lobster.clawbox.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lobster.clawbox.MainActivity
import com.lobster.clawbox.R
import com.lobster.clawbox.data.AppPrefs
import com.lobster.clawbox.runtime.RuntimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the local Gateway alive in the background.
 * Launches the embedded node gateway process and keeps the WS client connected.
 */
class GatewayService : Service() {

    companion object {
        private const val TAG = "ClawBox-SVC"
        const val CHANNEL_ID = "clawbox_gateway"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.lobster.clawbox.action.START"
        const val ACTION_STOP = "com.lobster.clawbox.action.STOP"

        @Volatile
        var running = false
            private set

        @Volatile
        var startedAtMs: Long = 0
            private set

        fun start(context: Context) {
            val i = Intent(context, GatewayService::class.java).setAction(ACTION_START)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, GatewayService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGateway()
                return START_NOT_STICKY
            }
            else -> startGateway()
        }
        return START_STICKY
    }

    private fun startGateway() {
        if (running) return
        running = true
        startedAtMs = System.currentTimeMillis()
        startForeground(NOTIF_ID, buildNotification("启动中…"))
        val port = AppPrefs(this).gatewayPort
        Log.d(TAG, "startGateway port=$port")
        job = scope.launch {
            try {
                Log.d(TAG, "isInstalled=${RuntimeManager.isInstalled()}")
                if (RuntimeManager.isInstalled()) {
                    GatewayProcess.start(port)
                    Log.d(TAG, "GatewayProcess.start 返回")
                    GatewayApi.start()
                    // wait for the WS link (gateway ready); timeout guard
                    val readyDeadline = System.currentTimeMillis() + 180_000
                    while (isActive && GatewayApi.connState.value != GatewayApi.ConnState.CONNECTED) {
                        if (!GatewayProcess.isRunning) break
                        if (System.currentTimeMillis() > readyDeadline) {
                            Log.e(TAG, "启动超时: connState=${GatewayApi.connState.value}")
                            updateNotification("启动超时：请到设置→网关日志查看")
                            stopGateway()
                            return@launch
                        }
                        delay(1000)
                    }
                    // keep foreground service alive while process lives
                    while (isActive) {
                        if (!GatewayProcess.isRunning) break
                        updateNotification("运行中 · 端口 $port")
                        delay(30_000)
                    }
                    if (isActive) stopGateway()
                } else {
                    updateNotification("运行时未安装")
                    stopGateway()
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动失败: ${e.message}", e)
                updateNotification("启动失败: ${e.message?.take(60)}")
                stopGateway()
            }
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun stopGateway() {
        GatewayApi.stop()
        GatewayProcess.stop()
        running = false
        startedAtMs = 0
        job?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID, "ClawBox Gateway",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClawBox 主机")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_lobster)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        running = false
        super.onDestroy()
    }
}
