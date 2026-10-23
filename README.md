<div align="center">

<img src="assets/icon/preview_masked.png" width="128" alt="FuckDQD 图标" />

# FuckDQD

**去掉懂球帝里的广告和博彩推单内容的 LSPosed 模块**

[![License](https://img.shields.io/github/license/KotoriInSky/FuckDQD)](LICENSE)
[![Release](https://img.shields.io/github/v/release/KotoriInSky/FuckDQD)](https://github.com/KotoriInSky/FuckDQD/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/KotoriInSky/FuckDQD/total)](https://github.com/KotoriInSky/FuckDQD/releases)
![libxposed API](https://img.shields.io/badge/libxposed%20API-102-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)

</div>

## 简介

FuckDQD 是一个基于 [libxposed API 102](https://github.com/libxposed/api) 的 LSPosed 模块，作用域只有懂球帝（`com.dongqiudi.news`）。它在懂球帝自己的广告调度层直接回「没有广告」，后面的穿山甲、优量汇、快手、百度等广告 SDK 都不会被调起；同时把预测、专家、情报这类博彩推单入口换掉或藏掉。

所有功能都能在模块界面里单独开关。

## 功能

### 广告

| 开关 | 去掉的内容 |
| --- | --- |
| 开屏广告 | 冷启动、热启动开屏，以及缓存的开屏素材 |
| 信息流 / 列表广告 | 新闻流、视频、圈子、赛事、彩票等列表里的广告 |
| 弹窗广告 | 首页插屏弹窗、网页里触发的弹窗广告 |
| 横幅 / 详情页广告 | 赛事页、直播视频页的广告位，文章转发区下方的广告（连预留的空白一起收起） |

### 博彩 / 推单

| 开关 | 改动 |
| --- | --- |
| 推单 / 博彩 | 底部「预测」换成「圈子」（直接进首页的圈子频道）；比赛页去掉「专业版」；比赛列表去掉「竞北」 |
| 比赛情报 / 专家 / 前瞻 | 比赛详情页去掉这三个推单导流标签 |

### 其他

- 模块状态：框架版本、作用域是否包含懂球帝、懂球帝版本
- 在桌面隐藏图标（之后可以从 LSPosed 管理器打开）
- 深色模式：跟随系统 / 浅色 / 深色
- 一键重启懂球帝（需要 root）

## 环境要求

- Android 8.0 及以上
- 支持 libxposed API 102 的 LSPosed（开发时用的是 LSPosed 2.2.1）
- 懂球帝：在 **8.7.2（10441）** 上完整测试；其他版本会自动查找 hook 点，见[版本适配](#版本适配)

## 安装

1. 到 [Releases](https://github.com/KotoriInSky/FuckDQD/releases/latest) 下载最新的 APK 并安装。
2. 在 LSPosed 管理器里启用 FuckDQD，作用域勾选「懂球帝」。
3. 强制停止懂球帝后重新打开（模块界面里也可以一键重启）。

打开模块界面，「状态」里框架和作用域都正常就说明生效了。开关改动在懂球帝下次启动时生效。

## 版本适配

懂球帝的代码经过 R8 混淆，换版本后部分类名、方法名会变。模块用 [DexKit](https://github.com/LuckyPray/DexKit) 按不容易变的特征（接口地址、字符串常量、参数类型、调用关系）重新查找这些 hook 点：

- 每个懂球帝版本只在第一次启动时查找一次（约 200ms），结果缓存在懂球帝私有目录，之后直接读缓存。
- 在 8.7.2 上，查找结果和人工逆向的结果逐项比对，15 项全部一致。
- 某个 hook 点在新版本上找不到时，只是那一项不生效，不影响其他功能。

## 常见问题

**广告没去掉 / 想看模块到底装上了什么？**

懂球帝加了爱加密壳，壳会让模块在它解密完成之后打的 logcat 都看不到，所以日志写在懂球帝私有目录里：

```bash
adb shell su -c "cat /data/data/com.dongqiudi.news/files/fuckdqd.log"
```

正常的话能看到类似 `installed: splash=4, feed=3, popup=3, banner=3, betting=10, matchIntel=2` 的一行，以及每次拦截的 `blocked ...`。如果某一组后面带了 `missing=...`，说明那几个 hook 点在你的懂球帝版本上没找到，欢迎提 Issue 并附上这份日志和懂球帝版本号。

**更新懂球帝之后某些功能失效了？**

先强制停止懂球帝再打开一次，让模块重新查找 hook 点。如果日志里还是有 `missing`，请提 Issue。

**为什么从 1.0.0 之前的测试版升级要先卸载？**

正式版换了包名（`io.github.kotoriinsky.fuckdqd`）和签名，和测试版不是同一个应用。卸载旧版后安装新版，并在 LSPosed 里重新启用。

## 自己编译

需要 JDK 21 和 Android SDK（compileSdk 37）。

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/`。根目录放一个 `keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`）就用你自己的密钥签名，没有的话用 debug 签名。

### 项目结构

| 模块 | 内容 |
| --- | --- |
| `:hook` | 注入懂球帝的代码：入口 `HookEntry`、广告 `AdHooks`、博彩推单 `BettingHooks`、DexKit 查找 `HookTargets` |
| `:ui` | 模块界面（Jetpack Compose + miuix） |
| `:app` | 打包壳，`META-INF/xposed` 模块声明 |

## 致谢

- [LSPosed](https://github.com/LSPosed/LSPosed) / [libxposed](https://github.com/libxposed)：模块框架与 API
- [DexKit](https://github.com/LuckyPray/DexKit)：混淆代码的特征查找
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix)：界面组件

## 免责声明

本项目只用于学习和研究 Android 应用的运行机制，与懂球帝官方无关。使用本模块造成的任何后果（包括但不限于账号异常）由使用者自行承担。请勿将本项目用于商业用途。

## 开源协议

[GPL-3.0](LICENSE)
