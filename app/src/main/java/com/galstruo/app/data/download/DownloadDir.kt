package com.galstruo.app.data.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.galstruo.app.GalAetherApp
import java.io.File
import java.io.FileInputStream

/**
 * 自选下载目录(通过系统文件夹选择器 SAF 选定)。
 * 未选择时归档复制走系统"下载"公共目录;选择后复制进所选文件夹。
 * 选择的文件夹需要持久授权(takePersistableUriPermission),重启后仍可用。
 */
object DownloadDir {

    var treeUri: Uri? = null
        private set

    fun update(uri: Uri?) {
        treeUri = uri
    }

    fun isSet(): Boolean = treeUri != null

    /** 所选文件夹的名字(用于界面展示,拿不到就返回空串) */
    val label: String
        get() {
            val uri = treeUri ?: return ""
            return try {
                DocumentFile.fromTreeUri(GalAetherApp.appContext, uri)?.name ?: ""
            } catch (_: Exception) {
                ""
            }
        }

    /**
     * 把源文件复制进所选文件夹,返回目标文件名。
     * 文件夹被删/授权失效等情况返回 null,调用方应回退到系统下载目录。
     */
    fun copyInto(context: Context, src: File): String? {
        val uri = treeUri ?: return null
        return try {
            val tree = DocumentFile.fromTreeUri(context, uri) ?: return null
            val out = tree.createFile("application/octet-stream", src.name) ?: return null
            context.contentResolver.openOutputStream(out.uri)?.use { os ->
                FileInputStream(src).use { it.copyTo(os) }
            } ?: return null
            src.name
        } catch (_: Exception) {
            null
        }
    }
}
