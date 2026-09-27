package com.galstruo.app.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galstruo.app.data.kungal.KungalApi
import com.galstruo.app.data.kungal.KungalAuth
import com.galstruo.app.data.kungal.KungalResource
import com.galstruo.app.data.searchgal.SearchGalApi
import com.galstruo.app.data.searchgal.SearchGalPlatform
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
    /** 网盘资源是否来自官方接口(登录后;含提取码/解压密码) */
    var kungalOfficial by mutableStateOf(false)
        private set

    /** SearchGal 聚合搜索(27+ 资源站) */
    var sgPlatforms by mutableStateOf<List<SearchGalPlatform>>(emptyList())
        private set
    var sgLoading by mutableStateOf(false)
        private set
    var sgError by mutableStateOf<String?>(null)
        private set
    var sgSearched by mutableStateOf(false)
        private set
    /** 聚合搜索进度:已完成 X / 共 Y 个平台 */
    var sgProgress by mutableStateOf<Pair<Int, Int>?>(null)
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

    /** 在真红小站搜索本游戏的下载资源(先搜中文名,无结果再搜日文名),同时搜索鲲galgame 和更多资源站 */
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
        searchMoreSites()
    }

    /** 在鲲galgame 搜索网盘资源(先搜中文名,无结果再搜日文名);已登录时升级为官方接口(资源更全,含提取码/解压密码) */
    fun searchKungal() {
        val game = detail ?: return
        viewModelScope.launch {
            kungalLoading = true
            kungalError = null
            kungalOfficial = false
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
                // 已登录:走官方接口拿更全的资源列表(任意一步失败就保持网页解析结果)
                val cookies = KungalAuth.cookie()
                if (found.isNotEmpty() && cookies != null) {
                    val official = officialResourcesFor(found, cookies)
                    if (!official.isNullOrEmpty()) {
                        kungalResources = official
                        kungalOfficial = true
                    }
                }
            } catch (e: Exception) {
                kungalError = e.message ?: "网络异常"
            } finally {
                kungalLoading = false
                kungalSearched = true
            }
        }
    }

    /** 官方接口链路:SSR 搜索结果第一条 → 资源详情页反查 galgameId → /api/galgame/{id}/resource/all */
    private suspend fun officialResourcesFor(ssr: List<KungalResource>, cookies: String): List<KungalResource>? = try {
        val gid = KungalApi.galgameIdOf(ssr.first().id) ?: return null
        KungalApi.officialResources(gid, cookies)
    } catch (e: Exception) {
        null
    }

    /** SearchGal 聚合搜索:一次搜索 27+ 个资源站,结果按平台流式返回(边搜边显示) */
    fun searchMoreSites() {
        val game = detail ?: return
        viewModelScope.launch {
            sgLoading = true
            sgError = null
            sgSearched = false
            sgPlatforms = emptyList()
            sgProgress = null
            try {
                val keywords = listOfNotNull(game.chineseName, game.name).distinct()
                var found = emptyList<SearchGalPlatform>()
                for (kw in keywords.take(2)) {
                    if (found.isNotEmpty()) break
                    found = SearchGalApi.searchGal(
                        kw,
                        onProgress = { c, t -> sgProgress = c to t },
                        onPlatform = { sgPlatforms = sgPlatforms + it },
                    )
                }
                sgPlatforms = found.distinctBy { it.name }
                if (sgPlatforms.isEmpty()) {
                    sgError = "27+ 个资源站都没有找到该游戏"
                }
            } catch (e: Exception) {
                sgError = e.message ?: "网络异常"
            } finally {
                sgLoading = false
                sgSearched = true
            }
        }
    }
}
