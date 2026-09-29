package com.galstruo.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 通用文件下载:带进度回调,走全局代理配置。
 * 用于"检查更新"的安装包和设置页模拟器安装包的下载。
 */
object FileDownloader {

    private fun buildClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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

    /** GitHub 国内加速镜像前缀(依次回退;镜像地址偶尔失效,失效时自动跳到下一个) */
    private val mirrors = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://gh-proxy.com/",
    )

    /**
     * 下载 [url] 到 [dest],onProgress 回调百分比 0~100。
     * 回调在 IO 线程执行,更新界面需自行切回主线程。
     */
    suspend fun download(url: String, dest: File, onProgress: (Int) -> Unit = {}): File {
        var lastError: Exception? = null
        // 直连失败时依次尝试国内加速镜像
        for (fullUrl in listOf(url) + mirrors.map { it + url }) {
            try {
                return downloadOnce(fullUrl, dest, onProgress)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("下载失败:${lastError?.message ?: "未知错误"}")
    }

    private suspend fun downloadOnce(url: String, dest: File, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            val request = Request.Builder().url(url).build()
            currentClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("响应为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(dest).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val percent = ((read * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
            }
            dest
        }
}
