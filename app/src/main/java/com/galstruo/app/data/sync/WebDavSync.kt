package com.galstruo.app.data.sync

import com.galstruo.app.data.network.NetConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WebDAV 同步:把备份文件上传/下载到任意 WebDAV 网盘(坚果云等)。
 * 通用协议,不绑死某一家服务:
 * - 坚果云:注册 jianguoyun.com → 账户信息 → 安全选项 → 第三方应用管理
 *   → 添加应用密码 → 地址填 https://dav.jianguoyun.com/dav/
 * - 其他任何支持 WebDAV 的网盘同样可用,地址填它的 WebDAV 入口即可。
 */
object WebDavSync {

    /** 云盘里存放备份的文件名(固定,所有设备共用同一份) */
    const val FILE_NAME = "GalAether-backup.json"

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

    /** 地址统一补全:填的是文件夹就拼上文件名,填完整文件路径就直接用 */
    private fun fileUrl(url: String): String {
        val base = url.trim().trimEnd('/')
        return if (base.endsWith(FILE_NAME)) base else "$base/$FILE_NAME"
    }

    private fun request(url: String, user: String, password: String): Request.Builder =
        Request.Builder()
            .url(fileUrl(url))
            .header("Authorization", Credentials.basic(user, password))
            .header("User-Agent", "GalAether")

    /** 上传备份文件(覆盖式写入) */
    suspend fun push(url: String, user: String, password: String, json: String): Unit =
        withContext(Dispatchers.IO) {
            val req = request(url, user, password)
                .put(json.toRequestBody())
                .build()
            val resp = currentClient().newCall(req).execute()
            if (!resp.isSuccessful) throw IOException("WebDAV 上传失败(状态码 ${resp.code})")
        }

    /** 下载备份内容;云端还没有备份文件(404)时返回 null */
    suspend fun fetch(url: String, user: String, password: String): String? =
        withContext(Dispatchers.IO) {
            val resp = currentClient().newCall(request(url, user, password).build()).execute()
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        }

    /** 测试连接:成功返回"连接成功"提示,失败返回原因(不抛异常,给界面弹提示用) */
    suspend fun test(url: String, user: String, password: String): String =
        withContext(Dispatchers.IO) {
            try {
                val req = request(url, user, password)
                    .method("PROPFIND", null)
                    .header("Depth", "0")
                    .build()
                val resp = currentClient().newCall(req).execute()
                when (resp.code) {
                    in 200..299 -> "连接成功,可以开始同步"
                    401 -> "账号或密码不正确"
                    404 -> "服务器地址不对,找不到 WebDAV 目录"
                    else -> "连接失败(状态码 ${resp.code}),请检查地址"
                }
            } catch (e: Exception) {
                "连接失败:${e.message ?: "网络不可用"}"
            }
        }
}
