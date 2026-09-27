package com.galstruo.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 收藏的一部游戏(存名称/封面快照,收藏页离线也能显示) */
data class FavoriteGame(
    val gid: Long,
    val name: String,
    val coverUrl: String? = null,
    val releaseDate: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
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
}
