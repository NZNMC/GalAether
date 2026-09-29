package com.galstruo.app.data.kungal

import com.galstruo.app.data.network.NetConfig
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
    /** 是否为限制级(NSFW)作品(来自搜索页载荷的 is_nsfw;官方接口资源无此标记,默认 false) */
    val nsfw: Boolean = false,
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

    // 首页载荷解析结果缓存:登录检测会连poll几次,10 秒内不重复抓首页
    private var lastHomepageTryAt = 0L
    private var lastHomepageUser: KungalUser? = null

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

    /** 带 Cookie 的 GET;非 2xx(如 401 未登录)返回 null,不抛异常 */
    private suspend fun fetchWithCookiesOrNull(url: String, cookies: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Cookie", cookies)
            .build()
        currentClient().newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
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
        // 渲染出的卡片上没有 NSFW 标记,但页面载荷里有每个作品的 is_nsfw,先解析成对照表
        val nsfwMap = parseNsfwMap(html)
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
                result += KungalResource(id, title, size.orEmpty(), nsfw = nsfwMap[id] ?: false)
            }
            idx = chunkEnd
        }
        return result.distinctBy { it.id }
    }

    /**
     * 从搜索页 __NUXT_DATA__ 载荷里提取 资源id → is_nsfw 对照表。
     * 载荷里每个资源是一个对象:{"object":→"galgame_resource" 类型标签,
     * "id":→资源id字符串, "work":→游戏对象(含 is_nsfw 字段)}(2026-09 实测,
     * 50 条资源全部命中;类型标签在载荷里只出现一次,其余都是指针)
     */
    private fun parseNsfwMap(html: String): Map<Long, Boolean> {
        val m = Regex("""<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)</script>""").find(html)
            ?: return emptyMap()
        val arr = runCatching { JsonParser.parseString(m.groupValues[1]).asJsonArray }.getOrNull()
            ?: return emptyMap()
        val map = mutableMapOf<Long, Boolean>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            val type = derefPayload(arr, o.get("object") ?: continue)
            if (!type.isJsonPrimitive || type.asString != "galgame_resource") continue
            val rid = derefPayload(arr, o.get("id") ?: continue)
                .takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.toLongOrNull() ?: continue
            val work = derefPayload(arr, o.get("work") ?: continue)
                .takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val flag = derefPayload(arr, work.get("is_nsfw") ?: continue)
            if (flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean) map[rid] = flag.asBoolean
        }
        return map
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

    /**
     * 当前登录用户;未登录/失效/解析失败返回 null。
     * 官网 2026-09 改版后旧接口 /api/user 已退役:先试新接口 /api/user/session
     * (返回 { user: { uid, name, avatar, ... }, unread }),失败再试旧接口(过渡期双保险)。
     */
    suspend fun currentUser(cookies: String): KungalUser? {
        // 官网把 pinia KUNGalgameUser 存储持久化在同名 Cookie 里(SSR 还原登录态用),
        // Cookie 值就是存储对象的 JSON(中文等字符会被 URL 编码)。这是最可靠的来源:
        // 不依赖任何接口,也不依赖网页当前状态,直接解析
        var user = parseKungalUserCookie(cookies)
        // 新接口:GET /api/user/session
        if (user == null) {
            user = runCatching {
                val raw = fetchWithCookiesOrNull("$BASE/api/user/session", cookies) ?: return@runCatching null
                val json = JsonParser.parseString(raw).asJsonObject
                if (json.get("code")?.asInt == 205) return@runCatching null
                val userObj = findObject(json, "user") ?: return@runCatching null
                val name = userObj.get("name")?.asString ?: userObj.get("username")?.asString
                if (name.isNullOrBlank()) null
                else KungalUser(
                    id = userObj.get("uid")?.asLong ?: userObj.get("id")?.asLong ?: 0L,
                    name = name,
                    avatar = avatarOf(userObj.get("avatar")),
                )
            }.getOrNull()
        }
        // 旧接口(官网已退役,过渡期兜底)
        if (user == null) {
            user = try {
                parseLegacyUser(fetchWithCookies("$BASE/api/user", cookies))
            } catch (e: Exception) {
                null
            }
        }
        // 最终兜底:接口全不可用时,直接抓官网首页(SSR 页面),
        // 登录用户的数据就在页面的 __NUXT_DATA__ 载荷里
        if (user == null) user = currentUserFromHomepage(cookies)

        // 头像补全:SSR 载荷与 Cookie 里当前用户的头像恒为空,依次兜底:
        // ① 用户主页 /user/{id} 的公开数据(设置了自定义头像的用户在这里有);
        // ② 官网默认头像池(未设置头像的用户,官网按名字哈希分配一张,照抄规则保证同款)
        if (user != null && user.avatar.isBlank() && user.id > 0) {
            fetchUserAvatar(user.id, cookies)?.takeIf { it.isNotBlank() }?.let {
                user = user?.copy(avatar = it)
            }
        }
        if (user != null && user.avatar.isBlank()) {
            user = user?.copy(avatar = kungalDefaultAvatar(user.name))
        }
        return user
    }

    /**
     * 从用户主页 /user/{id} 取头像。
     * 官网 SSR 里 KUNGalgameUser 的头像恒为空,但用户主页的载荷里,
     * 目标用户对象(id 为字符串形态)带完整头像 {url, hash, …}。
     */
    suspend fun fetchUserAvatar(userId: Long, cookies: String?): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetchWithCookiesOrNull("$BASE/user/$userId", cookies.orEmpty())
            val m = html?.let {
                Regex("""<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)</script>""").find(it)
            } ?: return@withContext null
            val arr = runCatching { JsonParser.parseString(m.groupValues[1]).asJsonArray }.getOrNull()
                ?: return@withContext null
            for (el in arr) {
                if (!el.isJsonObject) continue
                val o = el.asJsonObject
                val avatarEl = o.get("avatar") ?: continue
                val idEl = o.get("id") ?: continue
                val idVal = derefPayload(arr, idEl)
                // 主页里用户 id 是字符串形态(如 "131523")
                val idLong = idVal.asString.toLongOrNull()
                    ?: (if (idVal.isJsonPrimitive && idVal.asJsonPrimitive.isNumber) idVal.asLong else null)
                    ?: continue
                if (idLong != userId) continue
                // 头像对象里的 url/hash 在载荷里仍是指针,要逐层解引用
                val av = derefPayload(arr, avatarEl)
                var url = ""
                when {
                    av.isJsonObject -> {
                        val o2 = av.asJsonObject
                        url = o2.get("url")?.let { derefPayload(arr, it) }?.asString.orEmpty()
                        if (url.isBlank()) {
                            url = o2.get("src")?.let { derefPayload(arr, it) }?.asString.orEmpty()
                        }
                        if (url.isBlank()) {
                            url = o2.get("hash")?.let { derefPayload(arr, it) }?.asString.orEmpty()
                        }
                    }
                    av.isJsonPrimitive && av.asJsonPrimitive.isString -> url = av.asString
                }
                if (url.isBlank()) continue
                if (url.startsWith("/")) url = BASE + url
                else if (!url.startsWith("http") && !url.contains(".")) {
                    url = "https://image.kungal.iloveren.link/${url.substring(0, 2)}/${url.substring(2, 4)}/$url.webp"
                }
                return@withContext url
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 Cookie 头里解析官网持久化的用户存储(KUNGalgameUser Cookie)。
     * 登录后官网前端会把 pinia KUNGalgameUser 存储序列化进同名 Cookie,
     * 值可能是裸 JSON 或 URL 编码的 JSON,两种都试。
     */
    fun parseKungalUserCookie(cookies: String): KungalUser? {
        val value = cookies.split(";")
            .mapNotNull { part ->
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].trim() == "KUNGalgameUser") kv[1] else null
            }
            .firstOrNull() ?: return null
        val decoded = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrNull()
        val candidates = listOf(value, decoded).filterNotNull().distinct()
        for (c in candidates) {
            val el = runCatching { JsonParser.parseString(c) }.getOrNull() ?: continue
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            val name = o.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString.orEmpty()
            // 官网把未登录的匿名昵称存成字面量 '""',要当空值过滤
            if (name.isBlank() || name == "\"\"") continue
            val avatar = avatarOf(o.get("avatar")).ifBlank { avatarOf(o.get("avatarMin")) }
            // id 是数字(如 132287);sub 是 UUID 字符串,不能当数字 id 用
            val userId = o.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asLong ?: o.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.toLongOrNull() ?: 0L
            return KungalUser(
                id = userId,
                name = name,
                avatar = avatar,
                moemoepoint = o.get("moemoepoint")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                    ?.asInt ?: 0,
                isCheckIn = o.get("isCheckIn")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                    ?.asBoolean ?: false,
            )
        }
        return null
    }

    /**
     * 从官网首页的 __NUXT_DATA__ 载荷里提取当前登录用户。
     * 官网是服务端渲染:登录后首页 HTML 里的 pinia KUNGalgameUser 存储对象
     * 就带 name/avatar 等字段,不需要任何接口。
     */
    suspend fun currentUserFromHomepage(cookies: String): KungalUser? {
        val now = System.currentTimeMillis()
        if (now - lastHomepageTryAt < 10_000) return lastHomepageUser
        lastHomepageTryAt = now
        val found = try {
            val html = fetchWithCookiesOrNull(BASE, cookies)
            val m = html?.let {
                Regex("""<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)</script>""").find(it)
            }
            val arr = m?.let { runCatching { JsonParser.parseString(it.groupValues[1]).asJsonArray }.getOrNull() }
            arr?.let { extractUserFromPayload(it) }
        } catch (e: Exception) {
            null
        }
        lastHomepageUser = found
        return found
    }

    /**
     * devalue 载荷解引用:指针有两种形态——纯数字(直接是下标),
     * 以及 ["Ref",下标] / ["EmptyRef",下标] 数组(重复出现的值去重后变成引用)。
     * 2026-09 实测:登录后的首页里 name 就是 ["Ref",1619] 形态,只按数字解析会扑空
     */
    private fun derefPayload(arr: JsonArray, el: JsonElement, depth: Int = 0): JsonElement {
        var v = el
        var d = depth
        while (d < 12) {
            when {
                v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> {
                    val idx = v.asInt
                    if (idx < 0 || idx >= arr.size()) break
                    v = arr[idx]
                    d++
                    // 落到数字就是叶子值(如萌汁点数 42),不能再当指针继续追
                    if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) break
                }
                v.isJsonArray && v.asJsonArray.size() >= 2 -> {
                    val a = v.asJsonArray
                    val tag = a[0].takeIf { it.isJsonPrimitive }?.asString
                    val idxEl = a[1]
                    if ((tag == "Ref" || tag == "EmptyRef") && idxEl.isJsonPrimitive &&
                        idxEl.asJsonPrimitive.isNumber
                    ) {
                        val idx = idxEl.asInt
                        if (idx < 0 || idx >= arr.size()) break
                        v = arr[idx]
                        d++
                        if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) break
                    } else break
                }
                else -> break
            }
        }
        return v
    }

    /** 在载荷数组里找当前用户:按 pinia KUNGalgameUser 存储对象的字段签名匹配 */
    private fun extractUserFromPayload(arr: JsonArray): KungalUser? {
        fun deref(el: JsonElement, depth: Int = 0): JsonElement = derefPayload(arr, el, depth)
        fun str(el: JsonElement?): String {
            val v = deref(el ?: return "")
            return if (v.isJsonPrimitive && v.asJsonPrimitive.isString) v.asString else ""
        }
        fun avatarOf(el: JsonElement?): String {
            val v = deref(el ?: return "")
            var avatar = when {
                v.isJsonPrimitive -> v.asString
                v.isJsonArray -> str(v.asJsonArray.firstOrNull())
                v.isJsonObject -> str(v.asJsonObject.get("url")).ifBlank {
                    str(v.asJsonObject.get("src")).ifBlank { str(v.asJsonObject.get("hash")) }
                }
                else -> ""
            }
            if (avatar.isNotBlank() && avatar.startsWith("/")) avatar = BASE + avatar
            else if (avatar.isNotBlank() && !avatar.startsWith("http") && !avatar.contains(".")) {
                avatar = "https://image.kungal.iloveren.link/${avatar.substring(0, 2)}/${avatar.substring(2, 4)}/$avatar.webp"
            }
            return avatar
        }
        // KUNGalgameUser 存储对象:同时带 moemoepoint 与 isCheckIn/dailyCheckIn 等专属字段
        // (未登录时这些字段的值是 EmptyRef,解不出 name,自然跳过)
        for (el in arr) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            if (o.get("moemoepoint") == null) continue
            if (o.get("isCheckIn") == null && o.get("dailyCheckIn") == null &&
                o.get("dailyToolsetUploadBytes") == null
            ) continue
            val name = str(o.get("name"))
            // 官网把未登录的匿名昵称存成字面量 '""'(两个引号字符),要当空值过滤
            if (name.isBlank() || name == "\"\"") continue
            val moemoe = deref(o.get("moemoepoint"))
                .takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
            val checkIn = deref(o.get("isCheckIn"))
                .takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false
            return KungalUser(
                id = str(o.get("sub")).toLongOrNull() ?: 0L,
                name = name,
                avatar = avatarOf(o.get("avatar")).ifBlank { avatarOf(o.get("avatarMin")) },
                moemoepoint = moemoe,
                isCheckIn = checkIn,
            )
        }
        return null
    }

    /** 从返回 JSON 里找 user 对象:优先根下的 user 字段,其次 data.user(两种包裹方式都兼容) */
    private fun findObject(root: JsonObject, key: String): JsonObject? {
        val direct = root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        if (direct != null) return direct
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return data.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** 头像字段兼容三种形态:字符串 / 数组取第一个 / 图片对象(url·src·hash) */
    private fun avatarOf(el: com.google.gson.JsonElement?): String {
        if (el == null) return ""
        var avatar = when {
            el.isJsonPrimitive -> el.asString
            el.isJsonArray -> el.asJsonArray.firstOrNull()?.asString.orEmpty()
            el.isJsonObject -> {
                val o = el.asJsonObject
                o.get("url")?.asString ?: o.get("src")?.asString ?: o.get("hash")?.asString.orEmpty()
            }
            else -> ""
        }
        // 站内相对路径补全;纯 hash 拼完整 CDN 地址(鲲头像:image.kungal.iloveren.link/前2位/第3-4位/完整hash.webp)
        if (avatar.isNotBlank() && avatar.startsWith("/")) avatar = BASE + avatar
        else if (avatar.isNotBlank() && !avatar.startsWith("http") && !avatar.contains(".")) {
            avatar = "https://image.kungal.iloveren.link/${avatar.substring(0, 2)}/${avatar.substring(2, 4)}/$avatar.webp"
        }
        return avatar
    }

    /** 旧接口返回解析:{ name|username, avatar: str|array };登录失效标准响应 {"code":205} */
    private fun parseLegacyUser(raw: String): KungalUser? {
        val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return null
        if (json.get("code")?.asInt == 205) return null
        val obj = (json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject) ?: json
        val name = obj.get("name")?.asString ?: obj.get("username")?.asString ?: return null
        return KungalUser(
            id = obj.get("uid")?.asLong ?: obj.get("id")?.asLong ?: 0L,
            name = name,
            avatar = avatarOf(obj.get("avatar")),
        )
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
