package com.galstruo.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galstruo.app.data.ListStyle
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.data.popular.PopularGames
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.components.CoverCard
import com.galstruo.app.ui.components.GameCard
import com.galstruo.app.ui.components.GameRow
import com.galstruo.app.ui.components.staggeredIn
import com.galstruo.app.ui.home.HomeViewModel
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import coil.compose.AsyncImage
import com.galstruo.app.data.kungal.KungalAuth
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenGame: (GameItem) -> Unit,
    onOpenKungalLogin: () -> Unit,
    uiSettings: UiSettings,
    homeViewModel: HomeViewModel = viewModel(),
) {
    // 未开启 NSFW 时过滤限制级作品(随机接口无标记,今日推荐逐个查详情过滤)
    val latest = if (uiSettings.showNsfw) homeViewModel.latest
    else homeViewModel.latest.filterNot { it.restricted }
    // 进入首页时加载今日推荐;NSFW 开关变化时自动重新过滤
    LaunchedEffect(uiSettings.showNsfw) { homeViewModel.rollDaily(uiSettings.showNsfw) }
    // 热门经典:人工精选名作清单(写死在应用里,不依赖网络),按 NSFW 设置过滤
    val hotGames = remember(uiSettings.showNsfw) {
        PopularGames.list.filter { uiSettings.showNsfw || !it.restricted }
    }
    // 首次进入页面:各区块交错入场
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // 最近浏览:接着上次看(浏览历史实时刷新)
    val recent by HistoryStore.history.collectAsStateWithLifecycle()
    // 鲲账号状态(顶部头像圆钮用);进入首页时刷新一次,头像保持新鲜
    val kungalUser by KungalAuth.user.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { KungalAuth.refresh() }

    // Play 商店式滚动动画:向下滚动时大标题逐渐收缩、顶栏收紧
    val listState = rememberLazyListState()
    val collapse by remember {
        derivedStateOf {
            (listState.firstVisibleItemScrollOffset / 120f).coerceIn(0f, 1f)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        // 顶部第一行(Play 风格):大标题 + 搜索圆钮 + 头像圆钮(点头像进鲲,未登录进登录页)
        // 向下滚动时标题从 28sp 缩到 20sp,顶栏间距同步收紧
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = 16.dp,
                        vertical = lerp(6.dp, 1.dp, collapse),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "GalAether",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = lerp(28.sp, 20.sp, collapse),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 搜索圆钮
                Surface(
                    onClick = onOpenSearch,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(lerp(40.dp, 36.dp, collapse)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                // 头像圆钮:已登录显示鲲头像,未登录显示默认人像
                Surface(
                    onClick = onOpenKungalLogin,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(lerp(40.dp, 36.dp, collapse)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (kungalUser?.avatar?.isNotBlank() == true) {
                            AsyncImage(
                                model = kungalUser?.avatar,
                                contentDescription = "鲲galgame 账号",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = "鲲galgame 账号",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        // 第二行:搜索条(点击进搜索页)
        item {
            Surface(
                onClick = onOpenSearch,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(40.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "搜索游戏、会社…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            homeViewModel.loading -> {
                // 顶部细加载条 + 骨架占位(Pixel 风格,替代转圈)
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                    )
                }
                item {
                    val skelAlpha by rememberInfiniteTransition(label = "skel").animateFloat(
                        initialValue = 0.45f,
                        targetValue = 0.85f,
                        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                        label = "skelAlpha",
                    )
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Spacer(Modifier.height(12.dp))
                        SkelBox(Modifier.width(110.dp).height(22.dp), skelAlpha)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(3) {
                                SkelBox(Modifier.width(104.dp).height(138.dp), skelAlpha)
                            }
                        }
                    }
                }
            }

            homeViewModel.error != null -> item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("加载失败", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        homeViewModel.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = homeViewModel::refresh) { Text("重试") }
                }
            }

            else -> {
                // 今日推荐:区块始终显示,空的时候给提示,不会整块消失(设置里可隐藏)
                if (uiSettings.showHomeDaily) {
                    item {
                        staggeredIn(0, entered) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "今日推荐",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { homeViewModel.rerollDaily(uiSettings.showNsfw) }) {
                                    Text("换一批")
                                }
                            }
                        }
                    }
                    if (homeViewModel.daily.isEmpty()) {
                        item {
                            if (homeViewModel.dailyLoading) {
                                Column {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "正在为你挑选…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            } else {
                                Text(
                                    "今天手气不太好,一个都没选出来,点右上角「换一批」再试试",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    } else {
                        item {
                            // 「换一批」:旧的一批向左滑出,新的一批从右弹入
                            AnimatedContent(
                                targetState = homeViewModel.daily,
                                transitionSpec = {
                                    (slideInHorizontally { it / 5 } + fadeIn()).togetherWith(
                                        slideOutHorizontally { -it / 5 } + fadeOut()
                                    )
                                },
                                label = "dailyRoll",
                            ) { daily ->
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                ) {
                                    items(daily, key = { it.gameId }) { game ->
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
                }
                if (uiSettings.showHomeRecent && recent.isNotEmpty()) {
                    item {
                        staggeredIn(1, entered) {
                            Text(
                                "最近浏览",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item {
                        staggeredIn(2, entered) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(recent.take(10), key = { it.gid }) { entry ->
                                    val game = GameItem(
                                        gid = entry.gid,
                                        mainName = entry.name,
                                        mainImg = entry.coverUrl,
                                    )
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
                if (uiSettings.showHomeHot && hotGames.isNotEmpty()) {
                    item {
                        staggeredIn(1, entered) {
                            Text(
                                "热门经典",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item {
                        staggeredIn(2, entered) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(hotGames, key = { it.gid }) { game ->
                                    CoverCard(
                                        PopularGames.toGameItem(game),
                                        onClick = { onOpenGame(PopularGames.toGameItem(game)) },
                                        modifier = Modifier.width(140.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (uiSettings.showHomeForYou && homeViewModel.forYou.isNotEmpty()) {
                    item {
                        staggeredIn(2, entered) {
                            Text(
                                "猜你喜欢",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item {
                        staggeredIn(3, entered) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(homeViewModel.forYou, key = { it.gameId }) { game ->
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
                if (uiSettings.showHomeLatest && latest.isNotEmpty()) {
                    item {
                        staggeredIn(3, entered) {
                            Text(
                                "最新发行",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                if (uiSettings.showHomeLatest) {
                    itemsIndexed(latest, key = { _, it -> it.gameId }) { index, game ->
                        staggeredIn(index, entered) {
                            // 列表样式:大卡片 / 紧凑行(设置里可切换)
                            if (uiSettings.listStyle == ListStyle.COMPACT) {
                                GameRow(
                                    game,
                                    onClick = { onOpenGame(game) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            } else {
                                GameCard(
                                    game,
                                    onClick = { onOpenGame(game) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                if (latest.isEmpty() && homeViewModel.daily.isEmpty() && homeViewModel.forYou.isEmpty()) {
                    item {
                        Text(
                            "当前列表为空(已按设置隐藏限制级内容,可在设置中打开)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                item {
                    Text(
                        "游戏信息来自月幕Galgame(ymgal.games)· 资源版权归原作者所有,请支持正版",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

/** 骨架屏占位块:扁平淡色,透明度呼吸 */
@Composable
private fun SkelBox(modifier: Modifier = Modifier, alpha: Float) {
    Box(
        modifier.background(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
        ),
    )
}
