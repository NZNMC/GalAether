package com.galstruo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.components.CoverCard
import com.galstruo.app.ui.components.GameCard
import com.galstruo.app.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenGame: (GameItem) -> Unit,
    uiSettings: UiSettings,
    homeViewModel: HomeViewModel = viewModel(),
) {
    // 未开启 NSFW 时过滤限制级作品(随机接口无标记,直接隐藏随机推荐)
    val latest = if (uiSettings.showNsfw) homeViewModel.latest
    else homeViewModel.latest.filterNot { it.restricted }
    val random = if (uiSettings.showNsfw) homeViewModel.random else emptyList()

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
                if (random.isNotEmpty()) {
                    item {
                        Text(
                            "随机推荐",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(random, key = { it.gameId }) { game ->
                                CoverCard(
                                    game,
                                    onClick = { onOpenGame(game) },
                                    modifier = Modifier.width(140.dp),
                                )
                            }
                        }
                    }
                }
                if (latest.isNotEmpty()) {
                    item {
                        Text(
                            "最新发行",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(latest, key = { it.gameId }) { game ->
                    GameCard(
                        game,
                        onClick = { onOpenGame(game) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                if (latest.isEmpty() && random.isEmpty()) {
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
