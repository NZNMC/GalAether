# GalAether

安卓 Galgame 资源商店 App(个人自用项目)。

## 功能

- 游戏信息浏览:搜索、分类浏览(按发行月份)、首页最新发行/随机推荐
- 多版本下载:安卓直装(APK)/ KRKR / ONS / PC,断点续传、前台服务、下载管理(重试/删除)
- 智能归档:APK 一键安装;KRKR/ONS 压缩包自动解压到模拟器目录;其他存到下载目录
- 网盘资源:显示百度网盘/夸克等链接,一键复制或唤起网盘 App
- 收藏、浏览历史、搜索历史(全部本地保存)
- Material You 风格:动态取色、深色模式、自定义主题色;NSFW 内容开关(默认关闭)

## 数据来源

| 数据 | 来源 | 方式 |
|---|---|---|
| 游戏信息(名称/封面/简介/标签) | 月幕Galgame | 官方 API(OAuth2 公开凭据) |
| 直链下载 | 真红小站 shinnku.com | 网页解析,免登录 |
| 网盘资源链接 | 鲲galgame kungal.com | 网页解析,链接复制/唤起 |
| 直链下载(扩展) | TouchGAL | 官方开发者 API(申请中) |

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

## 声明

本项目为个人学习自用,不上架应用商店。游戏资源版权归原作者所有,请支持正版。
