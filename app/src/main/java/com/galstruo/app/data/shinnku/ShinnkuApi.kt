package com.galstruo.app.data.shinnku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 真红小站(shinnku.com)资源搜索。
 * 站点搜索页由服务端渲染,结果以转义 JSON 内嵌在 HTML 中,这里直接解析。
 */
object ShinnkuApi {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 匹配内嵌的转义 JSON 结果:{\"id\":\"...\",\"info\":{\"file_path\":\"...\",...}} */
    private val itemRegex = Regex(
        """\{\\"id\\":\\"([^\\]+)\\",\\"info\\":\{\\"file_path\\":\\"([^\\]+)\\",\\"upload_timestamp\\":(\d+),\\"file_size\\":(\d+)\}\}"""
    )

    /** 按关键词搜索资源文件(最多返回站点内嵌的 200 条) */
    suspend fun searchFiles(keyword: String): List<ShinnkuFile> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(keyword, "UTF-8")
        val request = Request.Builder()
            .url("https://www.shinnku.com/search?q=$query")
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("真红小站访问失败: HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            itemRegex.findAll(body).map { m ->
                ShinnkuFile(
                    filePath = decodeEscapes(m.groupValues[1]),
                    uploadTimestamp = m.groupValues[3].toLongOrNull() ?: 0,
                    fileSize = m.groupValues[4].toLongOrNull() ?: 0,
                )
            }.toList()
        }
    }

    /** 还原 HTML 中的转义序列(\uXXXX、\"、\\) */
    private fun decodeEscapes(s: String): String = s
        .replace(Regex("""\\u([0-9a-fA-F]{4})""")) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
