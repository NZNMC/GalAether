package com.galstruo.app.data.kungal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 鲲galgame 的一个网盘资源(来自资源搜索页) */
data class KungalResource(
    val id: Long,
    val title: String,
    val size: String = "",
)

/**
 * 鲲galgame(kungal.com)公开页面解析:
 * - 资源搜索:GET /galgame/resource?keywords=&page=(SSR 公开,免登录)
 * - 资源详情:GET /galgame/resource/{id}(SSR 公开,页面数据里带网盘链接)
 * 注意:站点 /api 接口全部需要登录,不能用;只能走网页解析。
 */
object KungalApi {

    private const val BASE = "https://www.kungal.com"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    /** 按关键词搜索网盘资源(第 1 页,最多 20 条左右) */
    suspend fun searchResources(keyword: String, page: Int = 1): List<KungalResource> {
        val kw = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val html = fetch("$BASE/galgame/resource?keywords=$kw&page=$page")
        return parseSearch(html)
    }

    /** 解析资源搜索页:每个资源是一个 <a href="/galgame/resource/{id}"> 卡片 */
    private fun parseSearch(html: String): List<KungalResource> {
        val result = mutableListOf<KungalResource>()
        val marker = "<a href=\"/galgame/resource/"
        var idx = html.indexOf(marker)
        while (idx >= 0) {
            val idEnd = html.indexOf('"', idx + marker.length)
            if (idEnd < 0) break
            val id = html.substring(idx + marker.length, idEnd).toLongOrNull()
            val chunkEnd = html.indexOf(marker, idEnd)
            val chunk = html.substring(idx, if (chunkEnd < 0) html.length else chunkEnd)
            val title = Regex("""<h3[^>]*>([^<]{1,150})</h3>""").find(chunk)?.groupValues?.get(1)?.trim()
            val size = Regex("""([0-9.]+ ?[KMG]?B)(?:<!--|</span>)""").find(chunk)?.groupValues?.get(1)
            if (id != null && !title.isNullOrBlank()) {
                result += KungalResource(id, title, size.orEmpty())
            }
            idx = chunkEnd
        }
        return result.distinctBy { it.id }
    }

    /** 解析资源详情页里的网盘下载链接 */
    suspend fun resourceLinks(id: Long): List<String> {
        val html = fetch("$BASE/galgame/resource/$id")
        return parseLinks(html)
    }

    private fun parseLinks(html: String): List<String> {
        val links = LinkedHashSet<String>()
        // Nuxt 数据载荷里的转义链接: https://pan.baidu.com/s/...
        // URL 内部也有 / 转义,所以整段按"转义序列或普通字符"匹配(排除全角空格 NBSP,防止链接后跟中文说明)
        Regex("""https?:(?:\\u[0-9A-Fa-f]{4}|[^"\\  ]){10,400}""").findAll(html).forEach { m ->
            links += unescape(m.value)
        }
        // 明文链接(渲染出来的 HTML 里)
        Regex("""https?://[^"'<\\  ]{10,400}""").findAll(html).forEach { m ->
            links += m.value.replace("&amp;", "&")
        }
        // 过滤掉图片/站内/静态资源等无关链接,只留网盘类链接
        return links
            .map { it.trimEnd { c -> c in ".。,，)）】」!（(" } }
            .filter { link ->
                // 只要带路径的链接,去掉裸域名(如 https://pan.baidu.com)
                link.substringAfter("://").contains("/") &&
                    !link.contains("kungal", ignoreCase = true) &&
                    !link.contains("iloveren", ignoreCase = true) &&
                    !link.contains("github.com") &&
                    !link.contains("nextmoe") &&
                    !link.contains("w3.org") && !link.contains("schema.org") &&
                    !link.endsWith(".png") && !link.endsWith(".jpg") && !link.endsWith(".jpeg") &&
                    !link.endsWith(".webp") && !link.endsWith(".gif") && !link.endsWith(".ico") &&
                    !link.endsWith(".css") && !link.endsWith(".js") && !link.endsWith(".svg")
            }
            .toList()
    }

    /** 把载荷里的 \uXXXX 转义还原成真实字符 */
    private fun unescape(s: String): String =
        s.replace("\\u002F", "/").replace("\\u0026", "&").replace("\\u003D", "=")
}
