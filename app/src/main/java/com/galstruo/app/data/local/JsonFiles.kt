package com.galstruo.app.data.local

import com.galstruo.app.GalAetherApp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 本地小数据(收藏/历史等)的 JSON 文件读写工具,和下载记录同一套方案 */
internal val gson = Gson()

internal inline fun <reified T> loadList(fileName: String): List<T> {
    val f = File(GalAetherApp.appContext.filesDir, fileName)
    if (!f.exists()) return emptyList()
    return try {
        gson.fromJson<List<T>>(f.readText(), object : TypeToken<List<T>>() {}.type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun <T> saveList(fileName: String, list: List<T>) {
    try {
        File(GalAetherApp.appContext.filesDir, fileName).writeText(gson.toJson(list))
    } catch (_: Exception) {
    }
}
