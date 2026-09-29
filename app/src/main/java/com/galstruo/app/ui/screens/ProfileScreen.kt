package com.galstruo.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.kungal.KungalAuth
import com.galstruo.app.data.local.BackupManager
import com.galstruo.app.ui.components.HistoryIcon
import com.galstruo.app.ui.components.pressScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    settings: SettingsStore,
    onOpenThemeSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenKungalLogin: () -> Unit,
    uiSettings: UiSettings,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by KungalAuth.user.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var ghMenuOpen by remember { mutableStateOf(false) }
    var showGhLogout by remember { mutableStateOf(false) }

    // 数据备份:导出到下载目录 / 从备份文件导入(系统文件选择器)
    var backupBusy by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !backupBusy) {
            scope.launch {
                backupBusy = true
                Toast.makeText(context, BackupManager.import(context, uri), Toast.LENGTH_LONG).show()
                backupBusy = false
            }
        }
    }

    // 每次进入「我的」页都刷新登录状态(WebView 的 Cookie 重启后仍在,这里恢复)
    LaunchedEffect(Unit) { KungalAuth.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("我的") })
        if (user == null) {
            ListItem(
                headlineContent = { Text("鲲galgame 账号") },
                supportingContent = { Text("登录后网盘资源更全,含提取码/解压密码") },
                leadingContent = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
                modifier = Modifier
                    .pressScale()
                    .clickable { onOpenKungalLogin() },
            )
        } else {
            ListItem(
                headlineContent = { Text(user?.name.orEmpty()) },
                supportingContent = {
                    Text(
                        "萌汁 ${user?.moemoepoint ?: 0} · ${
                            if (user?.isCheckIn == true) "今日已签到" else "今日未签到"
                        }",
                    )
                },
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
                            Icons.Rounded.AccountCircle,
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
        // GitHub 账号:登录云同步后显示头像与昵称,点击弹出小菜单(打开同步设置 / 退出登录)
        if (uiSettings.githubUser.isNotBlank()) {
            Box {
                ListItem(
                    headlineContent = { Text(uiSettings.githubUser) },
                    supportingContent = { Text("已登录 GitHub · 数据自动备份到云端") },
                    leadingContent = {
                        if (uiSettings.githubAvatar.isNotBlank()) {
                            AsyncImage(
                                model = uiSettings.githubAvatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Icon(
                                Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    },
                    modifier = Modifier
                        .pressScale()
                        .clickable { ghMenuOpen = true },
                )
                DropdownMenu(
                    expanded = ghMenuOpen,
                    onDismissRequest = { ghMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("打开同步设置") },
                        onClick = {
                            ghMenuOpen = false
                            onOpenThemeSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("退出登录") },
                        onClick = {
                            ghMenuOpen = false
                            showGhLogout = true
                        },
                    )
                }
            }
        }
        ListItem(
            headlineContent = { Text("设置") },
            supportingContent = { Text("主题 · 深色模式 · 内容 · 数据与存储 · 网络代理") },
            leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            modifier = Modifier
                .pressScale()
                .clickable { onOpenThemeSettings() },
        )
        ListItem(
            headlineContent = { Text("浏览历史") },
            supportingContent = { Text("打开过的游戏自动记录") },
            leadingContent = { Icon(HistoryIcon, contentDescription = null) },
            modifier = Modifier
                .pressScale()
                .clickable { onOpenHistory() },
        )
        ListItem(
            headlineContent = { Text("数据备份") },
            supportingContent = { Text("导出备份到下载目录 / 从备份文件恢复收藏与记录") },
            leadingContent = { Icon(Icons.Rounded.Share, contentDescription = null) },
            modifier = Modifier
                .pressScale()
                .clickable {
                    if (backupBusy) return@clickable
                    scope.launch {
                        backupBusy = true
                        Toast.makeText(context, BackupManager.export(context), Toast.LENGTH_LONG).show()
                        backupBusy = false
                    }
                },
            trailingContent = {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                    Text("导入")
                }
            },
        )
        ListItem(
            headlineContent = { Text("关于") },
            supportingContent = {
                Text("GalAether ${BuildConfig.VERSION_NAME} · 包名 ${BuildConfig.APPLICATION_ID} · 点击查看项目主页")
            },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            modifier = Modifier
                .pressScale()
                .clickable {
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

    if (showGhLogout) {
        AlertDialog(
            onDismissRequest = { showGhLogout = false },
            title = { Text("退出 GitHub 登录") },
            text = { Text("退出后不再自动同步到 GitHub,云端备份会保留,重新登录即可找回。") },
            confirmButton = {
                TextButton(onClick = {
                    showGhLogout = false
                    scope.launch {
                        settings.clearGithubAuth()
                        Toast.makeText(context, "已退出 GitHub 登录", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showGhLogout = false }) { Text("取消") }
            },
        )
    }
}
