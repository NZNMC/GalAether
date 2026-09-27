package com.galstruo.app.data.shinnku

import java.net.URLEncoder

/** 版本类型:对应真红小站的目录结构 */
enum class VersionType(val label: String) {
    APK("安卓直装"),
    KRKR("KRKR版"),
    ONS("ONS版"),
    PC("PC版"),
    COLLECTION("合集"),
    TOOLS("工具"),
    OTHER("其他"),
}

/** 真红小站的一个资源文件(来自搜索) */
data class ShinnkuFile(
    val filePath: String,
    val uploadTimestamp: Long = 0,
    val fileSize: Long = 0,
) {
    val fileName: String get() = filePath.substringAfterLast('/')

    val type: VersionType
        get() = when {
            filePath.startsWith("0/apk") || fileName.endsWith(".apk", true) -> VersionType.APK
            filePath.startsWith("0/krkr") -> VersionType.KRKR
            filePath.startsWith("0/ons") -> VersionType.ONS
            filePath.startsWith("0/win") || filePath.startsWith("zd") -> VersionType.PC
            // 搜索结果里合集路径可能带也可能不带"合集系列/"前缀
            filePath.startsWith("合集系列") || filePath.startsWith("浮士德galgame游戏合集") ->
                VersionType.COLLECTION
            filePath.startsWith("0/tools") -> VersionType.TOOLS
            else -> VersionType.OTHER
        }

    /** 生成下载直链 */
    fun downloadUrl(): String {
        return if (type == VersionType.COLLECTION) {
            // 合集系列走站内跳转接口(服务端把 galgame0 映射到存储路径)
            var rel = filePath
            if (rel.startsWith("合集系列/")) rel = rel.removePrefix("合集系列/")
            if (rel.startsWith("浮士德galgame游戏合集/")) rel = rel.removePrefix("浮士德galgame游戏合集/")
            "https://www.shinnku.com/api/r2/download-url/galgame0/${encodeSegments(rel)}"
        } else {
            // 普通文件走 zd 直链域名,支持断点续传
            "https://zd.shinnku.top/file/shinnku/${encodeSegments(filePath)}"
        }
    }

    private fun encodeSegments(path: String): String =
        path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}
