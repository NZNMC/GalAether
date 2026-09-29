package com.galstruo.app.data.kungal

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 鲲galgame 登录用户信息 */
data class KungalUser(
    val id: Long,
    val name: String,
    val avatar: String = "",
    /** 萌汁点数(官网积分,下载资源用) */
    val moemoepoint: Int = 0,
    /** 今日是否已签到 */
    val isCheckIn: Boolean = false,
)

/**
 * 鲲galgame 登录状态管理。
 * 登录方式:App 内置浏览器(WebView)打开 kungal.com,和平时在浏览器登录一模一样,
 * 登录成功后 WebView 会把会话 Cookie 存在系统 CookieManager 里(重启 App 也在)。
 *
 * 官网 2026-09 改版后旧接口 /api/user 退役,登录验证与用户信息有三个来源,按顺序:
 * 1. 官方接口(/api/user/session → /api/user 兜底);
 * 2. 登录网页的页面数据里直接读(登录时 WebView 提取);
 * 3. 本地暂存的上次登录信息(接口退役期间的兜底,重启也能显示)。
 */
object KungalAuth {

    private val _user = MutableStateFlow<KungalUser?>(null)

    /** 当前登录用户;null 表示未登录 */
    val user: StateFlow<KungalUser?> = _user

    /** 会话 Cookie(发给 kungal.com /api 接口用) */
    @Volatile
    private var cookies: String? = null

    @Volatile
    private var store: KungalUserStore? = null

    val isLoggedIn: Boolean get() = _user.value != null

    fun cookie(): String? = cookies

    /** 应用启动时调用:初始化本地暂存,并把上次登录的用户信息恢复到界面 */
    fun init(context: Context) {
        if (store != null) return
        val s = KungalUserStore(context.applicationContext)
        store = s
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { _user.value = s.load() }
        }
    }

    /** 从 WebView 的 CookieManager 读取会话 Cookie,并用官方接口验证;
     *  接口不可用(官网退役旧接口)时退回本地暂存的用户信息 */
    suspend fun refresh() {
        val cookie = CookieManager.getInstance().getCookie("https://www.kungal.com")
            ?.takeIf { it.isNotBlank() }
        val apiUser = if (cookie == null) null else KungalApi.currentUser(cookie)
        cookies = if (apiUser != null) cookie else null
        val user = apiUser ?: store?.load()
        if (apiUser != null && store != null) store?.save(apiUser)
        _user.value = user
    }

    /** 登录网页里读到的用户信息(官网接口退役后的主要来源),保存并立即显示 */
    suspend fun applyUserFromPage(user: KungalUser) {
        _user.value = user
        cookies = CookieManager.getInstance().getCookie("https://www.kungal.com")
            ?.takeIf { it.isNotBlank() }
        store?.save(user)
    }

    /** 会话失效(接口返回 401/登录过期)时调用:只清 Cookie,显示退回本地暂存 */
    fun expire() {
        cookies = null
    }

    /** 退出登录:清掉本地 Cookie、暂存信息并通知服务器(尽力而为,失败不影响本地退出) */
    suspend fun logout() {
        val c = cookies
        cookies = null
        _user.value = null
        store?.clear()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        if (!c.isNullOrBlank()) runCatching { KungalApi.logout(c) }
    }
}
