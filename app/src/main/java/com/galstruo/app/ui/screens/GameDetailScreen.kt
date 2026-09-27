package com.galstruo.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.galstruo.app.data.archive.SmartArchive
import com.galstruo.app.data.kungal.KungalApi
import com.galstruo.app.data.kungal.KungalAuth
import com.galstruo.app.data.kungal.KungalResource
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.download.DownloadState
import com.galstruo.app.data.download.DownloadTask
import com.galstruo.app.data.local.FavoriteGame
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.data.searchgal.SearchGalItem
import com.galstruo.app.data.searchgal.SearchGalPlatform
import com.galstruo.app.data.shinnku.ShinnkuFile
import com.galstruo.app.data.shinnku.VersionType
import com.galstruo.app.data.ymgal.GameDetail
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.components.CoverCard
import com.galstruo.app.ui.components.LocalShowJapaneseNames
import com.galstruo.app.ui.components.LocalShowNsfw
import com.galstruo.app.ui.components.pressScale
import com.galstruo.app.ui.detail.GameDetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gid: Long,
    orgName: String?,
    onBack: () -> Unit,
    onOpenGame: (GameItem) -> Unit,
    detailViewModel: GameDetailViewModel = viewModel(key = "game-$gid") {
        GameDetailViewModel(gid, orgName)
    },
) {
    val detail = detailViewModel.detail
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    // 收藏状态与心形弹跳动画
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by FavoriteStore.favorites.collectAsStateWithLifecycle()
    val isFav = favorites.any { it.gid == gid }
    val heartScale = remember { Animatable(1f) }
    // 打开详情页自动记录浏览历史
    LaunchedEffect(detail) {
        detail?.let { HistoryStore.record(it.gid, it.displayName, it.coverUrl) }
    }
    // 登录鲲galgame 后回到详情页:自动重搜网盘资源(升级为官方接口)
    val kungalUser by KungalAuth.user.collectAsStateWithLifecycle()
    LaunchedEffect(kungalUser) {
        if (kungalUser != null && detailViewModel.kungalSearched) {
            detailViewModel.searchKungal()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                val useJp = LocalShowJapaneseNames.current
                val titleName = detail?.let { d ->
                    if (useJp && !d.name.isNullOrBlank()) d.name.orEmpty() else d.displayName
                } ?: "游戏详情"
                Text(
                    titleName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                // 分享游戏(系统分享面板)
                IconButton(
                    onClick = {
                        detail?.let { d ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${d.displayName} - 月幕Galgame: https://www.ymgal.games/GA${gid}",
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "分享游戏"))
                        }
                    },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "分享")
                }
                IconButton(
                    onClick = {
                        detail?.let { d ->
                            val nowFav = FavoriteStore.toggle(
                                FavoriteGame(
                                    gid = d.gid,
                                    name = d.displayName,
                                    coverUrl = d.coverUrl,
                                    releaseDate = d.releaseDate,
                                )
                            )
                            Toast.makeText(
                                context,
                                if (nowFav) "已收藏" else "已取消收藏",
                                Toast.LENGTH_SHORT,
                            ).show()
                            scope.launch {
                                heartScale.snapTo(0.5f)
                                heartScale.animateTo(1.3f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                heartScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = heartScale.value
                        scaleY = heartScale.value
                    },
                ) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFav) "取消收藏" else "收藏",
                        tint = if (isFav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        when {
            detailViewModel.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            detailViewModel.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "加载失败:${detailViewModel.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                    Button(onClick = detailViewModel::load) { Text("重试") }
                }
            }

            detail != null -> DetailContent(detail, orgName, detailViewModel, tasks, onOpenGame)
        }
    }
}

@Composable
private fun DetailContent(
    detail: GameDetail,
    orgName: String?,
    vm: GameDetailViewModel,
    tasks: Map<String, DownloadTask>,
    onOpenGame: (GameItem) -> Unit,
) {
    val context = LocalContext.current
    // 日文原名开关:开启时大标题显示日文原名,下面小字显示中文名
    val useJp = LocalShowJapaneseNames.current
    val coverTitle = if (useJp && !detail.name.isNullOrBlank()) detail.name.orEmpty() else detail.displayName
    val coverSub = if (useJp && !detail.name.isNullOrBlank()) detail.displayName else detail.name.orEmpty()
    // NSFW 开关(默认关):关闭时隐藏鲲galgame 网盘资源里的限制级作品
    val showNsfw = LocalShowNsfw.current
    val kungalShown = if (showNsfw) vm.kungalResources else vm.kungalResources.filterNot { it.nsfw }
    val kungalHidden = vm.kungalResources.size - kungalShown.size
    var typeFilter by remember { mutableStateOf<VersionType?>(null) }
    var pendingDownload by remember { mutableStateOf<ShinnkuFile?>(null) }
    var showManageDialog by remember { mutableStateOf(false) }
    // 鲲galgame 链接弹窗状态
    var linksFor by remember { mutableStateOf<KungalResource?>(null) }
    var links by remember { mutableStateOf<List<String>>(emptyList()) }
    var linksLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun viewLinks(res: KungalResource) {
        linksFor = res
        // 官方接口直接带链接,不用再解析网页
        if (res.links.isNotEmpty()) {
            links = res.links
            linksLoading = false
            return
        }
        links = emptyList()
        linksLoading = true
        scope.launch {
            try {
                links = KungalApi.resourceLinks(res.id)
            } catch (e: Exception) {
                Toast.makeText(context, "获取链接失败:${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                linksLoading = false
            }
        }
    }

    /** 真正开始下载并提示用户 */
    fun startDownload(file: ShinnkuFile) {
        DownloadManager.start(file)
        Toast.makeText(context, "已开始下载:${file.fileName}", Toast.LENGTH_SHORT).show()
    }

    // 通知权限(安卓 13+ 需要,拒绝也不影响下载)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingDownload?.let { startDownload(it) }
        pendingDownload = null
    }
    // 老设备(安卓 9 及以下)复制到下载目录需要存储权限
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingDownload?.let { startDownload(it) }
        pendingDownload = null
    }
    // "所有文件访问"设置页返回后继续下载(未授权则智能归档自动降级为保存到下载目录)
    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingDownload?.let { startDownload(it) }
        pendingDownload = null
    }

    fun requestDownload(file: ShinnkuFile) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = file
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = file
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        val isSimZip = file.fileName.endsWith(".zip", true) &&
            (file.type == VersionType.KRKR || file.type == VersionType.ONS)
        if (isSimZip && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            pendingDownload = file
            showManageDialog = true
            return
        }
        startDownload(file)
    }

    val filtered = if (typeFilter == null) vm.resources else vm.resources.filter { it.type == typeFilter }
    // 资源卡当前选中的来源:0=下载资源 1=网盘资源 2=更多资源站
    var resourceTab by remember { mutableStateOf(0) }
    LazyColumn(Modifier.fillMaxSize()) {
        // 封面大图 + 底部渐变,标题和原名叠在图上
        item {
            Box {
                AsyncImage(
                    model = detail.coverUrl,
                    contentDescription = detail.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f),
                            )
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        coverTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (coverSub.isNotBlank() && coverSub != coverTitle) {
                        Text(
                            coverSub,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        // 信息 chips 行(封面下方,横向可滚动)
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                orgName?.let { InfoChip(it) }
                detail.releaseDate?.let { InfoChip(it) }
                // 平台信息在各发行版本(releases)里
                detail.releases.orEmpty()
                    .mapNotNull { it.platform }
                    .distinct()
                    .forEach { InfoChip(platformLabel(it)) }
                if (detail.haveChinese) InfoChip("官方中文")
                if (detail.restricted) InfoChip("限制级")
            }
        }
        // 限制级作品提示:成年确认 + 版权提醒
        if (detail.restricted) {
            item {
                Text(
                    "本作品为限制级(18+)作品,请确认你已成年并遵守当地法律法规。游戏版权归原作者所有,请支持正版。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        // 查找资源:三来源合成一张卡,顶部 pill 切换
        item {
            ResourceCard(
                vm = vm,
                tab = resourceTab,
                onTab = { resourceTab = it },
                typeFilter = typeFilter,
                onFilter = { typeFilter = it },
                onFind = vm::searchResources,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        // 资源行:只显示当前选中的来源,切换时有滑入滑出动画
        when (resourceTab) {
            0 -> items(filtered, key = { it.filePath }) { file ->
                ResourceRow(
                    file,
                    tasks[file.downloadUrl()],
                    onDownload = { requestDownload(file) },
                    modifier = Modifier.animateItem(),
                )
            }

            1 -> items(kungalShown, key = { "kungal-${it.id}" }) { res ->
                KungalResourceRow(
                    res,
                    onViewLinks = { viewLinks(res) },
                    modifier = Modifier.animateItem(),
                )
            }

            else -> vm.sgPlatforms.forEach { platform ->
                item(key = "sg-head-${platform.name}") { SearchGalPlatformHeader(platform) }
                items(platform.items, key = { "sg-${platform.name}-${it.url}" }) { item ->
                    SearchGalItemRow(item, modifier = Modifier.animateItem())
                }
            }
        }
        // 按 NSFW 设置隐藏限制级资源时给出提示(开关在设置页)
        if (resourceTab == 1 && kungalHidden > 0) {
            item {
                Text(
                    "已按设置隐藏 $kungalHidden 条限制级资源(可在设置中开启显示)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("简介", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    detail.introduction ?: "暂无简介",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 24.sp,
                )
            }
        }
        // 同会社作品:同一制作方的其他游戏,横向滑动
        if (vm.orgGames.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "同会社作品",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(vm.orgGames, key = { it.gameId }) { game ->
                            CoverCard(
                                game,
                                onClick = { onOpenGame(game) },
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showManageDialog) {
        AlertDialog(
            onDismissRequest = {
                showManageDialog = false
                pendingDownload?.let { startDownload(it) }
                pendingDownload = null
            },
            title = { Text("需要\"所有文件访问\"权限") },
            text = {
                Text("KRKR/ONS 压缩包将自动解压到模拟器目录,需要授予\"所有文件访问\"权限。跳过授权也可以下载,文件会保存到下载目录,由你手动解压。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showManageDialog = false
                    manageLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showManageDialog = false
                    pendingDownload?.let { startDownload(it) }
                    pendingDownload = null
                }) { Text("仍然下载") }
            },
        )
    }

    // 网盘链接弹窗
    linksFor?.let { res ->
        AlertDialog(
            onDismissRequest = { linksFor = null },
            title = {
                Text(res.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                when {
                    linksLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在获取链接…")
                    }

                    links.isEmpty() -> Text("公开页面里没有提取到下载链接,请在网页中查看。")

                    else -> Column {
                        // 官方接口给出的提取码/解压密码/备注
                        if (res.code.isNotBlank() || res.password.isNotBlank() || res.note.isNotBlank()) {
                            Text(
                                buildString {
                                    if (res.code.isNotBlank()) append("提取码:${res.code}    ")
                                    if (res.password.isNotBlank()) append("解压密码:${res.password}")
                                    if (res.note.isNotBlank()) append("\n备注:${res.note}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            "点击「复制」复制链接,或「打开」直接唤起网盘 App / 浏览器",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        links.forEach { link -> LinkRow(link) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kungal.com/galgame/resource/${res.id}"))
                    )
                    linksFor = null
                }) { Text("在浏览器打开资源页") }
            },
            dismissButton = {
                TextButton(onClick = { linksFor = null }) { Text("关闭") }
            },
        )
    }
}

/** 「网盘资源」tab 内容:鲲galgame 搜索状态 */
@Composable
private fun KungalTabContent(vm: GameDetailViewModel) {
    Text(
        "来自鲲galgame(kungal.com),网盘链接只复制/唤起,不直接下载",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    when {
        vm.kungalLoading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("正在搜索鲲galgame…", style = MaterialTheme.typography.bodyMedium)
        }

        vm.kungalResources.isEmpty() && vm.kungalSearched -> Column {
            Text(
                vm.kungalError ?: "没有找到网盘资源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::searchKungal) { Text("重新搜索") }
        }

        vm.kungalResources.isEmpty() -> Column {
            Text(
                "点「查找下载资源」会连同真红小站、更多资源站一起搜索。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::searchKungal) { Text("单独搜索网盘资源") }
        }

        else -> Column {
            Text(
                if (vm.kungalOfficial) {
                    "找到 ${vm.kungalResources.size} 个网盘资源(官方接口,含提取码/解压密码),点「查看链接」获取下载链接。"
                } else {
                    "找到 ${vm.kungalResources.size} 个网盘资源,点「查看链接」获取百度网盘等下载链接,可直接复制或唤起网盘 App。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!vm.kungalOfficial) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "登录鲲galgame 账号可获取更全的官方资源(含提取码/解压密码),在「我的」页登录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 「更多资源站」tab 内容:SearchGal 聚合搜索(27+ 资源站,流式进度) */
@Composable
private fun SearchGalTabContent(vm: GameDetailViewModel) {
    Text(
        "来自 SearchGal 聚合搜索,一次搜索 27+ 个资源站;结果是发布页链接,点「打开网页」查看",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    val progress = vm.sgProgress
    when {
        vm.sgLoading && progress != null && vm.sgPlatforms.isEmpty() -> Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    "已搜索 ${progress.first}/${progress.second} 个平台…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = {
                    if (progress.second > 0) progress.first.toFloat() / progress.second else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50)),
            )
        }

        vm.sgLoading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                if (vm.sgPlatforms.isEmpty()) "正在搜索 27+ 个资源站…"
                else "继续搜索中(已找到 ${vm.sgPlatforms.size} 个平台)…",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        vm.sgPlatforms.isEmpty() && vm.sgSearched -> Column {
            Text(
                vm.sgError ?: "没有找到资源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::searchMoreSites) { Text("重新搜索") }
        }

        vm.sgPlatforms.isEmpty() -> Column {
            Text(
                "点「查找下载资源」会连同真红小站、鲲galgame 一起搜索。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::searchMoreSites) { Text("搜索更多资源站") }
        }

        else -> Text(
            "在 ${vm.sgPlatforms.size} 个资源站找到结果,点「打开网页」查看发布页,「复制链接」可保存分享。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 查找资源卡:三来源合成一张卡,顶部 pill 切换,内容随 tab 左右滑动切换 */
@Composable
private fun ResourceCard(
    vm: GameDetailViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    typeFilter: VersionType?,
    onFilter: (VersionType?) -> Unit,
    onFind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // 三个标签按文字自然宽度居中排列(不再强制三等分,避免字号较大时换行堆叠)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(50),
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                ResourceTabPill("下载资源", tab == 0, { onTab(0) })
                ResourceTabPill("网盘资源", tab == 1, { onTab(1) })
                ResourceTabPill("更多资源站", tab == 2, { onTab(2) })
            }
            Spacer(Modifier.height(14.dp))
            // 内容区不套切换动画(动画会压缩内容导致文字堆叠),靠标签弹跳反馈
            when (tab) {
                0 -> DownloadTabContent(vm, typeFilter, onFilter, onFind)
                1 -> KungalTabContent(vm)
                else -> SearchGalTabContent(vm)
            }
        }
    }
}

/** 资源卡顶部切换 pill:选中时先缩小再弹回,像果冻 */
@Composable
private fun ResourceTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(0.82f)
            scale.animateTo(
                1.1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .pressScale()
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** 平台小标题:彩色圆点 + 平台名 + 标签(免登录/需登录等) */
@Composable
private fun SearchGalPlatformHeader(platform: SearchGalPlatform) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(platformDotColor(platform.color), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Text(
            platform.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        platform.tags.forEach { tag ->
            Text(
                tagLabel(tag),
                style = MaterialTheme.typography.labelSmall,
                color = tagColor(tag),
                modifier = Modifier
                    .background(tagColor(tag).copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** 一条聚合搜索结果:资源名 + 打开网页/复制链接 */
@Composable
private fun SearchGalItemRow(item: SearchGalItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
            }
        }) { Text("打开网页") }
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(item.url))
            Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
        }) { Text("复制链接") }
    }
}

/** 标签转中文(SearchGal 平台标签) */
private fun tagLabel(tag: String): String = when (tag) {
    "NoReq" -> "免登录"
    "Login" -> "需登录"
    "LoginPay" -> "需登录付费"
    "magic" -> "魔法"
    "SuDrive" -> "快速网盘"
    "MixDrive" -> "混合网盘"
    else -> tag
}

/** 标签配色:免登录=绿,需登录=橙,付费=红 */
@Composable
private fun tagColor(tag: String): androidx.compose.ui.graphics.Color = when {
    tag == "NoReq" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    tag.contains("Pay") -> androidx.compose.ui.graphics.Color(0xFFC62828)
    tag.contains("Login") -> androidx.compose.ui.graphics.Color(0xFFEF6C00)
    else -> MaterialTheme.colorScheme.tertiary
}

/** 平台圆点颜色(SearchGal 返回的 color 字段是颜色名) */
@Composable
private fun platformDotColor(colorName: String): androidx.compose.ui.graphics.Color = when (colorName.lowercase()) {
    "red" -> androidx.compose.ui.graphics.Color(0xFFE53935)
    "lime" -> androidx.compose.ui.graphics.Color(0xFF7CB342)
    "white" -> androidx.compose.ui.graphics.Color(0xFFEEEEEE)
    "green" -> androidx.compose.ui.graphics.Color(0xFF43A047)
    "blue" -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
    "yellow" -> androidx.compose.ui.graphics.Color(0xFFFDD835)
    "orange" -> androidx.compose.ui.graphics.Color(0xFFFB8C00)
    "pink" -> androidx.compose.ui.graphics.Color(0xFFD81B60)
    "purple" -> androidx.compose.ui.graphics.Color(0xFF8E24AA)
    "cyan" -> androidx.compose.ui.graphics.Color(0xFF00ACC1)
    else -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
}

/** 鲲galgame 资源行(带来源标签) */
@Composable
private fun KungalResourceRow(
    res: KungalResource,
    onViewLinks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    res.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "鲲galgame",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (res.official) {
                        Text(
                            "官方接口",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (res.size.isNotBlank()) {
                        Text(
                            res.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (res.code.isNotBlank() || res.password.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            if (res.code.isNotBlank()) append("提取码 ${res.code}")
                            if (res.code.isNotBlank() && res.password.isNotBlank()) append(" · ")
                            if (res.password.isNotBlank()) append("解压码 ${res.password}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kungal.com/galgame/resource/${res.id}"))
                )
            }) { Text("打开网页") }
            Button(onClick = onViewLinks) { Text("查看链接") }
        }
    }
}

/** 网盘链接行:复制 / 打开 */
@Composable
private fun LinkRow(link: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Text(
            link,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(link))
            Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
        }) { Text("复制") }
        TextButton(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
            }
        }) { Text("打开") }
    }
}

/** 「下载资源」tab 内容:真红小站搜索状态 + 版本筛选 chips */
@Composable
private fun DownloadTabContent(
    vm: GameDetailViewModel,
    typeFilter: VersionType?,
    onFilter: (VersionType?) -> Unit,
    onFind: () -> Unit,
) {
    val resources = vm.resources
    Text(
        "来自真红小站(shinnku.com),支持安卓直装 / KRKR / ONS / PC 多版本",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    when {
        vm.resourcesLoading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("正在搜索真红小站…", style = MaterialTheme.typography.bodyMedium)
        }

        resources.isEmpty() && vm.resourcesSearched -> Column {
            Text(
                vm.resourcesError ?: "没有找到该游戏的资源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onFind) { Text("重新搜索") }
        }

        else -> {
            if (resources.isEmpty()) {
                Text(
                    "在真红小站搜索本游戏的下载资源,支持安卓直装 / KRKR / ONS / PC 多版本。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = onFind) {
                Text(if (resources.isEmpty()) "查找下载资源" else "重新搜索")
            }
        }
    }
    if (resources.isNotEmpty()) {
        // 版本筛选
        val presentTypes = remember(resources) {
            listOfNotNull(null) + resources.map { it.type }.distinct().sortedBy { it.ordinal }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            presentTypes.forEach { type ->
                FilterChip(
                    label = type?.label ?: "全部",
                    selected = typeFilter == type,
                    onClick = { onFilter(type) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .pressScale()
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ResourceRow(
    file: ShinnkuFile,
    task: DownloadTask?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 进度条平滑过渡,避免每 256KB 一跳
    val smoothProgress by animateFloatAsState(
        targetValue = task?.progress ?: 0f,
        animationSpec = tween(300),
        label = "downloadProgress",
    )
    // 点击下载瞬间:按钮缩一下再弹回,让用户立即看到反馈
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "downloadPress",
    )
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoChip(file.type.label)
                    Text(
                        formatSize(file.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // 点击下载后,按钮动画切换为进度:让用户一眼看到下载已开始
            val status = when {
                task == null -> 0
                task.state == DownloadState.DOWNLOADING || task.state == DownloadState.QUEUED -> 1
                task.state == DownloadState.DONE -> 2
                else -> 3
            }
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    )).togetherWith(
                        fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120))
                    )
                },
                label = "downloadStatus",
            ) { s ->
                when (s) {
                    1 -> task?.let { current ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${(current.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = { DownloadManager.cancel(current.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "取消下载", Modifier.size(18.dp))
                            }
                        }
                    }

                    2 -> task?.let { current ->
                        if (file.type == VersionType.APK) {
                            Button(onClick = {
                                SmartArchive.installApk(context, DownloadManager.fileOf(current))
                            }) { Text("安装") }
                        } else {
                            Text(
                                "已下载",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    3 -> task?.let { current ->
                        Button(onClick = { DownloadManager.retry(current.id) }) { Text("重试") }
                    }

                    else -> Button(
                        onClick = {
                            pressed = true
                            scope.launch { delay(150); pressed = false }
                            onDownload()
                        },
                        modifier = Modifier.graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        },
                    ) { Text("下载") }
                }
            }
        }
        // 进度条展开出现,下载开始一眼可见
        AnimatedVisibility(
            visible = task != null &&
                (task.state == DownloadState.DOWNLOADING || task.state == DownloadState.QUEUED),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { smoothProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50)),
                )
            }
        }
        if (task?.state == DownloadState.ERROR) {
            Text(
                task.error.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 平台代码转中文名(月幕用 win/andr/psp 等代码) */
private fun platformLabel(code: String): String {
    val c = code.lowercase()
    return when {
        c.contains("win") -> "PC"
        c.contains("andr") -> "安卓"
        c == "ios" -> "iOS"
        c.contains("web") || c.contains("html") -> "网页"
        c.contains("lin") -> "Linux"
        c.contains("mac") -> "Mac"
        c.contains("psp") -> "PSP"
        c.contains("psv") -> "PSV"
        c.contains("nds") -> "NDS"
        c.contains("switch") || c == "ns" -> "Switch"
        else -> code
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
