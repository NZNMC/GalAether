package com.galstruo.app.data.kungal

import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 鲲galgame 登录用户信息 */
data class KungalUser(
    val id: Long,
    val name: String,
    val avatar: String = "",
)

/**
 * 鲲galgame 登录状态管理。
 * 登录方式:App 内置浏览器(WebView)打开 kungal.com,和平时在浏览器登录一模一样,
 * 登录成功后 WebView 会把会话 Cookie 存在系统 CookieManager 里(重启 App 也在),
 * 这里定期把 Cookie 读出来,并用官方接口 /api/user 验证登录是否有效。
 */
object KungalAuth {

    private val _user = MutableStateFlow<KungalUser?>(null)

    /** 当前登录用户;null 表示未登录 */
    val user: StateFlow<KungalUser?> = _user

    /** 会话 Cookie(发给 kungal.com /api 接口用) */
    @Volatile
    private var cookies: String? = null

    val isLoggedIn: Boolean get() = _user.value != null

    fun cookie(): String? = cookies

    /** 从 WebView 的 CookieManager 读取会话 Cookie,并用 /api/user 验证 */
    suspend fun refresh() {
        val cookie = CookieManager.getInstance().getCookie("https://www.kungal.com")
            ?.takeIf { it.isNotBlank() }
        val user = if (cookie == null) null else KungalApi.currentUser(cookie)
        cookies = if (user == null) null else cookie
        _user.value = user
    }

    /** 会话失效(接口返回 401/登录过期)时调用 */
    fun expire() {
        cookies = null
        _user.value = null
    }

    /** 退出登录:清掉本地 Cookie 并通知服务器(尽力而为,失败不影响本地退出) */
    suspend fun logout() {
        val c = cookies
        cookies = null
        _user.value = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        if (!c.isNullOrBlank()) runCatching { KungalApi.logout(c) }
    }
}
