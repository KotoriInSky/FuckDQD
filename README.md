<div align="center">

<img src="assets/icon/logo.png" width="160" alt="FuckDQD" />

# FuckDQD

**去除懂球帝广告与推单内容的 LSPosed 模块**

[![License](https://img.shields.io/github/license/KotoriInSky/FuckDQD)](LICENSE)
[![Release](https://img.shields.io/github/v/release/KotoriInSky/FuckDQD)](https://github.com/KotoriInSky/FuckDQD/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/KotoriInSky/FuckDQD/total)](https://github.com/KotoriInSky/FuckDQD/releases)
![libxposed API](https://img.shields.io/badge/libxposed%20API-102-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)

</div>

## 简介

FuckDQD 是一款 LSPosed 模块，用于去除懂球帝中的广告与推单内容，各项功能均可在模块界面中独立开关。

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
- 懂球帝 **8.7.2** 及以上，见[版本适配](#版本适配)

## 安装

1. 到 [Releases](https://github.com/KotoriInSky/FuckDQD/releases/latest) 下载最新的 APK 并安装。
2. 在 LSPosed 管理器里启用 FuckDQD，作用域勾选「懂球帝」。
3. 强制停止懂球帝后重新打开（模块界面里也可以一键重启）。

打开模块界面，「状态」里框架和作用域都正常就说明生效了。开关改动在懂球帝下次启动时生效。

## 版本适配

模块在懂球帝 8.7.2 上完整测试。懂球帝更新后，模块会在首次启动时自动重新定位所需的 hook 点，一般不需要等模块更新。

## 常见问题

**模块没有生效？**

确认 LSPosed 中已启用 FuckDQD 并勾选了懂球帝作用域，然后强制停止懂球帝再重新打开。

**懂球帝更新后部分功能失效？**

欢迎[提交 Issue](https://github.com/KotoriInSky/FuckDQD/issues)，并注明懂球帝的版本号。

## 构建

需要 JDK 21：

```bash
./gradlew :app:assembleRelease
```

## 致谢

- [LSPosed](https://github.com/LSPosed/LSPosed) / [libxposed](https://github.com/libxposed)：模块框架与 API
- [DexKit](https://github.com/LuckyPray/DexKit)：混淆代码的特征查找
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix)：界面组件

## 免责声明

本项目只用于学习和研究 Android 应用的运行机制，与懂球帝官方无关。使用本模块造成的任何后果（包括但不限于账号异常）由使用者自行承担。请勿将本项目用于商业用途。

## 开源协议

[GPL-3.0](LICENSE)
