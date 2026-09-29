package com.galstruo.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import com.galstruo.app.BuildConfig
import com.galstruo.app.data.FontSize
import com.galstruo.app.data.ListStyle
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.ThemeMode
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.archive.SmartArchive
import com.galstruo.app.data.download.DownloadDir
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.emulator.Emulator
import com.galstruo.app.data.emulator.EmulatorRepository
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.data.network.NetConfig
import com.galstruo.app.data.sync.GitHubSync
import com.galstruo.app.data.sync.SyncManager
import com.galstruo.app.data.sync.WebDavSync
import com.galstruo.app.data.update.UpdateChecker
import com.galstruo.app.ui.components.ColorWheel
import com.galstruo.app.ui.theme.m3RoleColor
import com.galstruo.app.ui.components.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun ThemeSettingsScreen(
    settings: SettingsStore,
    uiSettings: UiSettings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hue by remember(uiSettings.seedHue) {
        mutableFloatStateOf(if (uiSettings.seedHue in 0..359) uiSettings.seedHue.toFloat() else 212f)
    }
    // 数据统计:收藏数、下载完成数、图片缓存大小
    val context = LocalContext.current
    val favorites by FavoriteStore.favorites.collectAsStateWithLifecycle()
    val records by DownloadManager.records.collectAsStateWithLifecycle()
    val doneCount = records.count { it.state == "DONE" }
    var cacheSizeText by remember { mutableStateOf("计算中…") }
    LaunchedEffect(Unit) {
        cacheSizeText = withContext(Dispatchers.IO) {
            formatSize(Coil.imageLoader(context).diskCache?.size ?: 0L)
        }
    }
    fun clearCache() {
        scope.launch(Dispatchers.IO) {
            Coil.imageLoader(context).diskCache?.clear()
            Coil.imageLoader(context).memoryCache?.clear()
            cacheSizeText = "0 B"
            Toast.makeText(context, "图片缓存已清除", Toast.LENGTH_SHORT).show()
        }
    }
    // 网络代理(修改立即生效)
    var proxyOn by remember(uiSettings.proxyEnabled) { mutableStateOf(uiSettings.proxyEnabled) }
    var proxyHost by remember(uiSettings.proxyHost) { mutableStateOf(uiSettings.proxyHost) }
    var proxyPort by remember(uiSettings.proxyPort) { mutableStateOf(uiSettings.proxyPort.toString()) }

    fun applyProxy() {
        val port = proxyPort.toIntOrNull() ?: 7890
        NetConfig.update(proxyOn, proxyHost.trim(), port)
        scope.launch { settings.setProxy(proxyOn, proxyHost.trim(), port) }
    }

    // 自选下载目录(系统文件夹选择器)
    var dirLabel by remember { mutableStateOf("") }
    LaunchedEffect(uiSettings.downloadUri) {
        if (uiSettings.downloadUri.isNotBlank() && !DownloadDir.isSet()) {
            DownloadDir.update(Uri.parse(uiSettings.downloadUri))
        }
        dirLabel = DownloadDir.label
    }
    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                // 持久授权,重启后仍可写入所选文件夹
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            DownloadDir.update(uri)
            scope.launch { settings.setDownloadUri(uri.toString()) }
            dirLabel = DownloadDir.label
            Toast.makeText(context, "下载目录已设置", Toast.LENGTH_SHORT).show()
        }
    }

    // 检查更新(GitHub Releases 直连失败自动回退国内加速镜像)
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }

    fun checkUpdate() {
        if (checkingUpdate) return
        scope.launch {
            checkingUpdate = true
            try {
                val latest = UpdateChecker.check()
                // 用户点过"跳过此版本"的版本不再弹窗提示
                updateInfo = latest?.takeIf {
                    it.tag != uiSettings.skippedVersion &&
                        UpdateChecker.isNewer(it.tag, BuildConfig.VERSION_NAME)
                }
                if (updateInfo == null) {
                    Toast.makeText(
                        context,
                        if (latest != null && latest.tag == uiSettings.skippedVersion) {
                            "最新版 ${latest.tag} 已跳过,不再提示"
                        } else {
                            "已是最新版本"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "检查失败:${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                checkingUpdate = false
            }
        }
    }

    // Galgame 模拟器:应用内下载安装包后唤起系统安装
    val installedEmulators = remember { mutableStateMapOf<String, Boolean>() }
    LifecycleResumeEffect(Unit) {
        EmulatorRepository.list.forEach { emulator ->
            installedEmulators[emulator.name] = EmulatorRepository.isInstalled(context, emulator)
        }
        onPauseOrDispose { }
    }
    var emulatorBusy by remember { mutableStateOf<String?>(null) }
    var emulatorProgress by remember { mutableStateOf(0) }

    fun installEmulator(emulator: Emulator) {
        if (emulatorBusy != null) return
        scope.launch {
            emulatorBusy = emulator.name
            emulatorProgress = 0
            try {
                val apk = EmulatorRepository.download(emulator) { emulatorProgress = it }
                Toast.makeText(context, "下载完成,正在打开安装…", Toast.LENGTH_SHORT).show()
                SmartArchive.installApk(context, apk)
            } catch (e: Exception) {
                Toast.makeText(context, "下载失败:${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                emulatorBusy = null
            }
        }
    }

    // 关于:隐私政策 / 开源许可 / 免责声明 / 开发信息
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var showDevInfo by remember { mutableStateOf(false) }
    // 开启 NSFW 的成年确认弹窗
    var showNsfwConfirm by remember { mutableStateOf(false) }

    // 云同步:GitHub 登录 / WebDAV 配置 / 同步状态
    val syncStatus by SyncManager.status.collectAsStateWithLifecycle()
    var showGitLogin by remember { mutableStateOf(false) }
    var showGitLogout by remember { mutableStateOf(false) }
    var webdavUrl by remember(uiSettings.webdavUrl) { mutableStateOf(uiSettings.webdavUrl) }
    var webdavUser by remember(uiSettings.webdavUser) { mutableStateOf(uiSettings.webdavUser) }
    var webdavPassword by remember(uiSettings.webdavPassword) { mutableStateOf(uiSettings.webdavPassword) }
    var webdavTesting by remember { mutableStateOf(false) }

    // WebDAV 输入框边输边存(省掉"保存"按钮,改完即生效)
    fun saveWebdavFields() {
        scope.launch {
            settings.setWebdav(uiSettings.webdavEnabled, webdavUrl.trim(), webdavUser.trim(), webdavPassword)
        }
    }

    val syncStatusText = when {
        syncStatus.syncing -> "正在与云端同步…"
        syncStatus.lastMessage.isNotBlank() -> {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(syncStatus.lastSyncAt))
            "上次同步 $t · ${syncStatus.lastMessage}"
        }
        else -> "登录 GitHub 或配置 WebDAV 后自动同步"
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "主色调",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            ColorWheel(
                hue = hue,
                onHueChange = {
                    hue = it
                    scope.launch { settings.setSeedHue(it.toInt()) }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "拖动色轮选择主色,选好后自动关闭跟随壁纸",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            // 预设配色:一键套用调好的颜色(点击即生效,自动关闭跟随壁纸)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                // 预设色相改为 HCT 色相(配色表按 HCT 生成),数值由官方库换算而来,
                // 保证点"谷歌蓝"得到的确实是蓝,而不是偏青
                listOf(
                    "谷歌蓝" to 258,
                    "樱粉" to 5,
                    "薄荷绿" to 168,
                    "青紫" to 310,
                    "珊瑚橙" to 43,
                    "柠檬黄" to 105,
                ).forEach { (name, presetHue) ->
                    val selected = !uiSettings.dynamicColor && uiSettings.seedHue == presetHue
                    // 预设圆点颜色直接从调色板取(与点下去后应用的实际配色一致),
                    // 而不是用 HSV 公式画,避免圆点和实际效果有色差
                    val presetColor = m3RoleColor(
                        presetHue.toFloat(),
                        "primary",
                        isSystemInDarkTheme(),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .pressScale()
                            .clickable {
                                hue = presetHue.toFloat()
                                scope.launch { settings.setSeedHue(presetHue) }
                            },
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(presetColor, CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("跟随壁纸自动取色", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "安卓 12+ 生效,主题色随壁纸变化",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiSettings.dynamicColor,
                    onCheckedChange = { scope.launch { settings.setDynamicColor(it) } },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "深色模式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiSettings.themeMode == mode,
                        onClick = { scope.launch { settings.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size,
                        ),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "外观",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("界面动画", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭后页面切换、按压缩放等动画全部停止,更省电",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiSettings.animationsEnabled,
                    onCheckedChange = { scope.launch { settings.setAnimationsEnabled(it) } },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("显示日文原名", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "开启后列表与详情的主标题显示日文原名,中文名作为小字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiSettings.showJapaneseNames,
                    onCheckedChange = { scope.launch { settings.setShowJapaneseNames(it) } },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "字体大小",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FontSize.entries.forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = uiSettings.fontSize == size,
                        onClick = { scope.launch { settings.setFontSize(size) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FontSize.entries.size,
                        ),
                    ) {
                        Text(
                            when (size) {
                                FontSize.SMALL -> "小"
                                FontSize.NORMAL -> "标准"
                                FontSize.LARGE -> "大"
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "列表样式",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ListStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = uiSettings.listStyle == style,
                        onClick = { scope.launch { settings.setListStyle(style) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ListStyle.entries.size,
                        ),
                    ) {
                        Text(
                            when (style) {
                                ListStyle.CARD -> "大卡片"
                                ListStyle.COMPACT -> "紧凑"
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "网络代理",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("使用 HTTP 代理", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "搜索、详情、下载全部走代理,修改立即生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = proxyOn,
                    onCheckedChange = {
                        proxyOn = it
                        applyProxy()
                    },
                )
            }
            if (proxyOn) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = {
                            proxyHost = it
                            applyProxy()
                        },
                        label = { Text("地址") },
                        placeholder = { Text("127.0.0.1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = {
                            proxyPort = it.filter { c -> c.isDigit() }.take(5)
                            applyProxy()
                        },
                        label = { Text("端口") },
                        placeholder = { Text("7890") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "内容",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("显示限制级(NSFW)内容", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭时,首页「最新发行」和「分类浏览」不显示限制级作品;搜索不受影响",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiSettings.showNsfw,
                    onCheckedChange = { want ->
                        // 开启限制级内容前需确认成年
                        if (want) showNsfwConfirm = true
                        else scope.launch { settings.setShowNsfw(false) }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "首页显示区块",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(4.dp))
            HomeSectionSwitch("今日推荐", uiSettings.showHomeDaily) {
                scope.launch { settings.setShowHomeDaily(it) }
            }
            HomeSectionSwitch("热门经典", uiSettings.showHomeHot) {
                scope.launch { settings.setShowHomeHot(it) }
            }
            HomeSectionSwitch("猜你喜欢", uiSettings.showHomeForYou) {
                scope.launch { settings.setShowHomeForYou(it) }
            }
            HomeSectionSwitch("最新发行", uiSettings.showHomeLatest) {
                scope.launch { settings.setShowHomeLatest(it) }
            }
            HomeSectionSwitch("最近浏览", uiSettings.showHomeRecent) {
                scope.launch { settings.setShowHomeRecent(it) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Galgame 模拟器",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            EmulatorRepository.list.forEach { emulator ->
                val busy = emulatorBusy == emulator.name
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                emulator.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (installedEmulators[emulator.name] == true) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "已安装",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            "${emulator.desc}\n${emulator.version} · ${emulator.source}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (busy) {
                            LinearProgressIndicator(
                                progress = { emulatorProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                            Text(
                                "正在下载 $emulatorProgress%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (busy) {
                        TextButton(onClick = {}, enabled = false) { Text("下载中") }
                    } else if (emulator.apkUrl != null) {
                        TextButton(onClick = { installEmulator(emulator) }) { Text("下载安装") }
                    } else {
                        // 没有直链的模拟器:打开第三方下载页(浏览器)
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(emulator.pageUrl)),
                            )
                        }) { Text("打开下载页") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "数据与存储",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("已收藏游戏", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${favorites.size} 部",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("完成下载", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$doneCount 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("图片缓存", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        cacheSizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = ::clearCache) { Text("清除") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("下载目录", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        dirLabel.takeIf { it.isNotBlank() }?.let { "当前:$it" }
                            ?: "未选择(默认系统下载文件夹)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (dirLabel.isNotBlank()) {
                    TextButton(
                        onClick = {
                            DownloadDir.update(null)
                            dirLabel = ""
                            scope.launch { settings.setDownloadUri("") }
                        },
                    ) { Text("恢复默认") }
                }
                TextButton(onClick = { dirLauncher.launch(null) }) { Text("选择文件夹") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("检查更新", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "当前 ${BuildConfig.VERSION_NAME} · 从 GitHub Releases 更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = ::checkUpdate, enabled = !checkingUpdate) {
                    Text(if (checkingUpdate) "检查中…" else "检查更新")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "云同步",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            // GitHub:设备码登录,数据存私有 Gist
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("GitHub 同步", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (uiSettings.githubUser.isNotBlank()) {
                            "已登录:${uiSettings.githubUser} · 备份在私有 Gist"
                        } else {
                            "登录 GitHub,数据自动备份到私有 Gist"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiSettings.githubUser.isNotBlank()) {
                    TextButton(onClick = { showGitLogout = true }) { Text("退出登录") }
                } else {
                    TextButton(onClick = { showGitLogin = true }) { Text("登录") }
                }
            }
            if (uiSettings.githubUser.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动同步 GitHub", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "数据变化后自动上传到云端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiSettings.githubSyncEnabled,
                        onCheckedChange = { scope.launch { settings.setGithubSyncEnabled(it) } },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // WebDAV:通用网盘协议(坚果云等),备份存到自己的网盘
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("WebDAV 同步", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "通用网盘协议(坚果云等),备份存到自己的网盘",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiSettings.webdavEnabled,
                    onCheckedChange = {
                        scope.launch {
                            settings.setWebdav(it, webdavUrl.trim(), webdavUser.trim(), webdavPassword)
                        }
                    },
                )
            }
            if (uiSettings.webdavEnabled) {
                OutlinedTextField(
                    value = webdavUrl,
                    onValueChange = { webdavUrl = it; saveWebdavFields() },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    supportingText = { Text("坚果云填这个地址;其他网盘填它的 WebDAV 入口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = webdavUser,
                    onValueChange = { webdavUser = it; saveWebdavFields() },
                    label = { Text("账号") },
                    placeholder = { Text("坚果云注册邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = webdavPassword,
                    onValueChange = { webdavPassword = it; saveWebdavFields() },
                    label = { Text("密码") },
                    placeholder = { Text("坚果云「应用密码」,不是登录密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (webdavTesting) return@TextButton
                            webdavTesting = true
                            scope.launch {
                                saveWebdavFields()
                                val result = WebDavSync.test(
                                    webdavUrl.trim(), webdavUser.trim(), webdavPassword,
                                )
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                webdavTesting = false
                            }
                        },
                        enabled = webdavUrl.isNotBlank() && webdavUser.isNotBlank() && !webdavTesting,
                    ) { Text(if (webdavTesting) "测试中…" else "测试连接") }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 手动同步与状态
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("同步状态", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        syncStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            Toast.makeText(context, SyncManager.syncNow(), Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !syncStatus.syncing,
                ) { Text(if (syncStatus.syncing) "同步中…" else "立即同步") }
                TextButton(
                    onClick = {
                        scope.launch {
                            Toast.makeText(context, SyncManager.pullNow(), Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text("从云端恢复") }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "关于",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("隐私政策", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "不注册账号、不收集个人信息,点击查看全文",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showPrivacy = true }) { Text("查看") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("开源许可", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "MIT 协议及使用的开源库",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showLicense = true }) { Text("查看") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("免责声明", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "版权与合规说明,点击查看全文",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showDisclaimer = true }) { Text("查看") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("开发信息", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "版本 · 包名 · 构建与数据来源等",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showDevInfo = true }) { Text("查看") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    // 发现新版本弹窗(下载带进度,失败可用浏览器兜底)
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 ${info.tag}") },
            text = {
                Column {
                    Text(
                        "更新日志",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (info.notes.isBlank()) "本次更新无说明" else plainNotes(info.notes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 260.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                updateInfo = null
                                scope.launch { settings.setSkippedVersion(info.tag) }
                                Toast.makeText(context, "已跳过 ${info.tag}", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text("跳过此版本") }
                    }
                    if (downloadingUpdate) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { updateProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "正在下载更新包 $updateProgress%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            downloadingUpdate = true
                            updateProgress = 0
                            try {
                                val apk = UpdateChecker.downloadApk(info.apkUrl) { updateProgress = it }
                                Toast.makeText(
                                    context, "下载完成,正在打开安装…", Toast.LENGTH_SHORT,
                                ).show()
                                SmartArchive.installApk(context, apk)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context, "下载失败:${e.message}", Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                downloadingUpdate = false
                                updateInfo = null
                            }
                        }
                    },
                    enabled = !downloadingUpdate,
                ) { Text(if (downloadingUpdate) "下载中…" else "下载并安装") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // 兜底:用浏览器直接打开下载链接
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
                    updateInfo = null
                }) { Text("浏览器下载") }
            },
        )
    }
    // 隐私政策弹窗
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私政策") },
            text = { Text(PRIVACY_TEXT) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("知道了") }
            },
        )
    }
    // 开源许可弹窗
    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("开源许可") },
            text = { Text(LICENSE_TEXT) },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("知道了") }
            },
        )
    }
    // 免责声明弹窗
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("免责声明") },
            text = { Text(DISCLAIMER_TEXT) },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false }) { Text("知道了") }
            },
        )
    }
    // 开发信息弹窗:版本/包名/构建/数据来源
    if (showDevInfo) {
        AlertDialog(
            onDismissRequest = { showDevInfo = false },
            title = { Text("开发信息") },
            text = {
                Text(
                    buildString {
                        append("应用名称:GalAether\n")
                        append("版本:${BuildConfig.VERSION_NAME}(构建号 ${BuildConfig.VERSION_CODE})\n")
                        append("包名:${BuildConfig.APPLICATION_ID}\n")
                        append("开发者:NZNMC\n")
                        append("代码仓库:github.com/NZNMC/GalAether\n")
                        append("开源协议:MIT License\n")
                        append("系统要求:安卓 8.0 及以上(minSdk 26 / targetSdk 34)\n")
                        append("构建环境:Kotlin 2.0.20 · Jetpack Compose\n")
                        append("\n数据来源:\n")
                        append("· 月幕Galgame(ymgal.games)— 游戏资料\n")
                        append("· 真红小站(shinnku.com)— 下载资源\n")
                        append("· 鲲galgame(kungal.com)— 网盘资源\n")
                        append("· SearchGal — 多资源站聚合\n")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDevInfo = false }) { Text("知道了") }
            },
        )
    }
    // 开启限制级内容的成年确认
    if (showNsfwConfirm) {
        AlertDialog(
            onDismissRequest = { showNsfwConfirm = false },
            title = { Text("开启限制级内容") },
            text = { Text("你确认已年满 18 周岁,并了解且遵守所在地区的相关法律法规吗?限制级内容仅供学习交流,版权归原作者所有,请支持正版。") },
            confirmButton = {
                TextButton(onClick = {
                    showNsfwConfirm = false
                    scope.launch { settings.setShowNsfw(true) }
                }) { Text("我已成年,开启") }
            },
            dismissButton = {
                TextButton(onClick = { showNsfwConfirm = false }) { Text("取消") }
            },
        )
    }
    // GitHub 设备码登录弹窗
    if (showGitLogin) {
        GitHubLoginDialog(
            onDismiss = { showGitLogin = false },
            settings = settings,
        )
    }
    // 退出 GitHub 登录确认
    if (showGitLogout) {
        AlertDialog(
            onDismissRequest = { showGitLogout = false },
            title = { Text("退出 GitHub 登录") },
            text = { Text("退出后不再自动同步到 GitHub,云端备份保留,重新登录即可找回。") },
            confirmButton = {
                TextButton(onClick = {
                    showGitLogout = false
                    scope.launch {
                        settings.clearGithubAuth()
                        Toast.makeText(context, "已退出 GitHub 登录", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showGitLogout = false }) { Text("取消") }
            },
        )
    }
}

private const val PRIVACY_TEXT =
    "本应用(GalAether)为个人自用项目:不注册账号,不收集、不上传任何个人信息(包括姓名、手机号、邮箱、位置等),不含广告与统计埋点。\n\n" +
        "应用联网仅用于:获取游戏资料与下载链接(月幕Galgame、Kungal 等站点)、检查更新与下载模拟器安装包(GitHub Releases 及其公开加速镜像)、加载游戏封面图片。\n\n" +
        "收藏、下载记录、设置等数据默认只保存在你的手机本地,不会离开设备。\n\n" +
        "云同步(可选):只有你主动开启后,备份数据(收藏、历史、下载记录、设置)才会发送到你自己的 GitHub 私有 Gist 或 WebDAV 网盘。GitHub 令牌与网盘密码只保存在本机,不会写入任何备份文件。手动导出的备份文件同样不含任何密码。\n\n" +
        "如在设置中启用了网络代理,应用的全部网络请求将按你的配置转发。"

/** 首页区块开关行:一行一个区块,Switch 控制显示/隐藏 */
@Composable
private fun HomeSectionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * GitHub 设备码登录弹窗:向 GitHub 申请配对码 → 用户在浏览器输入配对码授权
 * → 应用轮询拿到令牌后自动登录,并立即与云端同步一次。
 */
@Composable
private fun GitHubLoginDialog(onDismiss: () -> Unit, settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flow by remember { mutableStateOf<GitHubSync.DeviceFlow?>(null) }
    var error by remember { mutableStateOf("") }

    // 第一步:申请设备码
    LaunchedEffect(Unit) {
        try {
            flow = GitHubSync.requestDeviceCode()
        } catch (e: Exception) {
            error = "申请配对码失败:${e.message}"
        }
    }
    // 第二步:轮询等待授权;拿到令牌后保存、登录成功
    LaunchedEffect(flow) {
        val f = flow ?: return@LaunchedEffect
        if (error.isNotBlank()) return@LaunchedEffect
        try {
            val token = GitHubSync.pollAccessToken(f)
            val user = GitHubSync.getUser(token)
            settings.setGithubAuth(token, user.login, user.avatarUrl)
            Toast.makeText(context, "GitHub 登录成功:${user.login}", Toast.LENGTH_SHORT).show()
            scope.launch {
                // 登录后立刻同步一次:老设备会创建云端备份,新设备会直接恢复数据
                Toast.makeText(context, SyncManager.refreshAfterLogin(), Toast.LENGTH_LONG).show()
            }
            onDismiss()
        } catch (e: Exception) {
            error = e.message ?: "登录失败"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录 GitHub") },
        text = {
            val f = flow
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    error.isNotBlank() -> {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = {
                            error = ""
                            scope.launch {
                                try {
                                    flow = GitHubSync.requestDeviceCode()
                                } catch (e: Exception) {
                                    error = "申请配对码失败:${e.message}"
                                }
                            }
                        }) { Text("重试") }
                    }

                    f == null -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在向 GitHub 申请配对码…")
                    }

                    else -> {
                        Text(
                            "在浏览器打开下面的网址,输入配对码并同意授权:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            f.userCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(f.verificationUri)),
                            )
                        }) { Text("打开 GitHub 授权网页") }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "完成后回到本应用,会自动完成登录。\n配对码 ${f.expiresInSec / 60} 分钟内有效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private const val DISCLAIMER_TEXT =
    "本应用为个人学习、交流、技术研究用途,不上架应用商店,不用于任何商业目的。\n\n" +
        "本应用本身不存储、不提供任何游戏资源文件。应用内展示的下载链接均为联网时实时检索自第三方公开站点的结果,相关资源的版权归原作者/原版权方所有。\n\n" +
        "请在下载游戏后 24 小时内删除,支持正版,并通过官方渠道购买。使用本应用产生的任何后果由使用者自行承担,与开发者无关。\n\n" +
        "应用内的模拟器(如 KRKR2、ONS)来自各自的官方开源项目,版权归各自作者所有。\n\n" +
        "限制级内容仅向已成年用户开放,请遵守所在地区的法律法规。"

private const val LICENSE_TEXT =
    "本应用采用 MIT 协议开源,代码仓库:github.com/NZNMC/GalAether\n\n" +
        "MIT License · Copyright (c) 2026 NZNMC\n\n" +
        "任何人可免费获得本软件的副本,不受限制地使用、复制、修改、合并、发布、分发、再许可或出售,唯一要求是在所有副本中保留以上版权声明与许可声明。软件按\"原样\"提供,不附带任何明示或暗示的担保。\n\n" +
        "使用的开源库:Jetpack Compose(Apache-2.0)、OkHttp(Apache-2.0)、Gson(Apache-2.0)、Coil(Apache-2.0)、AndroidX 系列(Apache-2.0)。"

/** 更新日志里的 Markdown 转简单纯文本(标题→圆点、去加粗符号) */
private fun plainNotes(md: String): String = md
    .replace(Regex("""#{1,6}\s+"""), "▪ ")
    .replace("**", "")
    .replace(Regex("""(?m)^[-*]\s+"""), "· ")

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
