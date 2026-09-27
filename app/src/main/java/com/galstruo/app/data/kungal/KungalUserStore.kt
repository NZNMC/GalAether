package com.galstruo.app.data.kungal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.kungalUserStore by preferencesDataStore(name = "kungal_user")

/**
 * 鲲galgame 用户信息的本地暂存。
 * 官网 2026-09 改版后旧接口 /api/user 已退役,登录状态没法再靠接口确认,
 * 所以登录时从网页里读到的昵称/头像存这里,重启应用也能正常显示。
 */
class KungalUserStore(private val context: Context) {

    suspend fun load(): KungalUser? {
        val p = context.kungalUserStore.data.first()
        val name = p[KEY_NAME] ?: return null
        return KungalUser(
            id = p[KEY_UID] ?: 0L,
            name = name,
            avatar = p[KEY_AVATAR] ?: "",
        )
    }

    suspend fun save(user: KungalUser) {
        context.kungalUserStore.edit {
            it[KEY_UID] = user.id
            it[KEY_NAME] = user.name
            it[KEY_AVATAR] = user.avatar
        }
    }

    suspend fun clear() {
        context.kungalUserStore.edit { it.clear() }
    }

    private companion object {
        val KEY_UID = longPreferencesKey("uid")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_AVATAR = stringPreferencesKey("avatar")
    }
}
