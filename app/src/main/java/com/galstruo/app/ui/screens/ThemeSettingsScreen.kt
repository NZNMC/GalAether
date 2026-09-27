package com.galstruo.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.ThemeMode
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.ui.components.ColorWheel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    onCheckedChange = { scope.launch { settings.setShowNsfw(it) } },
                )
            }
            Spacer(Modifier.height(20.dp))
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
