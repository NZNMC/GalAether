package com.galstruo.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.galstruo.app.data.local.FavoriteGame
import com.galstruo.app.data.local.FavoriteStatus
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.ui.components.EmptyState
import com.galstruo.app.ui.components.pressScale

/** 收藏页:展示收藏的游戏,可按状态筛选,点心形可以取消收藏 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(onOpenGame: (Long) -> Unit) {
    val favorites by FavoriteStore.favorites.collectAsStateWithLifecycle()
    // 状态筛选:0=全部 1=想玩 2=在玩 3=已通关
    var filter by remember { mutableIntStateOf(0) }
    val shown = if (filter == 0) favorites else favorites.filter { it.status == filter }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("收藏") })
        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Favorite,
                title = "还没有收藏",
                subtitle = "在游戏详情页点右上角的❤,收藏的游戏就会出现在这里",
            )
        } else {
            // 状态筛选 chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                StatusChip("全部 ${favorites.size}", filter == 0) { filter = 0 }
                listOf(FavoriteStatus.WANT, FavoriteStatus.PLAYING, FavoriteStatus.DONE).forEach { s ->
                    val count = favorites.count { it.status == s }
                    StatusChip("${FavoriteStatus.label(s)} $count", filter == s) { filter = s }
                }
            }
            LazyColumn {
                items(shown, key = { it.gid }) { fav ->
                    FavoriteRow(
                        fav,
                        onClick = { onOpenGame(fav.gid) },
                        onRemove = { FavoriteStore.remove(fav.gid) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** 状态筛选小胶囊 */
@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun FavoriteRow(
    fav: FavoriteGame,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 编辑状态/备注弹窗
    var showEdit by remember { mutableStateOf(false) }
    Card(onClick = onClick, shape = RoundedCornerShape(28.dp), modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .pressScale()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = fav.coverUrl,
                contentDescription = fav.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    fav.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fav.status != FavoriteStatus.NONE || fav.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fav.status != FavoriteStatus.NONE) {
                            StatusBadge(fav.status)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (fav.note.isNotBlank()) {
                            Text(
                                fav.note,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!fav.releaseDate.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        fav.releaseDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { showEdit = true }) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑状态与备注",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = "取消收藏",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (showEdit) {
        EditFavoriteDialog(
            fav = fav,
            onDismiss = { showEdit = false },
        )
    }
}

/** 状态徽章:想玩=紫、在玩=蓝、已通关=绿 */
@Composable
private fun StatusBadge(status: Int) {
    val (bg, fg) = when (status) {
        FavoriteStatus.WANT -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        FavoriteStatus.PLAYING -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        FavoriteStatus.DONE -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        FavoriteStatus.label(status),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 编辑弹窗:选状态 + 写备注 */
@Composable
private fun EditFavoriteDialog(
    fav: FavoriteGame,
    onDismiss: () -> Unit,
) {
    var status by remember { mutableIntStateOf(fav.status) }
    var note by remember { mutableStateOf(fav.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fav.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text("状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FavoriteStatus.NONE, FavoriteStatus.WANT, FavoriteStatus.PLAYING, FavoriteStatus.DONE)
                        .forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (status == s) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .pressScale()
                                    .clickable { status = s },
                            ) {
                                Text(
                                    FavoriteStatus.label(s),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (status == s) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(100) },
                    label = { Text("备注") },
                    placeholder = { Text("例如:卡在第3章 / 需要攻略") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                FavoriteStore.update(fav.gid, status, note.trim())
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
