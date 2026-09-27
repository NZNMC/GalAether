package com.galstruo.app.ui.screens

import android.annotation.SuppressLint
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 鲲galgame 登录页:App 内置浏览器打开 kungal.com 登录页,
 * 和平时在浏览器登录一模一样(账号密码只发给鲲galgame 官网)。
 * 登录成功后自动检测(轮询 Cookie + /api/user 验证),随后自动返回上一页。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KungalLoginScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var success by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    /** 验证登录状态:CookieManager 里有会话 Cookie 时,用 /api/user 确认 */
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
                                // 回到鲲galgame 主站时(登录流程会跳回主站)立即检查一次
                                if (url?.startsWith("https://www.kungal.com") == true) {
                                    checkLogin()
                                }
                            }
                        }
                        loadUrl("https://www.kungal.com/login")
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
