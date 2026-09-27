package com.galstruo.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 收藏状态:0=未分类 1=想玩 2=在玩 3=已通关 */
object FavoriteStatus {
    const val NONE = 0
    const val WANT = 1
    const val PLAYING = 2
    const val DONE = 3

    fun label(status: Int): String = when (status) {
        WANT -> "想玩"
        PLAYING -> "在玩"
        DONE -> "已通关"
        else -> "未分类"
    }
}

/** 收藏的一部游戏(存名称/封面快照,收藏页离线也能显示;状态与备注可自行整理) */
data class FavoriteGame(
    val gid: Long,
    val name: String,
    val coverUrl: String? = null,
    val releaseDate: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val status: Int = FavoriteStatus.NONE,
    val note: String = "",
)

/** 收藏夹:纯本地保存,最新收藏排最前 */
object FavoriteStore {

    private const val FILE = "favorites.json"

    private val _favorites = MutableStateFlow<List<FavoriteGame>>(emptyList())
    val favorites: StateFlow<List<FavoriteGame>> = _favorites

    /** 应用启动时从本地文件恢复 */
    fun load() {
        _favorites.value = loadList(FILE)
    }

    fun isFavorite(gid: Long): Boolean = _favorites.value.any { it.gid == gid }

    /** 收藏/取消收藏,返回操作后是否处于收藏状态 */
    fun toggle(game: FavoriteGame): Boolean {
        val exists = _favorites.value.any { it.gid == game.gid }
        val next = if (exists) {
            _favorites.value.filterNot { it.gid == game.gid }
        } else {
            listOf(game) + _favorites.value
        }
        _favorites.value = next
        saveList(FILE, next)
        return !exists
    }

    fun remove(gid: Long) {
        _favorites.value = _favorites.value.filterNot { it.gid == gid }
        saveList(FILE, _favorites.value)
    }

    /** 更新一部收藏的状态/备注 */
    fun update(gid: Long, status: Int, note: String) {
        _favorites.value = _favorites.value.map {
            if (it.gid == gid) it.copy(status = status, note = note) else it
        }
        saveList(FILE, _favorites.value)
    }

    /** 整体替换(导入备份时用) */
    fun replaceAll(list: List<FavoriteGame>) {
        _favorites.value = list
        saveList(FILE, list)
    }
}
