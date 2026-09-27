package com.galstruo.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 深色模式:跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 用户界面设置(存于本地,重启不丢失) */
data class UiSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,   // 跟随壁纸动态取色
    val seedHue: Int = -1,              // 自定义主色色相 0~359,-1 = 未自定义
    val showNsfw: Boolean = false,      // 显示限制级(NSFW)内容,默认关闭
    val proxyEnabled: Boolean = false,  // 网络代理开关
    val proxyHost: String = "",         // 代理地址(不含端口)
    val proxyPort: Int = 7890,          // 代理端口
    val downloadUri: String = "",       // 自选下载目录(系统文件夹选择器返回的 uri 字符串)
)

class SettingsStore(private val context: Context) {

    val uiSettings: Flow<UiSettings> = context.dataStore.data.map { p ->
        UiSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[KEY_THEME_MODE] ?: "") }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
            seedHue = p[KEY_SEED_HUE] ?: -1,
            showNsfw = p[KEY_SHOW_NSFW] ?: false,
            proxyEnabled = p[KEY_PROXY_ENABLED] ?: false,
            proxyHost = p[KEY_PROXY_HOST] ?: "",
            proxyPort = p[KEY_PROXY_PORT] ?: 7890,
            downloadUri = p[KEY_DOWNLOAD_URI] ?: "",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(on: Boolean) {
        context.dataStore.edit {
            it[KEY_DYNAMIC_COLOR] = on
            if (on) it[KEY_SEED_HUE] = -1   // 打开跟随壁纸时清掉自定义主色
        }
    }

    /** 从色轮选定主色后,自动关闭"跟随壁纸" */
    suspend fun setSeedHue(hue: Int) {
        context.dataStore.edit {
            it[KEY_SEED_HUE] = hue
            it[KEY_DYNAMIC_COLOR] = false
        }
    }

    suspend fun setShowNsfw(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_NSFW] = on }
    }

    suspend fun setProxy(enabled: Boolean, host: String, port: Int) {
        context.dataStore.edit {
            it[KEY_PROXY_ENABLED] = enabled
            it[KEY_PROXY_HOST] = host
            it[KEY_PROXY_PORT] = port
        }
    }

    suspend fun setDownloadUri(uri: String) {
        context.dataStore.edit { it[KEY_DOWNLOAD_URI] = uri }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_SEED_HUE = intPreferencesKey("seed_hue")
        val KEY_SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        val KEY_PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val KEY_PROXY_HOST = stringPreferencesKey("proxy_host")
        val KEY_PROXY_PORT = intPreferencesKey("proxy_port")
        val KEY_DOWNLOAD_URI = stringPreferencesKey("download_uri")
    }
}
