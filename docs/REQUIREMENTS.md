# FuckDQD 需求

## 项目定位
- LSPosed 模块，去除懂球帝（`com.dongqiudi.news`，调研版本 8.7.2 / 10441）里的广告。
- 逆向资料位于 `research/`（base.apk、dex、jadx 源码）。

## 图标
- 扁平风格自适应图标：绿色底、白色足球、右下角红色“禁止”角标。
- 源文件：`assets/icon/icon.svg`；Android 资源：`assets/icon/android/res/`（前景、背景色、单色主题图标）。

## 技术栈（参照本机 fp_verify 项目）
- AGP 9.3.1 / Kotlin 2.4.10 / Gradle 9.7.0，Java 21。
- Xposed：libxposed API 102（`compileOnly`），模块 App 里用 libxposed service 写远程配置（RemotePreferences）。
- UI：Jetpack Compose + miuix（`miuix-ui`、`miuix-preference` 0.9.4）。
- 模块划分：`:hook`（注入代码，不能依赖 Compose）、`:ui`（设置界面）、`:app`（打包壳 + `META-INF/xposed`）。
- 作用域：`com.dongqiudi.news`。

## 界面（miuix）
- 首页：模块激活状态（框架名称/版本、作用域是否包含懂球帝）、目标 App 版本。
- 广告开关（按 hook 点逐项开关）：
  - 开屏广告
  - 信息流 / 列表广告
  - 弹窗广告
  - 横幅 / 详情页广告（含文章转发区上方广告）
- 博彩 / 推单开关（详见 `docs/betting-removal.md`）：
  - 推单 / 博彩：底部「预测」→「圈子」、隐藏比赛「专业版」
  - 比赛情报 / 专家 / 前瞻：比赛详情页屏蔽这三个标签
- 设置：
  - 在桌面隐藏图标（禁用 launcher alias 组件；隐藏后可从 LSPosed 管理器打开）
  - 深色模式：跟随系统 / 浅色 / 深色
  - 改动后重启懂球帝（强制停止目标进程）
- 关于：版本号、构建时间、开源地址。

## 待办
- ~~注入时机~~（2026-10-23 已解决）：在 `onModuleLoaded` 里挂 `LoadedApk.createOrUpdateClassLoaderLocked` 等外层帧，返回时装 hook。壳在 `N.al` 之后会吞掉模块的 logcat，日志看 `/data/data/com.dongqiudi.news/files/fuckdqd.log`。
- 博彩 / 推单内容屏蔽：见 `docs/betting-removal.md`（已记录定位线索，待实现；注入前提已满足）。
- 圈子底部图标：按 `design/icon/circle_tab_ref.png` 绘制。
