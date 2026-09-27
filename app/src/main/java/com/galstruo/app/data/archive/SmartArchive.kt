package com.galstruo.app.data.archive

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.galstruo.app.GalAetherApp
import com.galstruo.app.data.download.DownloadDir
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.download.DownloadService
import com.galstruo.app.data.download.DownloadTask
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 智能归档:下载完成后按文件类型自动处理。
 * - APK → 通知一键安装
 * - ZIP + KRKR/ONS → 解压到对应模拟器目录(需"所有文件访问"权限,未授权则复制到下载目录)
 * - 其余(7z/rar 等)→ 复制到系统下载目录,用户自行解压
 */
object SmartArchive {

    private const val KRKR_DIR = "/storage/emulated/0/Kirikiroid2"
    private const val ONS_DIR = "/storage/emulated/0/ONS"

    fun handleCompleted(id: String, completed: DownloadTask) {
        val context = GalAetherApp.appContext
        val task = DownloadManager.tasks.value[id] ?: completed
        val src = DownloadManager.fileOf(task)
        if (!src.exists()) {
            notify(context, task, "下载完成,但文件未找到")
            return
        }
        try {
            when {
                task.fileName.endsWith(".apk", true) -> notifyInstall(context, task, src)
                task.fileName.endsWith(".zip", true) && isSimulatorType(task) -> handleZip(context, task, src)
                else -> handleCopy(context, task, src)
            }
        } catch (e: Exception) {
            notify(context, task, "归档失败:${e.message}")
        }
    }

    private fun isSimulatorType(task: DownloadTask) =
        task.typeLabel.contains("KRKR") || task.typeLabel.contains("ONS")

    private fun handleZip(context: Context, task: DownloadTask, src: File) {
        val targetDir = if (task.typeLabel.contains("ONS")) File(ONS_DIR) else File(KRKR_DIR)
        val canWriteShared = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (!canWriteShared) {
            // 未授权"所有文件访问",降级为复制到下载目录
            handleCopy(context, task, src)
            return
        }
        if (!targetDir.exists()) targetDir.mkdirs()
        val root = targetDir.absolutePath
        ZipInputStream(FileInputStream(src)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name.contains("..")) continue  // 防目录穿越
                val dest = File(root, name)
                if (entry.isDirectory) {
                    dest.mkdirs()
                } else {
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { out -> zip.copyTo(out) }
                }
            }
        }
        DownloadManager.markArchived(task.url)
        src.delete()  // 解压完成,应用目录里的压缩包不再需要
        notify(context, task, "已解压到 ${targetDir.name} 文件夹")
    }

    /** 复制到系统下载目录。成功返回提示文字,失败抛异常。 */
    private fun handleCopy(context: Context, task: DownloadTask, src: File) {
        // 用户选了自选下载目录时优先复制进去;文件夹不可用则回退到系统下载目录
        if (DownloadDir.isSet() && DownloadDir.copyInto(context, src) != null) {
            DownloadManager.markArchived(task.url)
            src.delete()  // 已复制到自选目录,应用目录里的原件不再需要
            notify(context, task, "已保存到「${DownloadDir.label}」文件夹")
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, src.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法写入下载目录")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(src).use { it.copyTo(out) }
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            FileInputStream(src).use { input ->
                FileOutputStream(File(dir, src.name)).use { input.copyTo(it) }
            }
        }
        DownloadManager.markArchived(task.url)
        src.delete()  // 已复制到下载目录,应用目录里的原件不再需要
        val hint = if (task.fileName.endsWith(".zip", true)) {
            "已保存到下载目录(解压需要先授予\"所有文件访问\"权限)"
        } else {
            "已保存到下载目录,请解压后使用"
        }
        notify(context, task, hint)
    }

    /** 下载管理界面「保存到下载目录」按钮:把应用目录里的文件复制到系统下载目录 */
    fun saveToDownloads(context: Context, task: DownloadTask): String {
        val src = DownloadManager.fileOf(task)
        if (!src.exists()) return "源文件已不存在"
        return try {
            handleCopy(context, task, src)
            "已保存到下载目录"
        } catch (e: Exception) {
            "保存失败:${e.message}"
        }
    }

    /** 界面"安装"按钮:唤起安装器(未开启"安装未知应用"时先跳设置) */
    fun installApk(context: Context, apk: File) {
        if (!apk.exists()) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun notifyInstall(context: Context, task: DownloadTask, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        notify(context, task, "下载完成,点击安装", installIntent)
    }

    private fun notify(context: Context, task: DownloadTask, text: String, contentIntent: Intent? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val pending = contentIntent?.let {
            PendingIntent.getActivity(
                context, task.id.hashCode(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(task.fileName)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        pending?.let { builder.setContentIntent(it) }
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.notify(task.id.hashCode(), builder.build())
    }
}
