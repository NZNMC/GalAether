package com.galstruo.app.data.emulator

import android.content.Context
import com.galstruo.app.GalAetherApp
import com.galstruo.app.data.network.FileDownloader
import java.io.File

/** 模拟器条目:安装包来自官方开源项目的 GitHub Releases,下载失败时自动换国内加速镜像 */
data class Emulator(
    val name: String,          // 显示名
    val desc: String,          // 一句话介绍
    val version: String,       // 当前提供的版本
    val apkUrl: String?,       // 应用内直链(GitHub Releases);为 null 时只能跳网页下载
    val pageUrl: String,       // 跳网页下载的地址(直链下载失败时兜底)
    val packageName: String?,  // 安装后按包名检测"已安装"状态
    val source: String,        // 来源说明
)

object EmulatorRepository {

    val list = listOf(
        Emulator(
            name = "KRKR2(吉里吉里2)",
            desc = "运行 KRKR 引擎游戏(游戏目录里有 data.xp3 的)",
            version = "v1.4.4",
            apkUrl = "https://github.com/2468785842/krkr2/releases/download/v1.4.4/krkr2-1.4.4-all.apk",
            pageUrl = "https://github.com/2468785842/krkr2/releases",
            packageName = "org.tvp.kirikiri2",
            source = "GitHub 开源项目 Kirikiroid2",
        ),
        Emulator(
            name = "ONS 模拟器(JH 版)",
            desc = "运行 ONS 引擎游戏(国内最常用的版本)",
            version = "v0.8.0",
            apkUrl = "https://github.com/jh10001/ONScripter-Jh/releases/download/v0.8.0/onscripter-jh-v0.8.0.universal.apk",
            pageUrl = "https://github.com/jh10001/ONScripter-Jh/releases",
            packageName = null,
            source = "GitHub 开源项目 ONScripter-Jh",
        ),
        Emulator(
            name = "Tyranor",
            desc = "一个模拟器通吃 KRKR / ONS / Artemis / RPG Maker 等引擎",
            version = "v2.3.4",
            apkUrl = null,  // 无官网直链,第三方站链接不稳定且防盗链,只能跳网页下载
            pageUrl = "https://www.gamedog.cn/down/39365.html",
            packageName = "com.akira.tyranoemu",
            source = "第三方下载站(无官网直链)",
        ),
    )

    /** 下载模拟器安装包到缓存目录,onProgress 回调下载百分比 */
    suspend fun download(emulator: Emulator, onProgress: (Int) -> Unit = {}): File {
        val url = emulator.apkUrl ?: throw IllegalStateException("该模拟器没有直链")
        val dir = File(GalAetherApp.appContext.cacheDir, "emulators").apply { mkdirs() }
        return FileDownloader.download(url, File(dir, url.substringAfterLast('/')), onProgress)
    }

    /** 是否已安装(按包名检测;没有包名的条目永远返回 false) */
    fun isInstalled(context: Context, emulator: Emulator): Boolean {
        val pkg = emulator.packageName ?: return false
        return runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
        }.isSuccess
    }
}
