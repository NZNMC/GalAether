package com.galstruo.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.data.ymgal.GameItem
import com.galstruo.app.data.ymgal.YmgalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    var latest by mutableStateOf<List<GameItem>>(emptyList())
        private set
    /** 今日推荐(随机接口数据;关闭 NSFW 时已逐个查详情过滤) */
    var daily by mutableStateOf<List<GameItem>>(emptyList())
        private set
    /** 猜你喜欢(按收藏/历史的制作会社推荐) */
    var forYou by mutableStateOf<List<GameItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** 今日推荐正在过滤/加载中(单独转圈,不阻塞整页) */
    var dailyLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var dailyJob: Job? = null
    private var lastShowNsfw: Boolean? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                latest = YmgalRepository.latest(30)
            } catch (e: Exception) {
                error = e.message ?: "网络异常"
            } finally {
                loading = false
            }
            loadForYou()  // 静默加载,失败不影响首页
        }
    }

    /** 今日推荐:首次进入/NSFW 开关变化时换一批(已加载则不重复请求) */
    fun rollDaily(showNsfw: Boolean) {
        if (daily.isNotEmpty() && lastShowNsfw == showNsfw) return
        doRoll(showNsfw)
    }

    /** 「换一批」按钮:强制重新随机 */
    fun rerollDaily(showNsfw: Boolean) = doRoll(showNsfw)

    private fun doRoll(showNsfw: Boolean) {
        lastShowNsfw = showNsfw
        dailyJob?.cancel()
        dailyJob = viewModelScope.launch {
            dailyLoading = true
            try {
                daily = collectDaily(showNsfw)
            } catch (e: Exception) {
                daily = emptyList()
            } finally {
                dailyLoading = false
            }
        }
    }

    /**
     * 今日推荐:目标凑满 8 部,两级来源保证不空:
     * 1. 随机推荐池最多抽 4 轮(关闭 NSFW 时逐个查详情过滤限制级;池子里限制级多、查详情还常失败,抽完经常所剩无几);
     * 2. 还不够时用最近发行的作品补齐(发行区间接口自带 restricted 标记,直接过滤,不用逐个查详情,稳定有结果)。
     */
    private suspend fun collectDaily(showNsfw: Boolean): List<GameItem> {
        val picked = mutableListOf<GameItem>()
        val seen = mutableSetOf<Long>()
        var rounds = 0
        while (picked.size < 8 && rounds < 4) {
            rounds++
            val batch = YmgalRepository.random(8).filter { seen.add(it.gameId) }
            picked += if (showNsfw) batch else filterSafe(batch)
        }
        if (picked.size < 8) {
            try {
                val fill = YmgalRepository.latest(45)
                    .filter { seen.add(it.gameId) }
                    .let { if (showNsfw) it else it.filterNot { it.restricted } }
                    .shuffled()
                    .take(8 - picked.size)
                picked += fill
            } catch (e: Exception) {
                // 补齐失败就算了,有多少展示多少
            }
        }
        return picked
    }

    /** 随机接口不返回限制级标记:关闭 NSFW 时逐个查详情,过滤限制级(查失败的一并排除,保证不越界) */
    private suspend fun filterSafe(items: List<GameItem>): List<GameItem> = coroutineScope {
        items.map { item ->
            async { item to runCatching { YmgalRepository.detail(item.gameId) } }
        }.awaitAll()
            .filter { (_, r) -> r.getOrNull()?.restricted == false }
            .map { (item, _) -> item }
    }

    /** 猜你喜欢:取收藏/最近浏览的游戏,反查制作会社,推荐同会社其他作品 */
    private suspend fun loadForYou() {
        forYou = try {
            val seedGids = (
                FavoriteStore.favorites.value.take(3).map { it.gid } +
                    HistoryStore.history.value.take(3).map { it.gid }
                ).distinct().take(3)
            val orgs = seedGids.mapNotNull { orgOf(it) }.distinct().take(2)
            val seen = (
                FavoriteStore.favorites.value.map { it.gid } +
                    HistoryStore.history.value.map { it.gid }
                ).toSet()
            orgs.flatMap { org -> YmgalRepository.search(org, 1).result.orEmpty() }
                .distinctBy { it.gameId }
                .filter { it.gameId !in seen }
                .take(10)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 用游戏名搜索该游戏条目,拿到制作会社(没有/是"默认机构"时返回 null) */
    private suspend fun orgOf(gid: Long): String? = try {
        val name = FavoriteStore.favorites.value.find { it.gid == gid }?.name
            ?: HistoryStore.history.value.find { it.gid == gid }?.name
            ?: return null
        YmgalRepository.search(name, 1).result.orEmpty()
            .firstOrNull { it.gameId == gid }?.orgName
            ?.takeUnless { it == "默认机构" }
    } catch (e: Exception) {
        null
    }
}
