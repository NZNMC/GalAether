package com.galstruo.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.galstruo.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 下载前台服务:在通知栏显示下载进度,保证后台下载不被系统杀死 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startObserving()
        return START_NOT_STICKY
    }

    private fun startObserving() {
        if (collectJob != null) return
        collectJob = scope.launch {
            DownloadManager.tasks.collect { map ->
                val active = map.values.filter {
                    it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING
                }
                when {
                    active.isNotEmpty() -> updateNotification(active)
                    map.isEmpty() || foregroundStarted -> stopAndShutDown()
                    else -> stopSelf()  // 只有已结束的任务,等归档通知后自行停止
                }
            }
        }
    }

    private fun updateNotification(active: List<DownloadTask>) {
        val totalDone = active.sumOf { it.downloadedBytes }
        val totalSize = active.sumOf { it.totalBytes }
        val percent = if (totalSize > 0) (totalDone * 100 / totalSize).toInt() else 0
        val title = if (active.size == 1) active.first().fileName else "正在下载 ${active.size} 个文件"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, totalSize <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        if (foregroundStarted) {
            nm.notify(ONGOING_ID, notification)
        } else {
            startForeground(ONGOING_ID, notification)
            foregroundStarted = true
        }
    }

    private fun stopAndShutDown() {
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        collectJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val ONGOING_ID = 1001

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "下载",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
    }
}
