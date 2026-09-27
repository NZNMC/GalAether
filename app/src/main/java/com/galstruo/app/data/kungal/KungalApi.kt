package com.galstruo.app.data.kungal

import com.galstruo.app.data.network.NetConfig
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 鲲galgame 的一个网盘资源(来自资源搜索页或官方接口) */
data class KungalResource(
    val id: Long,
    val title: String,
    val size: String = "",
    /** 官方接口直接给出的下载链接(为空时点「查看链接」再解析网页) */
    val links: List<String> = emptyList(),
    /** 官方接口给出的提取码 / 解压密码 / 备注 */
    val code: String = "",
    val password: String = "",
    val note: String = "",
    /** 是否来自官方接口(登录后) */
    val official: Boolean = false,
)

/**
 * 鲲galgame(kungal.com)公开页面解析 + 官方接口:
 * - 资源搜索:GET /galgame/resource?keywords=&page=(SSR 公开,免登录)
 * - 资源详情:GET /galgame/resource/{id}(SSR 公开,页面数据里带网盘链接)
 * - 官方接口(需登录,带会话 Cookie):GET /api/user、GET /api/galgame/{id}/resource/all
 */
object KungalApi {

    private const val BASE = "https://www.kungal.com"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun buildClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .also { NetConfig.apply(it) }
        .build()

    private var client = buildClient()
    private var clientVersion = NetConfig.version

    private fun currentClient(): OkHttpClient {
        if (clientVersion != NetConfig.version) {
            client = buildClient()
            clientVersion = NetConfig.version
        }
        return client
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        currentClient().newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    /** 把会话 Cookie 附加到请求(官方 /api 接口需要) */
    private suspend fun fetchWithCookies(url: String, cookies: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Cookie", cookies)
            .build()
        currentClient().newCall(request).execute().use { resp ->
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

    // ---------------- 官方接口(登录后) ----------------

    /** 当前登录用户(GET /api/user);未登录/失效/解析失败返回 null */
    suspend fun currentUser(cookies: String): KungalUser? {
        return try {
            val raw = fetchWithCookies("$BASE/api/user", cookies)
            val json = JsonParser.parseString(raw).asJsonObject
            // 登录失效的标准响应:{"code":205,"message":"用户登录失效"}
            if (json.get("code")?.asInt == 205) return null
            // 有的接口会把数据包在 data 字段里
            val obj = (json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject) ?: json
            val name = obj.get("name")?.asString ?: obj.get("username")?.asString ?: return null
            var avatar = obj.get("avatar")?.let { a ->
                when {
                    a.isJsonPrimitive -> a.asString
                    a.isJsonArray -> a.asJsonArray.firstOrNull()?.asString.orEmpty()
                    else -> ""
                }
            }.orEmpty()
            if (avatar.startsWith("/")) avatar = BASE + avatar
            KungalUser(id = obj.get("uid")?.asLong ?: obj.get("id")?.asLong ?: 0L, name = name, avatar = avatar)
        } catch (e: Exception) {
            null
        }
    }

    /** 退出登录(POST /api/auth/logout) */
    suspend fun logout(cookies: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/api/auth/logout")
            .header("User-Agent", UA)
            .header("Cookie", cookies)
            .post("".toRequestBody(null))
            .build()
        runCatching { currentClient().newCall(request).execute().close() }
    }

    /** 从资源详情页反查所属游戏的 ID(页面里有 /galgame/{gid} 链接);找不到返回 null */
    suspend fun galgameIdOf(resourceId: Long): Long? = try {
        val html = fetch("$BASE/galgame/resource/$resourceId")
        Regex("""/galgame/(\d+)""").find(html)?.groupValues?.get(1)?.toLongOrNull()
    } catch (e: Exception) {
        null
    }

    /**
     * 官方接口:某游戏的全部资源列表(GET /api/galgame/{id}/resource/all,需登录)。
     * 字段参考开源模型:link(链接数组)、code(提取码)、password(解压密码)、note、type、language、platform、size。
     * 任何一步失败/未登录返回 null,调用方回退到网页解析。
     */
    suspend fun officialResources(galgameId: Long, cookies: String): List<KungalResource>? {
        return try {
            val raw = fetchWithCookies("$BASE/api/galgame/$galgameId/resource/all?galgameId=$galgameId", cookies)
            if (raw.contains("\"code\":205")) {
                KungalAuth.expire()
                return null
            }
            parseOfficialResources(raw)
        } catch (e: Exception) {
            null
        }
    }

    /** 解析官方资源列表:递归找出所有带 link 数组的对象,每个对象一条资源 */
    private fun parseOfficialResources(raw: String): List<KungalResource>? {
        val json = try {
            JsonParser.parseString(raw)
        } catch (e: Exception) {
            return null
        }
        val objs = mutableListOf<JsonObject>()
        fun collect(el: com.google.gson.JsonElement) {
            when {
                el.isJsonObject -> {
                    objs += el.asJsonObject
                    el.asJsonObject.entrySet().forEach { collect(it.value) }
                }
                el.isJsonArray -> el.asJsonArray.forEach { collect(it) }
            }
        }
        collect(json)
        val resources = objs.filter { it.get("link") != null }
        if (resources.isEmpty()) return null
        return resources.map { obj ->
            val links = (obj.get("link")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray())
                .mapNotNull { el -> el.asString?.takeIf { it.startsWith("http") } }
            val parts = listOf(
                obj.get("type")?.asString,
                obj.get("language")?.asString,
                obj.get("platform")?.asString,
            ).filterNotNull().map { it.trim() }.filter { it.isNotBlank() }
            KungalResource(
                id = obj.get("grid")?.asLong ?: obj.get("id")?.asLong ?: 0L,
                title = if (parts.isEmpty()) "网盘资源" else parts.joinToString(" · "),
                size = obj.get("size")?.asString.orEmpty(),
                links = links,
                code = obj.get("code")?.asString.orEmpty(),
                password = obj.get("password")?.asString.orEmpty(),
                note = obj.get("note")?.asString.orEmpty(),
                official = true,
            )
        }
    }
}
