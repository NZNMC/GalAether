# GalAether

安卓 Galgame 资源商店 App(个人自用项目)。

> 新版本通过 [Releases](../../releases) 发布(含 APK),应用内「检查更新」即从此处获取。

## 功能

- 游戏信息浏览:搜索、分类浏览(按发行月份)、首页今日推荐/热门经典/最新发行
- 多版本下载:安卓直装(APK)/ KRKR / ONS / PC,断点续传、前台服务、下载管理(重试/删除)
- 智能归档:APK 一键安装;KRKR/ONS 压缩包自动解压到模拟器目录;其他存到下载目录
- 网盘资源:显示百度网盘/夸克等链接,一键复制或唤起网盘 App
- Galgame 模拟器:设置页在线下载安装 KRKR2、ONS、Tyranor
- 应用内检查更新(GitHub Releases + 国内镜像回退)、首次启动新手引导
- 收藏(分组+备注)、浏览历史、搜索历史(全部本地保存)
- 数据备份:导出/导入 JSON 备份文件
- 云同步(可选):GitHub 设备码登录自动备份到私有 Gist / 自备 WebDAV 网盘,新设备一键恢复
- Material You 风格:动态取色、深色模式、自定义主题色、HarmonyOS Sans 字体;NSFW 内容开关(默认关闭)
- 不注册账号、不收集个人信息、无广告

## 数据来源

| 数据 | 来源 | 方式 |
|---|---|---|
| 游戏信息(名称/封面/简介/标签) | 月幕Galgame | 官方 API(OAuth2 公开凭据) |
| 直链下载 | 真红小站 shinnku.com | 网页解析,免登录 |
| 网盘资源链接 | 鲲galgame kungal.com | 网页解析,链接复制/唤起 |

## 技术栈

- Kotlin + Jetpack Compose + Material 3(minSdk 26,compileSdk 34)
- 网络:OkHttp(下载支持 HTTP Range 断点续传)
- 本地存储:DataStore(设置)+ JSON 文件(下载记录/收藏/历史)
- 图片:Coil

## 构建

```bash
gradlew assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。

## 许可

[MIT License](LICENSE) · Copyright (c) 2026 NZNMC

## 声明

本项目为个人学习自用,不用于任何商业目的。应用本身不存储、不提供任何游戏资源文件,应用内展示的下载链接均为联网时实时检索自第三方公开站点的结果,相关资源版权归原作者/原版权方所有。请在下载后 24 小时内删除,支持正版,并通过官方渠道购买。使用本应用产生的任何后果由使用者自行承担。
