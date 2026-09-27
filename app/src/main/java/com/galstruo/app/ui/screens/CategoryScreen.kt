package com.galstruo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galstruo.app.data.ListStyle
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.ui.category.CategoryViewModel
import com.galstruo.app.ui.components.GameCard
import com.galstruo.app.ui.components.GameRow
import java.time.YearMonth

/** 分类浏览:按发行月份浏览游戏 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onOpenGame: (GameItem) -> Unit,
    uiSettings: UiSettings,
    categoryViewModel: CategoryViewModel = viewModel(),
) {
    val vm = categoryViewModel
    // 未开启 NSFW 时过滤限制级作品
    val games = if (uiSettings.showNsfw) vm.games else vm.games.filterNot { it.restricted }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("分类浏览") })
        Text(
            "按发行月份浏览月幕收录的游戏",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // 月份选择:横向滑动,当月在前
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            vm.months.forEach { month ->
                MonthChip(
                    label = "${month.year}年${month.monthValue}月",
                    selected = month == vm.selected,
                    onClick = { vm.select(month) },
                )
            }
        }
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            vm.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        vm.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.load(vm.selected) }) { Text("重试") }
                }
            }

            games.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (vm.games.isEmpty()) "这个月没有收录发行的游戏"
                    else "这个月只有限制级作品,已按设置隐藏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(games, key = { it.gameId }) { game ->
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
    }
}

@Composable
private fun MonthChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
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
