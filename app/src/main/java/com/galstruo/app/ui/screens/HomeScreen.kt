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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.components.CoverCard
import com.galstruo.app.ui.components.GameCard
import com.galstruo.app.ui.components.staggeredIn
import com.galstruo.app.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenGame: (GameItem) -> Unit,
    uiSettings: UiSettings,
    homeViewModel: HomeViewModel = viewModel(),
) {
    // 未开启 NSFW 时过滤限制级作品(随机接口无标记,今日推荐逐个查详情过滤)
    val latest = if (uiSettings.showNsfw) homeViewModel.latest
    else homeViewModel.latest.filterNot { it.restricted }
    // 进入首页时加载今日推荐;NSFW 开关变化时自动重新过滤
    LaunchedEffect(uiSettings.showNsfw) { homeViewModel.rollDaily(uiSettings.showNsfw) }
    // 首次进入页面:各区块交错入场
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            LargeTopAppBar(
                title = { Text("GalAether") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                },
            )
        }
        when {
            homeViewModel.loading -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
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
                if (homeViewModel.daily.isNotEmpty() || homeViewModel.dailyLoading) {
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
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("正在为你挑选…", style = MaterialTheme.typography.bodyMedium)
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
                if (homeViewModel.forYou.isNotEmpty()) {
                    item {
                        staggeredIn(1, entered) {
                            Text(
                                "猜你喜欢",
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
                if (latest.isNotEmpty()) {
                    item {
                        staggeredIn(2, entered) {
                            Text(
                                "最新发行",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                itemsIndexed(latest, key = { _, it -> it.gameId }) { index, game ->
                    staggeredIn(index, entered) {
                        GameCard(
                            game,
                            onClick = { onOpenGame(game) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
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
                        "游戏信息来自月幕Galgame(ymgal.games)",
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
