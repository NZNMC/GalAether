package com.galstruo.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.galstruo.app.data.local.HistoryEntry
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.ui.components.EmptyState
import com.galstruo.app.ui.components.HistoryIcon
import com.galstruo.app.ui.components.pressScale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 浏览历史页:打开过的游戏自动记录,可一键清空 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenGame: (Long) -> Unit) {
    val history by HistoryStore.history.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("浏览历史") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (history.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) { Text("清空") }
                }
            },
        )
        if (history.isEmpty()) {
            EmptyState(
                icon = HistoryIcon,
                title = "暂无浏览记录",
                subtitle = "打开过的游戏会自动记录在这里",
            )
        } else {
            LazyColumn {
                items(history, key = { it.gid }) { entry ->
                    HistoryRow(
                        entry,
                        onClick = { onOpenGame(entry.gid) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空浏览历史?") },
            text = { Text("清空后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    HistoryStore.clear()
                    showClearDialog = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, shape = RoundedCornerShape(28.dp), modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .pressScale()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.coverUrl,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatViewedTime(entry.viewedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 浏览时间:今天/昨天显示"今天 14:32",更早显示"09-21 14:32" */
private fun formatViewedTime(millis: Long): String {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dayFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val now = cal.timeInMillis
    cal.timeInMillis = millis
    val time = timeFmt.format(Date(millis))
    return when {
        isSameDay(now, millis) -> "今天 $time"
        isSameDay(now - 24 * 3600_000L, millis) -> "昨天 $time"
        else -> "${dayFmt.format(Date(millis))} $time"
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
