package com.galstruo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.galstruo.app.BuildConfig
import com.galstruo.app.ui.components.HistoryIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenThemeSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("我的") })
        ListItem(
            headlineContent = { Text("设置") },
            supportingContent = { Text("主题 · 深色模式 · 内容 · 数据与存储") },
            leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier.clickable { onOpenThemeSettings() },
        )
        ListItem(
            headlineContent = { Text("浏览历史") },
            supportingContent = { Text("打开过的游戏自动记录") },
            leadingContent = { Icon(HistoryIcon, contentDescription = null) },
            modifier = Modifier.clickable { onOpenHistory() },
        )
        ListItem(
            headlineContent = { Text("关于") },
            supportingContent = { Text("GalAether ${BuildConfig.VERSION_NAME}") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
        )
    }
}
