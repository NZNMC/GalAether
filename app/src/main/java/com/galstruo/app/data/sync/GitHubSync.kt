package com.galstruo.app.data.sync

import com.galstruo.app.data.network.NetConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub 同步:设备码授权登录 + 私有 Gist 存储备份数据。
 *
 * 为什么用"设备码登录":普通 OAuth 需要把 Client Secret 编译进应用,
 * 公开仓库里任何人都能抄走冒充这个应用。设备码流程只需要 Client ID
 * (本来就是公开的),用户在自己手机上输入配对码完成授权,全程不泄露密钥。
 *
 * CLIENT_ID 是占位符:需要用户在 GitHub 后台创建 OAuth App(勾选
 * "Enable Device Flow"),把返回的 Client ID 填到这里(设置页登录前提示)。
 */
object GitHubSync {

    // 用户本人创建的 OAuth App(已勾选 Enable Device Flow)。
    // Client ID 本来就是公开信息(随应用分发),设备码流程的安全靠的是
    // 不携带 Client Secret,而不是藏住 ID。
    const val CLIENT_ID = "Ov23libiPRUOVWcvigM2"

    private const val OAUTH_BASE = "https://github.com"
    private const val API_BASE = "https://api.github.com"

    /** Gist 里存放备份的文件名(固定,所有设备共用同一份) */
    const val GIST_FILE = "GalAether-backup.json"

    private const val UA = "GalAether"

    /** 设备码流程的第一步返回:配对码 + 轮询间隔 */
    data class DeviceFlow(
        val deviceCode: String,
        val userCode: String,        // 用户要在浏览器里输入的配对码
        val verificationUri: String, // 用户要去打开的网页
        val intervalSec: Int,        // 建议轮询间隔(秒)
        val expiresInSec: Int,       // 配对码有效期(秒)
    )

    private fun buildClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    private fun oauthRequest(path: String, body: FormBody): Request =
        Request.Builder()
            .url("$OAUTH_BASE$path")
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .post(body)
            .build()

    private fun apiRequest(path: String, token: String, method: String = "GET", bodyJson: String? = null): Request {
        val builder = Request.Builder()
            .url("$API_BASE$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", UA)
            .header("Authorization", "Bearer $token")
        when (method) {
            "POST" -> builder.post((bodyJson ?: "").toRequestBody("application/json".toMediaType()))
            "PATCH" -> builder.patch((bodyJson ?: "").toRequestBody("application/json".toMediaType()))
        }
        return builder.build()
    }

    /** 第一步:向 GitHub 申请设备码,返回配对码信息 */
    suspend fun requestDeviceCode(): DeviceFlow = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "gist")
            .build()
        val resp = currentClient().newCall(oauthRequest("/login/device/code", body)).execute()
        val text = resp.body?.string().orEmpty()
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IOException("GitHub 返回异常(${resp.code})")
        if (!resp.isSuccessful || obj.get("device_code") == null) {
            val err = obj.get("error_description")?.asString
                ?: obj.get("error")?.asString
                ?: "请求失败(${resp.code})"
            throw IOException(err)
        }
        DeviceFlow(
            deviceCode = obj.get("device_code").asString,
            userCode = obj.get("user_code").asString,
            verificationUri = obj.get("verification_uri").asString.ifBlank { "https://github.com/login/device" },
            intervalSec = obj.get("interval")?.asInt ?: 5,
            expiresInSec = obj.get("expires_in")?.asInt ?: 900,
        )
    }

    /**
     * 第二步:轮询 GitHub,直到用户在浏览器里输入配对码并同意授权。
     * 成功后返回 access_token;用户超时未操作 / 拒绝授权时抛异常。
     */
    suspend fun pollAccessToken(flow: DeviceFlow): String = withContext(Dispatchers.IO) {
        var token: String? = null
        withTimeout((flow.expiresInSec + 30).toLong() * 1000) {
            var waitSec = flow.intervalSec
            while (token == null) {
                val body = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("device_code", flow.deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()
                val resp = currentClient().newCall(oauthRequest("/login/oauth/access_token", body)).execute()
                val text = resp.body?.string().orEmpty()
                val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                    ?: throw IOException("GitHub 返回异常(${resp.code})")
                token = obj.get("access_token")?.asString
                if (token.isNullOrBlank()) {
                    when (obj.get("error")?.asString) {
                        "authorization_pending" -> delay(waitSec * 1000L)  // 还没操作,继续等
                        "slow_down" -> {                                    // 轮询太频繁,GitHub 要求放慢
                            waitSec += 5
                            delay(waitSec * 1000L)
                        }
                        "expired_token" -> throw IOException("配对码已过期,请重新登录")
                        "access_denied" -> throw IOException("已取消授权")
                        else -> throw IOException("登录失败:${obj.get("error")?.asString ?: "未知错误"}")
                    }
                }
            }
        }
        token ?: throw IOException("登录失败:未收到授权")
    }

    /** 令牌对应的 GitHub 用户信息(登录后显示昵称与头像) */
    data class GitHubUser(val login: String, val avatarUrl: String)

    suspend fun getUser(token: String): GitHubUser = withContext(Dispatchers.IO) {
        val resp = currentClient().newCall(apiRequest("/user", token)).execute()
        val text = resp.body?.string().orEmpty()
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IOException("获取用户信息失败(${resp.code})")
        GitHubUser(
            login = obj.get("login")?.asString ?: "GitHub 用户",
            avatarUrl = obj.get("avatar_url")?.asString.orEmpty(),
        )
    }

    /**
     * 上传备份到私有 Gist:已有 gistId 就更新内容,没有就新建,返回 gistId。
     * 新建的 Gist 是私密的(public=false),只有登录者本人能看到。
     */
    suspend fun push(token: String, gistId: String?, json: String): String = withContext(Dispatchers.IO) {
        val files = """{"$GIST_FILE":{"content":${escapeJson(json)}}}"""
        if (gistId.isNullOrBlank()) {
            val body = """{"description":"GalAether 数据备份(应用自动同步,请勿手动修改)","public":false,"files":$files}"""
            val resp = currentClient().newCall(apiRequest("/gists", token, "POST", body)).execute()
            val text = resp.body?.string().orEmpty()
            val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: throw IOException("创建云端备份失败(${resp.code})")
            obj.get("id")?.asString ?: throw IOException("创建云端备份失败:${obj.get("message")?.asString ?: resp.code}")
        } else {
            val body = """{"files":$files}"""
            val resp = currentClient().newCall(apiRequest("/gists/$gistId", token, "PATCH", body)).execute()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject.get("message")?.asString
                }.getOrNull() ?: "状态码 ${resp.code}"
                throw IOException("更新云端备份失败:$msg")
            }
            gistId
        }
    }

    /** 下载云端备份内容;还没有备份(Gist 不存在)时返回 null */
    suspend fun fetch(token: String, gistId: String?): String? = withContext(Dispatchers.IO) {
        if (gistId.isNullOrBlank()) return@withContext null
        val resp = currentClient().newCall(apiRequest("/gists/$gistId", token)).execute()
        if (resp.code == 404) return@withContext null
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) return@withContext null
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: return@withContext null
        return@withContext obj.getAsJsonObject("files")
            ?.getAsJsonObject(GIST_FILE)
            ?.get("content")?.asString
    }

    /** 把备份 JSON 变成 JSON 字符串字面量(Gist 内容里再嵌一层 JSON) */
    private fun escapeJson(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
