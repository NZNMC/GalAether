package com.galstruo.app.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.sync.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份:把收藏、浏览历史、搜索历史、下载记录、用户设置打包成一个 JSON 文件。
 * - 导出:写入系统下载目录,换手机/删数据后可用这个文件恢复;
 * - 导入:选择备份文件后整体恢复(下载记录只恢复列表,文件本身不重新下载);
 * - 凭据(GitHub 令牌 / WebDAV 密码)不会写进备份文件。
 */
object BackupManager {

    private fun fileName(): String =
        "GalAether备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())}.json"

    /** 导出到系统下载目录,返回提示文字 */
    suspend fun export(context: Context): String = withContext(Dispatchers.IO) {
        val settings = SettingsStore(context)
        val data = SyncData.build(settings.uiSettings.first())
        val json = SyncData.toJson(data)
        val name = fileName()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                ) ?: throw IllegalStateException("无法写入下载目录")
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, name).writeText(json)
            }
            "备份成功:已保存「$name」到下载目录(共 ${data.favorites.size} 部收藏,含全部设置)"
        } catch (e: Exception) {
            "备份失败:${e.message}"
        }
    }

    /** 从选中的文件导入,返回提示文字 */
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext "读取文件失败"
            val data = SyncData.fromJson(text)
                ?: return@withContext "不是有效的 GalAether 备份文件"
            val settings = SettingsStore(context)
            SyncData.apply(data, settings.uiSettings.first(), settings)
            "导入成功:收藏 ${data.favorites.size} 部 · 历史 ${data.history.size} 条 · 下载记录 ${data.downloadRecords.size} 个,设置已恢复"
        } catch (e: Exception) {
            "导入失败:${e.message}"
        }
    }
}
