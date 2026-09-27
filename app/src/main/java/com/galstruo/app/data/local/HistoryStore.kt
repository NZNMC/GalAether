package com.galstruo.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 浏览历史的一条记录 */
data class HistoryEntry(
    val gid: Long,
    val name: String,
    val coverUrl: String? = null,
    val viewedAt: Long = System.currentTimeMillis(),
)

/** 浏览历史:打开游戏详情自动记录,同一部游戏只留最新一条,最多 50 条 */
object HistoryStore {

    private const val FILE = "history.json"
    private const val MAX = 50

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history

    /** 应用启动时从本地文件恢复 */
    fun load() {
        _history.value = loadList(FILE)
    }

    /** 记录一次浏览(去重、最新在前) */
    fun record(gid: Long, name: String, coverUrl: String?) {
        if (gid == 0L) return
        val next = (listOf(HistoryEntry(gid, name, coverUrl)) +
            _history.value.filterNot { it.gid == gid }).take(MAX)
        _history.value = next
        saveList(FILE, next)
    }

    fun clear() {
        _history.value = emptyList()
        saveList(FILE, emptyList<HistoryEntry>())
    }

    /** 整体替换(导入备份时用) */
    fun replaceAll(list: List<HistoryEntry>) {
        _history.value = list
        saveList(FILE, list)
    }
}
