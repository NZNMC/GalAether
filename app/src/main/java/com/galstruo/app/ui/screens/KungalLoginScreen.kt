package com.galstruo.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.galstruo.app.data.kungal.KungalAuth
import com.galstruo.app.data.kungal.KungalUser
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 官网 2026-09-27 改版后 /login 已不存在,登录改为首页弹窗 + OAuth 跳转。
 * 这里直接复刻官网登录按钮的完整流程:
 * 1. 在 kungal.com 种下 oauth_state / oauth_code_verifier / oauth_return_to 三个 Cookie(回调页校验用);
 * 2. 打开 nextmoe 统一登录的授权地址(自动跳转到登录页);
 * 3. 登录成功后跳回 kungal.com,自动检测(轮询 Cookie + /api/user 验证),随后自动返回上一页。
 */
/** nextmoe 统一登录参数(取自官网前端配置,与官网登录按钮完全一致) */
private const val KUNGAL_OAUTH_SERVER = "https://account.nextmoe.com/api/v1"
private const val KUNGAL_CLIENT_ID = "4ed9bc99ec0a789a4796b83e22bd84c5"
private const val KUNGAL_REDIRECT_URI = "https://www.kungal.com/auth/callback"
private const val KUNGAL_SCOPE =
    "openid profile preferences catalog:read catalog:edit playtime:read playtime:write folder:read folder:write"

/**
 * 在登录后的网页里提取当前用户昵称与头像。
 * 官网 2026-09 改版后旧 /api/user 接口退役,没法再靠接口拿用户信息;
 * 但官网页面是服务端渲染的,登录用户的数据就在页面的 __NUXT_DATA__ 里,
 * 扫描其中"同时带 name 和 avatar 字段"的对象即为当前用户。
 */
private const val EXTRACT_USER_JS = """
(function () {
  try {
    var arr = window.__NUXT_DATA__;
    if (!arr || !Array.isArray(arr) || !arr.length) {
      var script = document.getElementById('__NUXT_DATA__');
      if (!script) return null;
      try { arr = JSON.parse(script.textContent); } catch (e) { return null; }
    }
    function deref(v) {
      var g = 0;
      while (typeof v === 'number' && v >= 0 && v < arr.length && g++ < 6) v = arr[v];
      return v;
    }
    function str(v) {
      var s = deref(v);
      return typeof s === 'string' ? s : '';
    }
    var name = '', avatar = '', uid = 0;
    // 第一轮:同时带 name 和 avatar 字段的对象(最像当前登录用户)
    for (var i = 0; i < arr.length; i++) {
      var el = arr[i];
      if (!el || typeof el !== 'object' || Array.isArray(el)) continue;
      if (el.avatar === undefined || el.avatar === null) continue;
      var n = str(el.name) || str(el.username) || str(el.nickname);
      var a = str(el.avatar);
      if (n && a && /^https?:\/\/|\//.test(a)) { name = n; avatar = a; uid = el.uid | 0; break; }
    }
    // 第二轮:没有头像时,认带用户专属字段(moemoepoint/role/bio)的对象
    if (!name) {
      for (var j = 0; j < arr.length; j++) {
        var o = arr[j];
        if (!o || typeof o !== 'object' || Array.isArray(o)) continue;
        if (o.moemoepoint === undefined && o.role === undefined && o.bio === undefined) continue;
        var nn = str(o.name) || str(o.username);
        if (nn) { name = nn; avatar = str(o.avatar); uid = o.uid | 0; break; }
      }
    }
    if (!name) return null;
    if (avatar && avatar.indexOf('/') === 0) avatar = 'https://www.kungal.com' + avatar;
    return { name: name, avatar: avatar, uid: uid };
  } catch (e) { return null; }
})()
"""

private fun randomToken(bytes: Int = 32): String {
    val buf = ByteArray(bytes)
    SecureRandom().nextBytes(buf)
    return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

/** PKCE S256 校验码:Base64URL(SHA256(verifier)) */
private fun sha256Challenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

/** 拼出与官网一致的 OAuth 授权地址(打开后自动跳转到 nextmoe 登录页) */
private fun buildAuthorizeUrl(verifier: String, state: String): String {
    val params = mapOf(
        "client_id" to KUNGAL_CLIENT_ID,
        "redirect_uri" to KUNGAL_REDIRECT_URI,
        "response_type" to "code",
        "scope" to KUNGAL_SCOPE,
        "state" to state,
        "code_challenge" to sha256Challenge(verifier),
        "code_challenge_method" to "S256",
    )
    return "$KUNGAL_OAUTH_SERVER/oauth/authorize?" + params.entries.joinToString("&") {
        "${it.key}=${Uri.encode(it.value)}"
    }
}
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KungalLoginScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var success by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }

    /** 验证登录状态:CookieManager 里有会话 Cookie 时,用官方接口确认 */
    fun checkLogin() {
        if (checking || success) return
        checking = true
        scope.launch {
            // 登录回调页会先向服务器提交再跳首页,Cookie 落地需要一点时间,轮询几次
            repeat(8) {
                KungalAuth.refresh()
                if (KungalAuth.isLoggedIn) {
                    success = true
                    checking = false
                    return@launch
                }
                delay(600)
            }
            checking = false
        }
    }

    /** 兜底验证:旧接口退役后,从登录网页的页面数据里直接读用户昵称/头像 */
    fun extractUser(view: WebView) {
        if (extracting || success) return
        extracting = true
        view.evaluateJavascript(EXTRACT_USER_JS) { value ->
            val obj = runCatching { JsonParser.parseString(value ?: "").asJsonObject }.getOrNull()
            val name = obj?.get("name")?.asString
            if (name.isNullOrBlank()) {
                extracting = false
                return@evaluateJavascript
            }
            val user = KungalUser(
                id = obj.get("uid")?.asLong ?: 0L,
                name = name,
                avatar = obj.get("avatar")?.asString.orEmpty(),
            )
            scope.launch {
                KungalAuth.applyUserFromPage(user)
                success = true
                extracting = false
            }
        }
    }

    // 登录成功 → 短暂停留展示提示 → 自动返回
    LaunchedEffect(success) {
        if (success) {
            delay(900)
            onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (success) "登录成功" else "登录鲲galgame") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 登录走 account.nextmoe.com 统一登录,跨域跳转需要允许第三方 Cookie
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                // 回到鲲galgame 主站时(登录流程会跳回主站)立即检查一次:
                                // 接口验证 + 页面数据提取双保险
                                if (url?.startsWith("https://www.kungal.com") == true && view != null) {
                                    checkLogin()
                                    extractUser(view)
                                }
                            }
                        }
                        // 官网登录按钮跳转前会种校验 Cookie(回调页要用),这里照做再打开授权页
                        val verifier = randomToken()
                        val state = randomToken(16)
                        CookieManager.getInstance().apply {
                            setCookie(
                                "https://www.kungal.com",
                                "oauth_code_verifier=$verifier; Path=/; Secure",
                            )
                            setCookie(
                                "https://www.kungal.com",
                                "oauth_state=$state; Path=/; Secure",
                            )
                            setCookie(
                                "https://www.kungal.com",
                                "oauth_return_to=https://www.kungal.com/; Path=/; Secure",
                            )
                            flush()
                        }
                        loadUrl(buildAuthorizeUrl(verifier, state))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // 登录成功遮罩:覆盖网页,给出明确反馈
            if (success) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("已登录鲲galgame", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "正在返回…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
