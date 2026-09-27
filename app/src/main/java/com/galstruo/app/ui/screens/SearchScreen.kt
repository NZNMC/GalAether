package com.galstruo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galstruo.app.data.ListStyle
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.local.SearchHistoryStore
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.components.EmptyState
import com.galstruo.app.ui.components.ErrorState
import com.galstruo.app.ui.components.GameCard
import com.galstruo.app.ui.components.GameRow
import com.galstruo.app.ui.components.LoadingState
import com.galstruo.app.ui.search.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenGame: (GameItem) -> Unit,
    uiSettings: UiSettings,
    searchViewModel: SearchViewModel = viewModel(),
) {
    val history by SearchHistoryStore.keywords.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = searchViewModel.keyword,
                    onValueChange = searchViewModel::onKeywordChange,
                    placeholder = { Text("搜索游戏名(中文/日文)") },
                    singleLine = true,
                    shape = RoundedCornerShape(32.dp),
                    trailingIcon = {
                        IconButton(onClick = searchViewModel::search) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchViewModel.search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
        if (searchViewModel.allResults.isNotEmpty()) {
            FilterSortRow(
                onlyChinese = searchViewModel.onlyChinese,
                sort = searchViewModel.sort,
                onToggleChinese = searchViewModel::toggleOnlyChinese,
                onSortChange = searchViewModel::selectSort,
            )
        }
        when {
            searchViewModel.loading -> LoadingState()

            searchViewModel.error != null && searchViewModel.results.isEmpty() -> ErrorState(
                message = searchViewModel.error.orEmpty(),
                onRetry = searchViewModel::search,
            )

            searchViewModel.results.isEmpty() -> {
                if (searchViewModel.allResults.isNotEmpty()) {
                    // 有原始结果但被筛选条件过滤掉了
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "筛选条件下没有结果",
                        subtitle = "试试放宽筛选条件",
                    )
                } else if (history.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "输入关键词开始搜索",
                        subtitle = "支持中文/日文游戏名",
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "搜索历史",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { SearchHistoryStore.clear() }) { Text("清空") }
                        }
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            history.forEach { kw ->
                                Surface(
                                    onClick = {
                                        searchViewModel.onKeywordChange(kw)
                                        searchViewModel.search()
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        kw,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(searchViewModel.results, key = { it.gameId }) { game ->
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
                item {
                    when {
                        searchViewModel.loadingMore -> Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        searchViewModel.hasNext -> LaunchedEffect(Unit) { searchViewModel.loadMore() }
                    }
                }
            }
        }
    }
}

/** 筛选(官方中文)+ 排序 chips,只在本页使用(月幕接口不支持服务端筛选) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortRow(
    onlyChinese: Boolean,
    sort: SearchViewModel.SortOption,
    onToggleChinese: () -> Unit,
    onSortChange: (SearchViewModel.SortOption) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "筛选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = !onlyChinese,
                onClick = { if (onlyChinese) onToggleChinese() },
                label = { Text("全部") },
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = onlyChinese,
                onClick = { if (!onlyChinese) onToggleChinese() },
                label = { Text("官方中文") },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Text(
                "排序",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            val options = listOf(
                SearchViewModel.SortOption.DEFAULT to "相关度",
                SearchViewModel.SortOption.NEWEST to "最新发行",
                SearchViewModel.SortOption.OLDEST to "最早发行",
                SearchViewModel.SortOption.TOP_RATED to "评分最高",
            )
            options.forEach { (option, label) ->
                FilterChip(
                    selected = sort == option,
                    onClick = { onSortChange(option) },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}
