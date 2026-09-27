package com.galstruo.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galstruo.app.data.archive.SmartArchive
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.download.DownloadRecord
import com.galstruo.app.data.download.DownloadState
import com.galstruo.app.data.download.DownloadTask
import com.galstruo.app.ui.components.DownloadIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 下载管理:集中管理所有下载。
 * - 下载中:进度 + 取消
 * - 下载失败:重试 / 删除
 * - 已完成:安装(APK)/ 保存到下载目录 / 打开下载目录 / 删除
 * 记录持久化在本地,重启 App 后依然可见。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    val records by DownloadManager.records.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<DownloadRecord?>(null) }
    // 已完成区的版本类型筛选
    var doneFilter by remember { mutableStateOf<String?>(null) }

    val active = tasks.values.filter {
        it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING
    }
    val errorRecords = records.filter { it.state == "ERROR" && tasks[it.id]?.state !in listOf(
        DownloadState.QUEUED, DownloadState.DOWNLOADING
    ) }
    val doneRecords = records.filter { it.state == "DONE" && tasks[it.id]?.state !in listOf(
        DownloadState.QUEUED, DownloadState.DOWNLOADING
    ) }
    val shownDone = doneRecords.filter { doneFilter == null || it.typeLabel == doneFilter }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("下载管理") })
        if (active.isEmpty() && errorRecords.isEmpty() && doneRecords.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        DownloadIcon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("还没有下载记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "去游戏详情页的「下载资源」区,\n点「下载」后就会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (active.isNotEmpty()) {
                    item { SectionTitle("下载中 · ${active.size}") }
                    items(active, key = { it.id }) { task ->
                        ActiveRow(task, Modifier.animateItem())
                    }
                }
                if (errorRecords.isNotEmpty()) {
                    item { SectionTitle("下载失败 · ${errorRecords.size}") }
                    items(errorRecords, key = { it.id }) { rec ->
                        ErrorRow(rec, onDelete = { deleteTarget = rec }, modifier = Modifier.animateItem())
                    }
                }
                if (doneRecords.isNotEmpty()) {
                    item { SectionTitle("已完成 · ${doneRecords.size}") }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            DoneFilterChip("全部", doneFilter == null) { doneFilter = null }
                            doneRecords.map { it.typeLabel }.distinct().forEach { type ->
                                DoneFilterChip(type, doneFilter == type) { doneFilter = type }
                            }
                        }
                    }
                    items(shownDone, key = { it.id }) { rec ->
                        DoneRow(
                            context, rec,
                            onDelete = { deleteTarget = rec },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { rec ->
        val fileExists = remember(rec) { DownloadManager.recordFile(rec).exists() }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条记录?") },
            text = {
                Text(
                    "${rec.fileName}\n\n" +
                        if (fileExists) "将同时删除应用目录里的下载文件。" else "文件已不在应用目录中。" +
                        "已复制到下载目录 / 已解压到模拟器目录的文件不受影响。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.removeRecord(rec.url)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ActiveRow(task: DownloadTask, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${task.typeLabel} · ${(task.progress * 100).toInt()}%" +
                            if (task.totalBytes > 0) " · ${formatSize(task.downloadedBytes)}/${formatSize(task.totalBytes)}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { DownloadManager.cancel(task.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "取消下载")
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun ErrorRow(rec: DownloadRecord, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rec.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${rec.typeLabel} · ${rec.error ?: "下载失败"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { DownloadManager.retryRecord(rec) }) { Text("重试") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun DoneRow(
    context: Context,
    rec: DownloadRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = remember(rec) { DownloadManager.recordFile(rec) }
    val fileExists = remember(rec) { file.exists() }
    val isApk = rec.typeLabel.contains("安卓") || rec.fileName.endsWith(".apk", true)
    val timeText = remember(rec) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(rec.time))
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                rec.fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TypeChip(rec.typeLabel)
                Text(
                    formatSize(rec.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    rec.archived -> "已归档:文件在系统下载目录或模拟器目录"
                    fileExists -> "文件保存在应用下载目录"
                    else -> "源文件已删除"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isApk && fileExists) {
                    Button(onClick = { SmartArchive.installApk(context, file) }) { Text("安装") }
                }
                if (!isApk && !rec.archived && fileExists) {
                    Button(onClick = {
                        val task = DownloadTask(rec.id, rec.fileName, rec.typeLabel, rec.url)
                        Toast.makeText(context, SmartArchive.saveToDownloads(context, task), Toast.LENGTH_SHORT).show()
                    }) { Text("保存到下载目录") }
                }
                if (rec.archived) {
                    OutlinedButton(onClick = { openDownloadsFolder(context) }) { Text("打开下载目录") }
                }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun DoneFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun TypeChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 打开系统下载目录(文件管理器) */
private fun openDownloadsFolder(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "*/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开下载目录,请用系统文件管理器查看", Toast.LENGTH_SHORT).show()
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
