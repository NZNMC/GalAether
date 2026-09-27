package com.galstruo.app

import android.app.Application
import android.content.Context
import com.galstruo.app.data.download.DownloadManager
import com.galstruo.app.data.local.FavoriteStore
import com.galstruo.app.data.local.HistoryStore
import com.galstruo.app.data.local.SearchHistoryStore

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
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
