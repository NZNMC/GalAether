package com.galstruo.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galstruo.app.data.local.SearchHistoryStore
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.data.ymgal.YmgalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    var keyword by mutableStateOf("")
        private set
    var results by mutableStateOf<List<GameItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    fun onKeywordChange(text: String) {
        keyword = text
    }

    fun search() {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val page = YmgalRepository.search(kw, 1)
                results = page.result ?: emptyList()
                hasNext = page.hasNext
                if (results.isEmpty()) error = "没有找到相关游戏,换个关键词试试"
                else SearchHistoryStore.add(kw)
            } catch (e: Exception) {
                results = emptyList()
                error = "搜索失败:${e.message ?: "网络异常"}"
            } finally {
                loading = false
            }
        }
    }

    /** 滚动到底部时加载下一页 */
    fun loadMore() {
        val kw = keyword.trim()
        if (kw.isEmpty() || loading || loadingMore || !hasNext) return
        viewModelScope.launch {
            loadingMore = true
            try {
                val page = YmgalRepository.search(kw, (results.size / 20) + 1)
                results = results + (page.result ?: emptyList())
                hasNext = page.hasNext
            } catch (e: Exception) {
                error = "加载更多失败:${e.message ?: "网络异常"}"
            } finally {
                loadingMore = false
            }
        }
    }
}
