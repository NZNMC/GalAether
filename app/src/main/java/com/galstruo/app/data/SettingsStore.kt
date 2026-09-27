package com.galstruo.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 深色模式:跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 字体大小:小 / 标准 / 大 */
enum class FontSize { SMALL, NORMAL, LARGE }

/** 列表样式:大卡片(默认)/ 紧凑列表 */
enum class ListStyle { CARD, COMPACT }

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
    val onboardingDone: Boolean = false, // 新手引导是否已完成(首次启动展示一次)
    val fontSize: FontSize = FontSize.NORMAL,  // 字体大小
    val listStyle: ListStyle = ListStyle.CARD, // 列表样式
    val animationsEnabled: Boolean = true,     // 界面动画开关
    val showHomeDaily: Boolean = true,   // 首页区块:今日推荐
    val showHomeHot: Boolean = true,     // 首页区块:热门经典
    val showHomeForYou: Boolean = true,  // 首页区块:猜你喜欢
    val showHomeLatest: Boolean = true,  // 首页区块:最新发行
    val showHomeRecent: Boolean = true,  // 首页区块:最近浏览
    val showJapaneseNames: Boolean = false, // 列表显示日文原名
    val skippedVersion: String = "",     // 已选择跳过的更新版本(不重复提示)
    // 云同步(GitHub 设备码授权 + WebDAV)
    val githubToken: String = "",        // GitHub OAuth 令牌(只存本机,不上云)
    val githubUser: String = "",         // GitHub 用户名(登录后显示)
    val githubAvatar: String = "",       // GitHub 头像地址(「我的」页显示)
    val githubClientId: String = "",     // 登录时用的 Client ID(OAuth App 更换后用于自动退出旧登录)
    val gistId: String = "",             // 同步用的 Gist ID(首次同步自动创建)
    val githubSyncEnabled: Boolean = false, // GitHub 自动同步开关
    val webdavEnabled: Boolean = false,  // WebDAV 自动同步开关
    val webdavUrl: String = "",          // WebDAV 服务器地址(如 https://dav.jianguoyun.com/dav/)
    val webdavUser: String = "",         // WebDAV 账号
    val webdavPassword: String = "",     // WebDAV 密码(只存本机,不上云)
    val lastSyncAt: Long = 0,            // 上次与云端同步的时间戳
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
            onboardingDone = p[KEY_ONBOARDING_DONE] ?: false,
            fontSize = runCatching { FontSize.valueOf(p[KEY_FONT_SIZE] ?: "") }
                .getOrDefault(FontSize.NORMAL),
            listStyle = runCatching { ListStyle.valueOf(p[KEY_LIST_STYLE] ?: "") }
                .getOrDefault(ListStyle.CARD),
            animationsEnabled = p[KEY_ANIMATIONS_ENABLED] ?: true,
            showHomeDaily = p[KEY_SHOW_HOME_DAILY] ?: true,
            showHomeHot = p[KEY_SHOW_HOME_HOT] ?: true,
            showHomeForYou = p[KEY_SHOW_HOME_FORYOU] ?: true,
            showHomeLatest = p[KEY_SHOW_HOME_LATEST] ?: true,
            showHomeRecent = p[KEY_SHOW_HOME_RECENT] ?: true,
            showJapaneseNames = p[KEY_SHOW_JP_NAMES] ?: false,
            skippedVersion = p[KEY_SKIPPED_VERSION] ?: "",
            githubToken = p[KEY_GH_TOKEN] ?: "",
            githubUser = p[KEY_GH_USER] ?: "",
            githubAvatar = p[KEY_GH_AVATAR] ?: "",
            githubClientId = p[KEY_GH_CLIENT_ID] ?: "",
            gistId = p[KEY_GIST_ID] ?: "",
            githubSyncEnabled = p[KEY_GH_SYNC] ?: false,
            webdavEnabled = p[KEY_WEBDAV_ENABLED] ?: false,
            webdavUrl = p[KEY_WEBDAV_URL] ?: "",
            webdavUser = p[KEY_WEBDAV_USER] ?: "",
            webdavPassword = p[KEY_WEBDAV_PASSWORD] ?: "",
            lastSyncAt = p[KEY_LAST_SYNC_AT] ?: 0,
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

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setFontSize(size: FontSize) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = size.name }
    }

    suspend fun setListStyle(style: ListStyle) {
        context.dataStore.edit { it[KEY_LIST_STYLE] = style.name }
    }

    suspend fun setAnimationsEnabled(on: Boolean) {
        context.dataStore.edit { it[KEY_ANIMATIONS_ENABLED] = on }
    }

    suspend fun setShowHomeDaily(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_HOME_DAILY] = on }
    }

    suspend fun setShowHomeHot(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_HOME_HOT] = on }
    }

    suspend fun setShowHomeForYou(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_HOME_FORYOU] = on }
    }

    suspend fun setShowHomeLatest(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_HOME_LATEST] = on }
    }

    suspend fun setShowHomeRecent(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_HOME_RECENT] = on }
    }

    suspend fun setShowJapaneseNames(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_JP_NAMES] = on }
    }

    suspend fun setSkippedVersion(version: String) {
        context.dataStore.edit { it[KEY_SKIPPED_VERSION] = version }
    }

    // ---- 云同步相关 ----

    /** GitHub 登录成功后保存令牌、用户名与头像(登录后默认打开自动同步) */
    suspend fun setGithubAuth(token: String, user: String, avatar: String) {
        context.dataStore.edit {
            it[KEY_GH_TOKEN] = token
            it[KEY_GH_USER] = user
            it[KEY_GH_AVATAR] = avatar
            it[KEY_GH_CLIENT_ID] = com.galstruo.app.data.sync.GitHubSync.CLIENT_ID
            it[KEY_GH_SYNC] = true
        }
    }

    /** 退出 GitHub 登录:清掉令牌与用户信息(保留 gistId,重新登录后还能找到云端备份) */
    suspend fun clearGithubAuth() {
        context.dataStore.edit {
            it[KEY_GH_TOKEN] = ""
            it[KEY_GH_USER] = ""
            it[KEY_GH_AVATAR] = ""
            it[KEY_GH_SYNC] = false
        }
    }

    suspend fun setGithubSyncEnabled(on: Boolean) {
        context.dataStore.edit { it[KEY_GH_SYNC] = on }
    }

    /** 首次同步时记录 GitHub 返回的 Gist ID */
    suspend fun setGistId(id: String) {
        context.dataStore.edit { it[KEY_GIST_ID] = id }
    }

    /** 保存 WebDAV 配置(开关 + 地址 + 账号 + 密码) */
    suspend fun setWebdav(enabled: Boolean, url: String, user: String, password: String) {
        context.dataStore.edit {
            it[KEY_WEBDAV_ENABLED] = enabled
            it[KEY_WEBDAV_URL] = url
            it[KEY_WEBDAV_USER] = user
            it[KEY_WEBDAV_PASSWORD] = password
        }
    }

    suspend fun setWebdavEnabled(on: Boolean) {
        context.dataStore.edit { it[KEY_WEBDAV_ENABLED] = on }
    }

    suspend fun setLastSyncAt(time: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC_AT] = time }
    }

    /**
     * 批量套用云端同步下来的设置(从云端恢复用)。
     * 写回全部键,并同步刷新网络代理与下载目录的运行时配置。
     */
    suspend fun applyAll(s: UiSettings) {
        context.dataStore.edit {
            it[KEY_THEME_MODE] = s.themeMode.name
            it[KEY_DYNAMIC_COLOR] = s.dynamicColor
            it[KEY_SEED_HUE] = s.seedHue
            it[KEY_SHOW_NSFW] = s.showNsfw
            it[KEY_PROXY_ENABLED] = s.proxyEnabled
            it[KEY_PROXY_HOST] = s.proxyHost
            it[KEY_PROXY_PORT] = s.proxyPort
            it[KEY_DOWNLOAD_URI] = s.downloadUri
            it[KEY_ONBOARDING_DONE] = s.onboardingDone
            it[KEY_FONT_SIZE] = s.fontSize.name
            it[KEY_LIST_STYLE] = s.listStyle.name
            it[KEY_ANIMATIONS_ENABLED] = s.animationsEnabled
            it[KEY_SHOW_HOME_DAILY] = s.showHomeDaily
            it[KEY_SHOW_HOME_HOT] = s.showHomeHot
            it[KEY_SHOW_HOME_FORYOU] = s.showHomeForYou
            it[KEY_SHOW_HOME_LATEST] = s.showHomeLatest
            it[KEY_SHOW_HOME_RECENT] = s.showHomeRecent
            it[KEY_SHOW_JP_NAMES] = s.showJapaneseNames
            it[KEY_SKIPPED_VERSION] = s.skippedVersion
            it[KEY_GH_TOKEN] = s.githubToken
            it[KEY_GH_USER] = s.githubUser
            it[KEY_GH_AVATAR] = s.githubAvatar
            it[KEY_GH_CLIENT_ID] = s.githubClientId
            it[KEY_GIST_ID] = s.gistId
            it[KEY_GH_SYNC] = s.githubSyncEnabled
            it[KEY_WEBDAV_ENABLED] = s.webdavEnabled
            it[KEY_WEBDAV_URL] = s.webdavUrl
            it[KEY_WEBDAV_USER] = s.webdavUser
            it[KEY_WEBDAV_PASSWORD] = s.webdavPassword
            it[KEY_LAST_SYNC_AT] = s.lastSyncAt
        }
        // 运行时配置立即生效(代理 / 下载目录)
        com.galstruo.app.data.network.NetConfig.update(s.proxyEnabled, s.proxyHost, s.proxyPort)
        com.galstruo.app.data.download.DownloadDir.update(
            s.downloadUri.takeIf { it.isNotBlank() }?.let(Uri::parse),
        )
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
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        val KEY_LIST_STYLE = stringPreferencesKey("list_style")
        val KEY_ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val KEY_SHOW_HOME_DAILY = booleanPreferencesKey("show_home_daily")
        val KEY_SHOW_HOME_HOT = booleanPreferencesKey("show_home_hot")
        val KEY_SHOW_HOME_FORYOU = booleanPreferencesKey("show_home_for_you")
        val KEY_SHOW_HOME_LATEST = booleanPreferencesKey("show_home_latest")
        val KEY_SHOW_HOME_RECENT = booleanPreferencesKey("show_home_recent")
        val KEY_SHOW_JP_NAMES = booleanPreferencesKey("show_japanese_names")
        val KEY_SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val KEY_GH_TOKEN = stringPreferencesKey("github_token")
        val KEY_GH_USER = stringPreferencesKey("github_user")
        val KEY_GH_AVATAR = stringPreferencesKey("github_avatar")
        val KEY_GH_CLIENT_ID = stringPreferencesKey("github_client_id")
        val KEY_GIST_ID = stringPreferencesKey("gist_id")
        val KEY_GH_SYNC = booleanPreferencesKey("github_sync_enabled")
        val KEY_WEBDAV_ENABLED = booleanPreferencesKey("webdav_enabled")
        val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        val KEY_WEBDAV_USER = stringPreferencesKey("webdav_user")
        val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }
}
