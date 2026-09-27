package com.galstruo.app.data.searchgal

import com.galstruo.app.data.network.NetConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一个资源平台在 SearchGal 里的搜索结果 */
data class SearchGalPlatform(
    val name: String,
    /** 平台标识颜色名(red/lime/white…,来自接口 color 字段) */
    val color: String = "",
    val tags: List<String> = emptyList(),
    val items: List<SearchGalItem> = emptyList(),
)

/** 一条资源(发布页链接,不是直链,需打开网页查看) */
data class SearchGalItem(val name: String, val url: String)

/**
 * SearchGal 聚合搜索:一次同时搜索 27+ 个 Gal 资源站。
 * 接口:POST {服务器}/gal,表单字段 game,SSE 流式返回一行一个 JSON:
 *   {"total":N} → {"progress":{"completed":x,"total":N},"result":{平台结果}} → {"done":true}
 * 平台结果边搜边返回,可用回调实时显示;某台服务器失败会自动换下一台。
 */
object SearchGalApi {

    /** 公开接口服务器,按优先级排列(实测第一台可用) */
    private val servers = listOf(
        "https://vercel.api.searchgal.top",
        "https://cf.api.searchgal.top",
        "https://netlify.api.searchgal.top",
        "https://gal.0721ciallo.top",
    )

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun buildClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
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

    /**
     * 聚合搜索游戏资源,返回所有找到结果的平台。
     * @param game 游戏名(中文名效果最好)
     * @param onProgress 搜索进度回调(已完成平台数, 总平台数)
     * @param onPlatform 每搜完一个平台就回调一次(可边搜边显示)
     */
    suspend fun searchGal(
        game: String,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        onPlatform: (SearchGalPlatform) -> Unit = {},
    ): List<SearchGalPlatform> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (server in servers) {
            val platforms = try {
                searchOn(server, game, onProgress, onPlatform)
            } catch (e: Exception) {
                lastError = e
                null
            }
            if (platforms != null) return@withContext platforms
            // 这次失败就换下一台服务器(已拿到的部分结果通过回调已发给界面,仍保留)
        }
        throw lastError ?: IOException("所有搜索服务器都不可用")
    }

    private fun searchOn(
        server: String,
        game: String,
        onProgress: (Int, Int) -> Unit,
        onPlatform: (SearchGalPlatform) -> Unit,
    ): List<SearchGalPlatform> {
        val body = MultipartBody.Builder()
            .setType("multipart/form-data".toMediaType())
            .addFormDataPart("game", game)
            .build()
        val request = Request.Builder()
            .url("$server/gal")
            .header("User-Agent", UA)
            .post(body)
            .build()
        currentClient().newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val source = resp.body?.source() ?: throw IOException("空响应")
            val platforms = mutableListOf<SearchGalPlatform>()
            var done = false
            source.use {
                while (!done) {
                    val line = it.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val obj = try {
                        JsonParser.parseString(line).asJsonObject
                    } catch (e: Exception) {
                        continue
                    }
                    if (obj.get("done")?.asBoolean == true) {
                        done = true
                        break
                    }
                    obj.get("total")?.asInt?.let { onProgress(0, it) }
                    obj.getAsJsonObject("progress")?.let { p ->
                        onProgress(p.get("completed")?.asInt ?: 0, p.get("total")?.asInt ?: 0)
                    }
                    obj.getAsJsonObject("result")?.let { r ->
                        val platform = SearchGalPlatform(
                            name = r.get("name")?.asString ?: "未知平台",
                            color = r.get("color")?.asString.orEmpty(),
                            tags = r.getAsJsonArray("tags")?.mapNotNull { it.asString } ?: emptyList(),
                            items = r.getAsJsonArray("items")
                                ?.mapNotNull { el -> el.asJsonObject }
                                ?.mapNotNull { o ->
                                    val n = o.get("name")?.asString.orEmpty()
                                    val u = o.get("url")?.asString.orEmpty()
                                    if (n.isNotBlank() && u.isNotBlank()) SearchGalItem(n, u) else null
                                }
                                ?: emptyList(),
                        )
                        if (platform.items.isNotEmpty()) {
                            platforms += platform
                            onPlatform(platform)
                        }
                    }
                }
            }
            if (!done) throw IOException("搜索流中断")
            return platforms.distinctBy { it.name }
        }
    }
}
