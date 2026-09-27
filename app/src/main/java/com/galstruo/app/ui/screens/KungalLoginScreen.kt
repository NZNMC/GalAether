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
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.galstruo.app.data.kungal.KungalApi
import com.galstruo.app.data.kungal.KungalAuth
import com.galstruo.app.data.kungal.KungalUser
import com.google.gson.GsonBuilder
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
 * 用户数据在 pinia 的 KUNGalgameUser 存储对象里(特征是同时带 moemoepoint
 * 与 isCheckIn/dailyCheckIn 等专属字段),两个来源:
 * 1. 页面的 __NUXT_DATA__ 服务端渲染载荷;
 * 2. 页面运行时的 pinia 实时状态(Nuxt 3 挂在 window.__NUXT__ 上,
 *    客户端跳转后的数据在这里,靠它就能覆盖"回调页→首页"的内部跳转)。
 */
private const val EXTRACT_USER_JS = """
(function () {
  try {
    function scanPayload(arr) {
      function deref(v) {
        var g = 0;
        while (g++ < 12) {
          if (typeof v === 'number' && v >= 0 && v < arr.length) { v = arr[v]; continue; }
          if (Array.isArray(v) && v.length >= 2 && (v[0] === 'Ref' || v[0] === 'EmptyRef') &&
              typeof v[1] === 'number' && v[1] >= 0 && v[1] < arr.length) { v = arr[v[1]]; continue; }
          break;
        }
        return v;
      }
      function str(v) {
        var s = deref(v);
        return typeof s === 'string' ? s : '';
      }
      function avatarOf(v) {
        var a = deref(v);
        if (typeof a === 'string') return a;
        if (a && typeof a === 'object' && !Array.isArray(a)) {
          var u = deref(a.url);
          if (typeof u === 'string') return u;
          var h = deref(a.hash);
          if (typeof h === 'string') return h;
        }
        return '';
      }
      for (var i = 0; i < arr.length; i++) {
        var el = arr[i];
        if (!el || typeof el !== 'object' || Array.isArray(el)) continue;
        if (el.moemoepoint === undefined) continue;
        if (el.isCheckIn === undefined && el.dailyCheckIn === undefined && el.dailyToolsetUploadBytes === undefined) continue;
        var name = str(el.name);
        if (!name) continue;
        var avatar = avatarOf(el.avatar) || avatarOf(el.avatarMin);
        if (avatar && avatar.indexOf('/') === 0) avatar = 'https://www.kungal.com' + avatar;
        return { name: name, avatar: avatar, uid: str(el.sub) };
      }
      return null;
    }
    // 来源 1:当前页面的服务端渲染载荷
    var arr = window.__NUXT_DATA__;
    if (!arr || !Array.isArray(arr) || !arr.length) {
      var script = document.getElementById('__NUXT_DATA__');
      if (script) {
        try { arr = JSON.parse(script.textContent); } catch (e) { return null; }
      }
    }
    if (arr && Array.isArray(arr) && arr.length) {
      var fromPayload = scanPayload(arr);
      if (fromPayload) return fromPayload;
    }
    // 来源 2:pinia 实时状态(客户端跳转后也能读到)
    try {
      var nuxt = window.__NUXT__;
      var pinia = nuxt && nuxt.vueApp && nuxt.vueApp.config && nuxt.vueApp.config.globalProperties &&
                  nuxt.vueApp.config.globalProperties.${'$'}pinia;
      var st = pinia && pinia.state && pinia.state.value;
      var u = st && st.KUNGalgameUser;
      if (u && typeof u === 'object') {
        var n2 = typeof u.name === 'string' ? u.name : '';
        if (n2) {
          var a2 = (typeof u.avatar === 'string' && u.avatar) || (typeof u.avatarMin === 'string' && u.avatarMin) || '';
          if (a2 && a2.indexOf('/') === 0) a2 = 'https://www.kungal.com' + a2;
          return { name: n2, avatar: a2, uid: typeof u.sub === 'string' ? u.sub : '' };
        }
      }
    } catch (e) {}
    return null;
  } catch (e) { return null; }
})()
"""

/**
 * 异步兜底:在网页自己的环境里抓首页与用户接口(网页能看到全部会话 Cookie,
 * 包括应用侧 CookieManager 读不到的分区 Cookie),找到后写进 window.__galstruoUser。
 * 内置重试:登录 Cookie 落地需要时间,一次抓不到隔 2 秒再抓,每条路径最多 7 次;
 * 外层每轮注入时用时间戳防重,不会叠加出多套重试。
 * (旧版用 __galstruoFetching 一次性标记,第一次没抓到就永久放弃,这是缺陷)
 */
private const val EXTRACT_ASYNC_JS = """
(function () {
  try {
    if (window.__galstruoUser) return;
    // 不限制当前页域名:官网接口带跨域许可,即使页面跳到了别的网站也能抓
    var now = Date.now();
    if (now - (window.__galstruoLastFetch || 0) < 5000) return;
    window.__galstruoLastFetch = now;
    function findUser(t) {
      var m = t.match(/<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
      if (!m) return null;
      var arr = JSON.parse(m[1]);
      function deref(v) {
        var g = 0;
        while (g++ < 12) {
          if (typeof v === 'number' && v >= 0 && v < arr.length) { v = arr[v]; continue; }
          if (Array.isArray(v) && v.length >= 2 && (v[0] === 'Ref' || v[0] === 'EmptyRef') &&
              typeof v[1] === 'number' && v[1] >= 0 && v[1] < arr.length) { v = arr[v[1]]; continue; }
          break;
        }
        return v;
      }
      function str(v) {
        var s = deref(v);
        return typeof s === 'string' ? s : '';
      }
      function avatarOf(v) {
        var a = deref(v);
        if (typeof a === 'string') return a;
        if (a && typeof a === 'object' && !Array.isArray(a)) {
          var u = deref(a.url);
          if (typeof u === 'string') return u;
          var h = deref(a.hash);
          if (typeof h === 'string') return h;
        }
        return '';
      }
      for (var i = 0; i < arr.length; i++) {
        var el = arr[i];
        if (!el || typeof el !== 'object' || Array.isArray(el)) continue;
        if (el.moemoepoint === undefined) continue;
        if (el.isCheckIn === undefined && el.dailyCheckIn === undefined && el.dailyToolsetUploadBytes === undefined) continue;
        var name = str(el.name);
        if (!name) continue;
        var avatar = avatarOf(el.avatar) || avatarOf(el.avatarMin);
        if (avatar && avatar.indexOf('/') === 0) avatar = 'https://www.kungal.com' + avatar;
        return { name: name, avatar: avatar, uid: str(el.sub) };
      }
      return null;
    }
    function tryHomepage(attempt) {
      fetch('https://www.kungal.com/', { credentials: 'include' })
        .then(function (r) { return r.text(); })
        .then(function (t) {
          if (window.__galstruoUser) return;
          var u = findUser(t);
          if (u) { window.__galstruoUser = JSON.stringify(u); return; }
          if (attempt < 6) setTimeout(function () { tryHomepage(attempt + 1); }, 2000);
        })
        .catch(function () {});
    }
    function trySession(attempt) {
      fetch('https://www.kungal.com/api/user/session', { credentials: 'include' })
        .then(function (r) { return r.json(); })
        .then(function (j) {
          if (window.__galstruoUser) return;
          try {
            var u = j.user || (j.data && j.data.user);
            var n = u && (u.name || u.username);
            if (n) {
              var av = typeof u.avatar === 'string' ? u.avatar : '';
              window.__galstruoUser = JSON.stringify({ name: n, avatar: av, uid: String(u.uid || u.id || '') });
              return;
            }
          } catch (e) {}
          if (attempt < 6) setTimeout(function () { trySession(attempt + 1); }, 2000);
        })
        .catch(function () {});
    }
    tryHomepage(0);
    trySession(0);
  } catch (e) {}
})()
"""

/**
 * 自检脚本:全部来源都找不到用户时运行,把网页里的真实情况收集出来。
 * 除了当前页面,还会用网页自己的 Cookie 抓一次登录后的首页载荷来分析
 * (用户数据到底在不在、存在哪个位置、昵称头像的原始值、Cookie 名清单),
 * 结果写进 window.__galstruoDiag,应用轮询读出后显示在登录页底部供截图反馈。
 */
private const val DIAG_JS = """
(function () {
  try {
    function derefIn(arr, v) {
      var g = 0;
      while (g++ < 12) {
        if (typeof v === 'number' && v >= 0 && v < arr.length) { v = arr[v]; continue; }
        if (Array.isArray(v) && v.length >= 2 && (v[0] === 'Ref' || v[0] === 'EmptyRef') &&
            typeof v[1] === 'number' && v[1] >= 0 && v[1] < arr.length) { v = arr[v[1]]; continue; }
        break;
      }
      return v;
    }
    function analyze(arr) {
      var out = {};
      out.nuxtLen = arr ? arr.length : -1;
      out.matchedStoreIdx = -1;
      if (arr) {
        for (var i = 0; i < arr.length; i++) {
          var el = arr[i];
          if (el && typeof el === 'object' && !Array.isArray(el) && el.moemoepoint !== undefined &&
              (el.isCheckIn !== undefined || el.dailyCheckIn !== undefined)) {
            out.matchedStoreIdx = i;
            var nameD = derefIn(arr, el.name);
            var avD = derefIn(arr, el.avatar);
            var avmD = derefIn(arr, el.avatarMin);
            out.storeNameVal = typeof nameD === 'string' ? nameD : JSON.stringify(nameD);
            out.storeAvatarVal = (typeof avD === 'string' && avD) ? avD : JSON.stringify(avD);
            out.storeAvatarMinVal = (typeof avmD === 'string' && avmD) ? avmD : JSON.stringify(avmD);
            out.storeSubVal = String(derefIn(arr, el.sub)).slice(0, 40);
          }
        }
      }
      try {
        var nuxt = window.__NUXT__;
        var pinia = nuxt && nuxt.vueApp && nuxt.vueApp.config && nuxt.vueApp.config.globalProperties &&
                    nuxt.vueApp.config.globalProperties.${'$'}pinia;
        out.hasPinia = !!pinia;
        if (pinia && pinia.state && pinia.state.value) {
          out.storeNames = Object.keys(pinia.state.value);
          var u = pinia.state.value.KUNGalgameUser;
          if (u) {
            out.piniaUserName = typeof u.name === 'string' ? u.name : JSON.stringify(u.name);
            out.piniaAvatar = typeof u.avatar === 'string' ? u.avatar : JSON.stringify(u.avatar);
            out.piniaSub = String(u.sub).slice(0, 30);
          }
        }
      } catch (e) { out.piniaErr = String(e); }
      out.cookieNames = document.cookie.split(';').map(function (c) { return c.trim().split('=')[0]; }).filter(Boolean);
      return out;
    }
    var result = {};
    result.url = location.href;
    var cur = window.__NUXT_DATA__;
    if (!cur) {
      var script = document.getElementById('__NUXT_DATA__');
      if (script) {
        try { cur = JSON.parse(script.textContent); } catch (e) {}
      }
    }
    result.currentPage = analyze(cur);
    fetch('https://www.kungal.com/', { credentials: 'include' })
      .then(function (r) { return r.text(); })
      .then(function (t) {
        try {
          var m = t.match(/<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
          result.homepagePayload = analyze(m ? JSON.parse(m[1]) : null);
        } catch (e) { result.homepageErr = String(e); }
        window.__galstruoDiag = JSON.stringify(result);
      })
      .catch(function (e) { result.homepageErr = String(e); window.__galstruoDiag = JSON.stringify(result); });
    // 保险:fetch 万一被页面策略拦下,8 秒后也把已收集的部分写出来
    setTimeout(function () {
      if (!window.__galstruoDiag) {
        result.homepageErr = 'fetch 超时未返回';
        window.__galstruoDiag = JSON.stringify(result);
      }
    }, 8000);
  } catch (e) { window.__galstruoDiag = '{"topErr":"' + String(e) + '"}'; }
})()
"""

/** evaluateJavascript 回调拿到的是 JSON 编码的字符串;剥一层还原真实字符串 */
private fun unwrapJsString(v: String?): String? {
    val el = runCatching { JsonParser.parseString(v ?: "") }.getOrNull() ?: return null
    return when {
        el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString
        else -> (v ?: "")
    }
}

/** 解析提取脚本的返回:对象直接解析;字符串(异步结果经 JSON 字符串传递)剥一层再解析 */
private fun parseUserJson(raw: String?): KungalUser? {
    val el = runCatching { JsonParser.parseString(raw ?: "") }.getOrNull() ?: return null
    val obj = when {
        el.isJsonObject -> el.asJsonObject
        el.isJsonPrimitive && el.asJsonPrimitive.isString ->
            runCatching { JsonParser.parseString(el.asString).asJsonObject }.getOrNull() ?: return null
        else -> return null
    }
    val name = obj.get("name")?.asString ?: return null
    return KungalUser(
        id = obj.get("uid")?.asString?.toLongOrNull() ?: 0L,
        name = name,
        avatar = obj.get("avatar")?.asString.orEmpty(),
    )
}

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
    // 底部状态行:登录前提示 / 读取进度 / 全部来源都找不到用户时的自检信息(供截图反馈)
    var statusText by remember { mutableStateOf("") }
    // 当前网页地址(用于判断用户是否停在登录表单页,给"别急着退出"的提示)
    var currentUrl by remember { mutableStateOf("") }

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

    /** 提取到用户后:保存到本地并触发登录成功流程 */
    fun gotUser(user: KungalUser) {
        if (success) return
        scope.launch {
            KungalAuth.applyUserFromPage(user)
            success = true
        }
    }

    /**
     * 轮询提取:登录流程是"回调页 → 网页内部跳转到首页",页面数据不是一次性就绪的
     * (客户端跳转不触发页面加载事件,只靠 onPageFinished 抓不到首页)。
     * 每 1.5 秒扫一次当前页面(SSR 载荷 + pinia 实时状态),并注入网页内抓首页的异步兜底。
     */
    fun startExtraction(view: WebView) {
        if (extracting || success) return
        extracting = true
        scope.launch {
            repeat(20) { i ->
                statusText = "正在读取你的账号信息…(${i + 1}/20)"
                // 应用侧直读官网的用户 Cookie(最可靠:不依赖接口,也不依赖网页当前状态)
                CookieManager.getInstance().getCookie("https://www.kungal.com")
                    ?.let { KungalApi.parseKungalUserCookie(it) }
                    ?.let { gotUser(it) }
                // 异步兜底每次注入(页面跳转后 window 会重置;脚本内部自带重试和时间戳防重)
                view.evaluateJavascript(EXTRACT_ASYNC_JS, null)
                view.evaluateJavascript(EXTRACT_USER_JS) { v ->
                    parseUserJson(v)?.let { gotUser(it) }
                }
                view.evaluateJavascript("window.__galstruoUser || null") { v ->
                    parseUserJson(v)?.let { gotUser(it) }
                }
                delay(1200)
                if (success) return@launch
            }
            // 全部来源都没找到:跑自检脚本(脚本异步收集,写进 window.__galstruoDiag,轮询读出)
            val allCookies = CookieManager.getInstance().getCookie("https://www.kungal.com").orEmpty()
            val cookieNames = allCookies.split(";")
                .mapNotNull { it.trim().split("=").firstOrNull() }.joinToString(",")
                .ifBlank { "(无)" }
            // 连同用户 Cookie 的原始内容一起显示(前 200 字),截图后就能看到真实格式
            val kguValue = allCookies.split(";")
                .firstOrNull { it.trim().startsWith("KUNGalgameUser=") }
                ?.substringAfter("=")?.take(200) ?: "(无)"
            view.evaluateJavascript(DIAG_JS, null)
            var diagJson: String? = null
            repeat(6) {
                view.evaluateJavascript("window.__galstruoDiag || null") { v ->
                    unwrapJsString(v)?.let { diagJson = it }
                }
                delay(1000)
                if (diagJson != null) return@repeat
            }
            val pretty = diagJson?.let { raw ->
                runCatching {
                    GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(raw))
                }.getOrDefault(raw)
            } ?: "自检无结果,请再试一次并多等几秒"
            statusText = "没读到账号信息(应用看到 Cookie:[$cookieNames])\nKUNGalgameUser 值前200字:$kguValue\n$pretty"
            extracting = false
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
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                if (url != null) currentUrl = url
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                // 回到鲲galgame 主站时(登录流程会跳回主站)立即检查一次:
                                // 接口验证 + 页面数据轮询提取双保险
                                if (url?.startsWith("https://www.kungal.com") == true && view != null) {
                                    checkLogin()
                                    startExtraction(view)
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
            // 底部状态行:登录表单页给"别急着退出"提示;读取中显示进度;失败显示自检信息(截图反馈用)
            val hint = if (!success && statusText.isBlank() && currentUrl.contains("account.nextmoe.com")) {
                "登录成功后先别急着退出,应用会自动读取你的账号信息(约需几秒)"
            } else null
            if (!success && (statusText.isNotBlank() || hint != null)) {
                Text(
                    text = statusText.ifBlank { hint.orEmpty() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(10.dp),
                )
            }
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
