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

    /** 排序方式(月幕接口不支持服务端排序,全部在本地处理) */
    enum class SortOption { DEFAULT, NEWEST, OLDEST, TOP_RATED }

    var keyword by mutableStateOf("")
        private set
    /** 筛选排序后的最终结果(界面显示这份) */
    var results by mutableStateOf<List<GameItem>>(emptyList())
        private set
    /** 未筛选的原始结果(翻页累积),用于切换筛选/排序时重新计算 */
    var allResults by mutableStateOf<List<GameItem>>(emptyList())
        private set
    var onlyChinese by mutableStateOf(false)
        private set
    var sort by mutableStateOf(SortOption.DEFAULT)
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
                allResults = page.result ?: emptyList()
                applyFilterSort()
                hasNext = page.hasNext
                if (allResults.isEmpty()) error = "没有找到相关游戏,换个关键词试试"
                else SearchHistoryStore.add(kw)
            } catch (e: Exception) {
                allResults = emptyList()
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
                val page = YmgalRepository.search(kw, (allResults.size / 20) + 1)
                allResults = allResults + (page.result ?: emptyList())
                applyFilterSort()
                hasNext = page.hasNext
            } catch (e: Exception) {
                error = "加载更多失败:${e.message ?: "网络异常"}"
            } finally {
                loadingMore = false
            }
        }
    }

    fun toggleOnlyChinese() {
        onlyChinese = !onlyChinese
        applyFilterSort()
    }

    fun selectSort(option: SortOption) {
        if (sort == option) return
        sort = option
        applyFilterSort()
    }

    /** 按当前筛选与排序条件重新计算显示列表 */
    private fun applyFilterSort() {
        var list = allResults
        if (onlyChinese) list = list.filter { it.haveChinese }
        list = when (sort) {
            SortOption.DEFAULT -> list
            SortOption.NEWEST -> list.sortedWith(compareByDescending(nullsLast<String>()) { it.releaseDate })
            SortOption.OLDEST -> list.sortedWith(compareBy(nullsLast<String>()) { it.releaseDate })
            SortOption.TOP_RATED -> list.sortedByDescending { it.score?.toFloatOrNull() ?: -1f }
        }
        results = list
    }
}
