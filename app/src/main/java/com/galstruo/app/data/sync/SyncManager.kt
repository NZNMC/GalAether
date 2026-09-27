package com.galstruo.app.data.sync

import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.data.local.SearchHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 云同步总控:
 * - 自动:收藏/历史/搜索/下载记录/设置任一变,停顿 8 秒后自动同步;
 * - 对账:每次同步前先看云端——云端比本机新就拉下来,本机新就推上去;
 * - 双通道:GitHub 私有 Gist 与 WebDAV 网盘各自独立,两边都会持有最新数据,
 *   新设备登录/配置任一通道即可完整恢复。
 */
@OptIn(FlowPreview::class)
object SyncManager {

    /** 界面展示用的同步状态 */
    data class SyncStatus(
        val syncing: Boolean = false,
        val lastSyncAt: Long = 0,
        val lastMessage: String = "",
    )

    val status = MutableStateFlow(SyncStatus())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settings: SettingsStore? = null

    private data class CloudSnapshot(val source: String, val data: BackupData)

    /** 应用启动时调用一次(重复调用忽略) */
    fun init(store: SettingsStore) {
        if (settings != null) return
        settings = store
        scope.launch {
            // OAuth App 更换后(如换 Client ID)旧令牌作废,自动退出登录,让用户重新授权。
            // 旧版本登录时没有记录 Client ID(值为空),同样按旧令牌处理,强制重新授权一次
            val cfg = store.uiSettings.first()
            if (cfg.githubClientId != GitHubSync.CLIENT_ID) {
                store.clearGithubAuth()
            }
            // 数据变化自动同步(跳过启动时的初值组合,只有真正变化才触发)
            combine(
                FavoriteStore.favorites,
                HistoryStore.history,
                SearchHistoryStore.keywords,
                DownloadManager.records,
                store.uiSettings,
            ) { _, _, _, _, _ -> Unit }
                .drop(1)
                .debounce(8000)
                .collect { runCatching { syncNow() } }
        }
        // 启动对账:稍等设置恢复完,和云端比对一次
        scope.launch {
            delay(2000)
            runCatching { syncNow() }
        }
    }

    /** 手动"立即同步"(自动对账:该拉就拉,该推就推),返回给界面的提示文字 */
    suspend fun syncNow(): String {
        val s = settings ?: return "同步模块未初始化"
        val cfg = s.uiSettings.first()
        if (!hasTarget(cfg)) return "尚未配置云同步(先在设置里登录 GitHub 或填写 WebDAV)"
        status.value = status.value.copy(syncing = true)
        return try {
            val cloud = fetchCloud(cfg)
            val msg: String
            if (cloud != null && (cfg.lastSyncAt == 0L || cloud.data.updatedAt > cfg.lastSyncAt)) {
                val merged = SyncData.apply(cloud.data, cfg, s)
                s.setLastSyncAt(cloud.data.updatedAt)
                doPush(s, merged)   // 让另一个通道也持有最新数据
                msg = "已从云端同步最新数据(来源:${cloud.source})"
            } else {
                msg = doPush(s, cfg)
            }
            val now = System.currentTimeMillis()
            status.value = SyncStatus(syncing = false, lastSyncAt = now, lastMessage = msg)
            msg
        } catch (e: Exception) {
            val msg = "同步失败:${e.message ?: "网络异常"}"
            status.value = status.value.copy(syncing = false, lastMessage = msg)
            msg
        }
    }

    /** 手动"从云端恢复":无条件用云端数据覆盖本机,返回提示文字 */
    suspend fun pullNow(): String {
        val s = settings ?: return "同步模块未初始化"
        val cfg = s.uiSettings.first()
        if (!hasTarget(cfg)) return "尚未配置云同步"
        return try {
            val cloud = fetchCloud(cfg)
                ?: return "云端还没有备份数据(先点「立即同步」把本机数据传上去)"
            SyncData.apply(cloud.data, cfg, s)
            s.setLastSyncAt(cloud.data.updatedAt)
            status.value = status.value.copy(
                syncing = false, lastSyncAt = System.currentTimeMillis(),
                lastMessage = "已从云端恢复",
            )
            "恢复完成:收藏 ${cloud.data.favorites.size} 部 · 历史 ${cloud.data.history.size} 条"
        } catch (e: Exception) {
            "恢复失败:${e.message ?: "网络异常"}"
        }
    }

    /** 用户点「登录」后立即触发一次同步(创建 Gist / 拉取云端数据) */
    suspend fun refreshAfterLogin() = syncNow()

    private fun hasTarget(cfg: UiSettings): Boolean =
        (cfg.githubToken.isNotBlank() && cfg.githubSyncEnabled) ||
            (cfg.webdavEnabled && cfg.webdavUrl.isNotBlank() && cfg.webdavUser.isNotBlank())

    /** 从两个通道各取一次,返回更新时间最新的一份(都取不到返回 null) */
    private suspend fun fetchCloud(cfg: UiSettings): CloudSnapshot? {
        val candidates = mutableListOf<CloudSnapshot>()
        if (cfg.githubToken.isNotBlank()) {
            runCatching {
                GitHubSync.fetch(cfg.githubToken, cfg.gistId)?.let { json ->
                    SyncData.fromJson(json)?.let { candidates += CloudSnapshot("GitHub", it) }
                }
            }
        }
        if (cfg.webdavEnabled && cfg.webdavUrl.isNotBlank() && cfg.webdavUser.isNotBlank()) {
            runCatching {
                WebDavSync.fetch(cfg.webdavUrl, cfg.webdavUser, cfg.webdavPassword)?.let { json ->
                    SyncData.fromJson(json)?.let { candidates += CloudSnapshot("WebDAV", it) }
                }
            }
        }
        return candidates.maxByOrNull { it.data.updatedAt }
    }

    /** 把本机数据推到所有开启的通道,返回提示文字 */
    private suspend fun doPush(s: SettingsStore, cfg: UiSettings): String {
        val data = SyncData.build(cfg)
        val json = SyncData.toJson(data)
        var pushed = 0
        if (cfg.githubToken.isNotBlank() && cfg.githubSyncEnabled) {
            val id = GitHubSync.push(cfg.githubToken, cfg.gistId, json)
            if (id != cfg.gistId) s.setGistId(id)
            pushed++
        }
        if (cfg.webdavEnabled && cfg.webdavUrl.isNotBlank() && cfg.webdavUser.isNotBlank()) {
            WebDavSync.push(cfg.webdavUrl, cfg.webdavUser, cfg.webdavPassword, json)
            pushed++
        }
        return if (pushed > 0) "已同步到云端($pushed 个通道)"
        else "未配置可用的云同步通道"
    }
}
