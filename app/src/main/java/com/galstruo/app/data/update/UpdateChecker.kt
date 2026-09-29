package com.galstruo.app.data.update

import com.galstruo.app.GalAetherApp
import com.galstruo.app.data.network.FileDownloader
import com.galstruo.app.data.network.NetConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 检查更新:查询 GitHub Releases 的最新版本,下载安装包后唤起安装器。
 * GitHub 在国内下载经常失败,查询与下载都会依次回退国内加速镜像。
 * 仅在用户手动点击时使用,不自动联网。
 */
object UpdateChecker {

    private const val REPO = "NZNMC/GalAether"

    /** 查询接口的加速镜像前缀(依次回退) */
    private val apiMirrors = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
    )

    /** 最新版本信息 */
    data class ReleaseInfo(
        val tag: String,
        val apkUrl: String,
        val notes: String,
    )

    private fun buildClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .also { NetConfig.apply(it) }
        .build()

    private var client = buildClient()
    private var clientVersion = NetConfig.version

    /** 代理配置变化时重建客户端 */
    private fun currentClient(): OkHttpClient {
        if (clientVersion != NetConfig.version) {
            client = buildClient()
            clientVersion = NetConfig.version
        }
        return client
    }

    /** 查询最新发布(没有带 APK 附件的发布时返回 null) */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$REPO/releases/latest"
        var lastError: Exception? = null
        for (url in listOf(apiUrl) + apiMirrors.map { it + apiUrl }) {
            try {
                val info = checkOnce(url)
                if (info != null) return@withContext info
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("检查更新失败:${lastError?.message ?: "未知错误"}")
    }

    private fun checkOnce(apiUrl: String): ReleaseInfo? {
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("Cache-Control", "no-cache")
            .build()
        currentClient().newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val obj = JsonParser.parseString(resp.body?.string().orEmpty()) as? JsonObject
                ?: return null
            val tag = obj.get("tag_name")?.asString ?: return null
            val assets = obj.getAsJsonArray("assets") ?: return null
            val apk = assets.firstOrNull {
                it.asJsonObject.get("name")?.asString?.endsWith(".apk", true) == true
            }?.asJsonObject ?: return null
            val url = apk.get("browser_download_url")?.asString ?: return null
            return ReleaseInfo(tag, url, obj.get("body")?.asString.orEmpty().take(2000))
        }
    }

    /** 简单版本比较(忽略 v 前缀与 -M 后缀,按数字段逐段比较) */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.removePrefix("v").substringBefore('-').split('.')
        val l = local.substringBefore('-').split('.')
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            if (rv != lv) return rv > lv
        }
        return false
    }

    /** 下载安装包到应用缓存目录(直连失败自动换加速镜像),onProgress 回调下载百分比 */
    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit = {}): File {
        val dir = File(GalAetherApp.appContext.cacheDir, "update").apply { mkdirs() }
        return FileDownloader.download(url, File(dir, "GalAether-update.apk"), onProgress)
    }
}
