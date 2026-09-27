package com.galstruo.app.data.sync

import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.UiSettings
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.download.DownloadRecord
import com.galstruo.app.data.local.FavoriteGame
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.data.local.HistoryEntry
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.data.local.SearchHistoryStore
import com.google.gson.Gson

/**
 * 备份 / 云同步的统一数据结构(手动备份文件与云端 Gist、WebDAV 共用)。
 * - version:格式版本,将来升级格式时用于兼容;
 * - updatedAt:最后修改时间戳,同步时用来判断"云端新还是本机新";
 * - settings:用户设置(其中 GitHub 令牌与 WebDAV 密码属于凭据,打包时会被清空,只留本机)。
 */
data class BackupData(
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val favorites: List<FavoriteGame> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val downloadRecords: List<DownloadRecord> = emptyList(),
    val settings: UiSettings = UiSettings(),
)

/** 打包与回写的公共逻辑(手动备份和云同步都用它) */
object SyncData {

    private val gson = Gson()

    fun toJson(data: BackupData): String = gson.toJson(data)

    /** 解析失败返回 null(不是备份文件 / 格式损坏) */
    fun fromJson(json: String): BackupData? =
        runCatching { gson.fromJson(json, BackupData::class.java) }.getOrNull()

    /** 收集本机全部数据,打包成一份备份。凭据(GitHub 令牌 / WebDAV 密码)永不写入备份 */
    fun build(local: UiSettings): BackupData = BackupData(
        favorites = FavoriteStore.favorites.value,
        history = HistoryStore.history.value,
        searchHistory = SearchHistoryStore.keywords.value,
        downloadRecords = DownloadManager.records.value,
        settings = local.copy(githubToken = "", webdavPassword = ""),
    )

    /**
     * 把备份数据写回本机(导入备份 / 从云端恢复共用),返回合并后的设置。
     * 设置合并规则:凭据保留本机的(云端备份里本来就没有),其余全部换成备份里的。
     */
    suspend fun apply(data: BackupData, local: UiSettings, settings: SettingsStore): UiSettings {
        FavoriteStore.replaceAll(data.favorites)
        HistoryStore.replaceAll(data.history)
        SearchHistoryStore.replaceAll(data.searchHistory)
        DownloadManager.replaceRecords(data.downloadRecords)
        val merged = data.settings.copy(
            githubToken = local.githubToken,
            webdavPassword = local.webdavPassword,
        )
        settings.applyAll(merged)
        return merged
    }
}
