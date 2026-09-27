package com.galstruo.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.galstruo.app.BuildConfig
import com.galstruo.app.data.kungal.KungalAuth
import com.galstruo.app.ui.components.HistoryIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenThemeSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenKungalLogin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by KungalAuth.user.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 每次进入「我的」页都刷新登录状态(WebView 的 Cookie 重启后仍在,这里恢复)
    LaunchedEffect(Unit) { KungalAuth.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("我的") })
        if (user == null) {
            ListItem(
                headlineContent = { Text("鲲galgame 账号") },
                supportingContent = { Text("登录后网盘资源更全,含提取码/解压密码") },
                leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                modifier = Modifier.clickable { onOpenKungalLogin() },
            )
        } else {
            ListItem(
                headlineContent = { Text(user?.name.orEmpty()) },
                supportingContent = { Text("已登录鲲galgame") },
                leadingContent = {
                    if (user?.avatar?.isNotBlank() == true) {
                        AsyncImage(
                            model = user?.avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                },
                trailingContent = {
                    TextButton(onClick = { showLogoutConfirm = true }) { Text("退出登录") }
                },
            )
        }
        ListItem(
            headlineContent = { Text("设置") },
            supportingContent = { Text("主题 · 深色模式 · 内容 · 数据与存储 · 网络代理") },
            leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier.clickable { onOpenThemeSettings() },
        )
        ListItem(
            headlineContent = { Text("浏览历史") },
            supportingContent = { Text("打开过的游戏自动记录") },
            leadingContent = { Icon(HistoryIcon, contentDescription = null) },
            modifier = Modifier.clickable { onOpenHistory() },
        )
        ListItem(
            headlineContent = { Text("关于") },
            supportingContent = { Text("GalAether ${BuildConfig.VERSION_NAME} · 点击查看项目主页") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            modifier = Modifier.clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NZNMC/GalAether"))
                )
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("确定退出鲲galgame 账号吗?退出后网盘资源将回到网页解析方式。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch { KungalAuth.logout() }
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            },
        )
    }
}
