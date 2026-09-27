package com.galstruo.app

import android.app.Application
import android.content.Context
import android.net.Uri
import com.galstruo.app.data.SettingsStore
import com.galstruo.app.data.download.DownloadDir
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.data.local.SearchHistoryStore
import com.galstruo.app.data.kungal.KungalAuth
import com.galstruo.app.data.network.NetConfig
import com.galstruo.app.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 应用入口:为无界面组件(下载服务等)提供全局 Context */
class GalAetherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // 恢复本地数据:下载记录、收藏、浏览历史、搜索历史
        DownloadManager.loadRecords()
        FavoriteStore.load()
        HistoryStore.load()
        SearchHistoryStore.load()
        // 恢复网络代理与自选下载目录(读取本地设置,不影响启动速度)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val s = SettingsStore(this@GalAetherApp).uiSettings.first()
                NetConfig.update(s.proxyEnabled, s.proxyHost, s.proxyPort)
                if (s.downloadUri.isNotBlank()) {
                    DownloadDir.update(Uri.parse(s.downloadUri))
                }
            }
        }
        // 云同步:自动监听数据变化上传,启动时与云端对账一次
        SyncManager.init(SettingsStore(this))
        // 鲲galgame 登录状态:恢复上次登录的用户信息(官网接口退役后的兜底)
        KungalAuth.init(this)
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
