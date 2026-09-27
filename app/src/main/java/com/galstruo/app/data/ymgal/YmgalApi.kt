package com.galstruo.app.data.ymgal

import com.galstruo.app.data.network.NetConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 月幕galgame 开放接口客户端(官方公开凭据,仅限非商业用途)
 * 规则:https://www.ymgal.games/developer
 */
class YmgalApi {

    private fun buildClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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

    private val gson = Gson()

    private var token: String? = null
    private var tokenExpireAt = 0L

    private fun ensureToken() {
        if (token != null && System.currentTimeMillis() < tokenExpireAt) return
        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()
        val request = Request.Builder()
            .url("$BASE_URL/oauth/token")
            .post(body)
            .build()
        currentClient().newCall(request).execute().use { resp ->
            val json = resp.body?.string().orEmpty()
            val tokenResp = gson.fromJson(json, TokenResponse::class.java)
            token = tokenResp.access_token
                ?: throw IOException("获取月幕访问令牌失败: ${json.take(200)}")
            // 提前一分钟过期,留出安全余量
            tokenExpireAt = System.currentTimeMillis() + (tokenResp.expires_in ?: 300) * 1000L - 60_000L
        }
    }

    private fun executeGet(path: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val request = Request.Builder()
            .url("$BASE_URL$path?$query")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json;charset=utf-8")
            .header("version", "1")
            .get()
            .build()
        currentClient().newCall(request).execute().use { resp ->
            val json = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${json.take(200)}")
            return json
        }
    }

    /** 带 token 发起请求;接口报错时刷新 token 重试一次 */
    private fun get(path: String, params: Map<String, String>): String {
        ensureToken()
        var json = executeGet(path, params)
        var resp = gson.fromJson(json, RawResponse::class.java)
        if (resp?.success != true) {
            token = null  // 强制刷新 token 后重试一次
            ensureToken()
            json = executeGet(path, params)
            resp = gson.fromJson(json, RawResponse::class.java)
            if (resp?.success != true) {
                throw IOException("月幕接口错误: ${resp?.msg ?: json.take(200)}")
            }
        }
        return json
    }

    private inline fun <reified T> parseData(json: String): T? =
        gson.fromJson<ApiResponse<T>>(json, object : TypeToken<ApiResponse<T>>() {}.type).data

    /** 所有网络调用切换到 IO 线程,避免阻塞界面 */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    /** 按关键词搜索游戏列表(分页) */
    suspend fun searchGames(keyword: String, page: Int, pageSize: Int = 20): SearchPage = io {
        val json = get("/open/archive/search-game", mapOf(
            "mode" to "list",
            "keyword" to keyword,
            "pageNum" to page.toString(),
            "pageSize" to pageSize.toString(),
        ))
        parseData<SearchPage>(json) ?: SearchPage()
    }

    /** 查询游戏详情 */
    suspend fun gameDetail(gid: Long): GameDetail = io {
        val json = get("/open/archive", mapOf("gid" to gid.toString()))
        parseData<GameDetailData>(json)?.game ?: GameDetail()
    }

    /** 查询发行日期区间内的游戏(区间最大 50 天) */
    suspend fun gamesBetween(startDate: String, endDate: String): List<GameItem> = io {
        val json = get("/open/archive/game", mapOf(
            "releaseStartDate" to startDate,
            "releaseEndDate" to endDate,
        ))
        parseData<List<GameItem>>(json) ?: emptyList()
    }

    /** 随机游戏列表(数量 1~10) */
    suspend fun randomGames(num: Int): List<GameItem> = io {
        val json = get("/open/archive/random-game", mapOf("num" to num.toString()))
        parseData<List<GameItem>>(json) ?: emptyList()
    }

    private data class TokenResponse(
        val access_token: String? = null,
        val expires_in: Int? = null,
    )

    private class RawResponse(val success: Boolean = false, val msg: String? = null)

    private class ApiResponse<T>(val success: Boolean = false, val msg: String? = null, val data: T? = null)

    companion object {
        const val BASE_URL = "https://www.ymgal.games"
        const val CLIENT_ID = "ymgal"
        const val CLIENT_SECRET = "luna0327"
    }
}
