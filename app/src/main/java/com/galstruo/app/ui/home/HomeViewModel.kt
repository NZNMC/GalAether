package com.galstruo.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.data.ymgal.YmgalRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    var latest by mutableStateOf<List<GameItem>>(emptyList())
        private set
    var random by mutableStateOf<List<GameItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                latest = YmgalRepository.latest(30)
                random = YmgalRepository.random(6)
            } catch (e: Exception) {
                error = e.message ?: "网络异常"
            } finally {
                loading = false
            }
        }
    }
}
