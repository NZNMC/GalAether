package com.galstruo.app.ui.category

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.data.ymgal.YmgalRepository
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 分类浏览:按发行月份浏览月幕收录的游戏 */
class CategoryViewModel : ViewModel() {

    /** 月份选项:当月往前 24 个月 */
    val months: List<YearMonth> = (0 until 24).map { YearMonth.now().minusMonths(it.toLong()) }

    var selected by mutableStateOf(months.first())
        private set
    var games by mutableStateOf<List<GameItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load(months.first())
    }

    fun select(month: YearMonth) {
        if (month == selected) return
        selected = month
        load(month)
    }

    fun load(month: YearMonth) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                games = YmgalRepository.month(month)
            } catch (e: Exception) {
                error = e.message ?: "网络异常"
            } finally {
                loading = false
            }
        }
    }
}
