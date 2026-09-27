package com.galstruo.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 搜索历史:搜索成功时记录关键词,最多 10 条,最新在前 */
object SearchHistoryStore {

    private const val FILE = "search_history.json"
    private const val MAX = 10

    private val _keywords = MutableStateFlow<List<String>>(emptyList())
    val keywords: StateFlow<List<String>> = _keywords

    /** 应用启动时从本地文件恢复 */
    fun load() {
        _keywords.value = loadList(FILE)
    }

    /** 记录一次搜索关键词(去重、最新在前) */
    fun add(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val next = (listOf(kw) + _keywords.value.filterNot { it == kw }).take(MAX)
        _keywords.value = next
        saveList(FILE, next)
    }

    fun remove(keyword: String) {
        _keywords.value = _keywords.value.filterNot { it == keyword }
        saveList(FILE, _keywords.value)
    }

    fun clear() {
        _keywords.value = emptyList()
        saveList(FILE, emptyList<String>())
    }
}
