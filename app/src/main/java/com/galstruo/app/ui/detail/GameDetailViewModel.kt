package com.galstruo.app.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galstruo.app.data.kungal.KungalApi
import com.galstruo.app.data.kungal.KungalResource
import com.galstruo.app.data.shinnku.ShinnkuApi
import com.galstruo.app.data.shinnku.ShinnkuFile
import com.galstruo.app.data.ymgal.GameDetail
import com.galstruo.app.data.ymgal.YmgalRepository
import kotlinx.coroutines.launch

class GameDetailViewModel(private val gid: Long) : ViewModel() {

    var detail by mutableStateOf<GameDetail?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 真红小站资源搜索 */
    var resources by mutableStateOf<List<ShinnkuFile>>(emptyList())
        private set
    var resourcesLoading by mutableStateOf(false)
        private set
    var resourcesError by mutableStateOf<String?>(null)
        private set
    var resourcesSearched by mutableStateOf(false)
        private set

    /** 鲲galgame 网盘资源搜索 */
    var kungalResources by mutableStateOf<List<KungalResource>>(emptyList())
        private set
    var kungalLoading by mutableStateOf(false)
        private set
    var kungalError by mutableStateOf<String?>(null)
        private set
    var kungalSearched by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                detail = YmgalRepository.detail(gid)
            } catch (e: Exception) {
                error = e.message ?: "网络异常"
            } finally {
                loading = false
            }
        }
    }

    /** 在真红小站搜索本游戏的下载资源(先搜中文名,无结果再搜日文名),同时搜索鲲galgame */
    fun searchResources() {
        val game = detail ?: return
        viewModelScope.launch {
            resourcesLoading = true
            resourcesError = null
            try {
                val keywords = listOfNotNull(game.chineseName, game.name).distinct()
                val found = mutableListOf<ShinnkuFile>()
                for (kw in keywords.take(2)) {
                    if (found.isNotEmpty()) break
                    found += ShinnkuApi.searchFiles(kw)
                }
                resources = found.distinctBy { it.filePath }
                if (resources.isEmpty()) {
                    resourcesError = "真红小站没有找到该游戏的资源"
                }
            } catch (e: Exception) {
                resourcesError = e.message ?: "网络异常"
            } finally {
                resourcesLoading = false
                resourcesSearched = true
            }
        }
        searchKungal()
    }

    /** 在鲲galgame 搜索网盘资源(先搜中文名,无结果再搜日文名) */
    fun searchKungal() {
        val game = detail ?: return
        viewModelScope.launch {
            kungalLoading = true
            kungalError = null
            try {
                val keywords = listOfNotNull(game.chineseName, game.name).distinct()
                var found = emptyList<KungalResource>()
                for (kw in keywords.take(2)) {
                    if (found.isNotEmpty()) break
                    found = KungalApi.searchResources(kw)
                }
                kungalResources = found
                if (found.isEmpty()) {
                    kungalError = "鲲galgame 没有找到该游戏的网盘资源"
                }
            } catch (e: Exception) {
                kungalError = e.message ?: "网络异常"
            } finally {
                kungalLoading = false
                kungalSearched = true
            }
        }
    }
}
