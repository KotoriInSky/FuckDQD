# 博彩 / 推单内容屏蔽（需求记录）

记录时间：2026-10-23。调研版本：懂球帝 8.7.2（10441）。
需求与定位线索。**已实现**，见 `BettingHooks.kt`；实现时和这里记录的有出入的地方以代码注释为准（比如「预测」改成直接跳首页圈子频道）。

参考图（第三方页面截图，只在本地 research/ref/，不进仓库）：
- `research/ref/match-detail-nba.jpg` —— NBA 比赛页，标签栏：评分 / 赛况 / 阵容 / **情报** / 分析 / **专家**
- `research/ref/match-detail-cba-preview.jpg` —— CBA 比赛页，标签栏含 **前瞻**（专家陪聊、关注解锁前瞻）
- `research/ref/match-detail-expert-betting.jpg` —— **专家** 页：猜胜负、让球、至少进 X 球、红钻解锁方案、预言家等，纯博彩导流
- `research/ref/circle_tab_ref.png` —— 圈子底部图标参考（说话气泡 + “…”，文字“圈子”）

---

## 1. 具体比赛页：屏蔽「情报 / 专家 / 前瞻」三个标签

- **屏蔽**：情报、专家、前瞻（均为博彩/推单导流：猜胜负、让球盘、专家方案、红钻解锁、关注解锁前瞻等）。
- **保留**：评分、赛况、阵容、分析。
- 标签是**服务端下发**的，不同比赛类型标签不一样（NBA 有情报/专家，CBA 有前瞻，足球有情报/专家），所以不能写死索引。
- 定位线索：
  - 比赛详情页 = `com.dongqiudi.match.TournamentDetailActivity`
  - 标签数据模型 = `com.dongqiudi.news.model.MatchTabsModel`，其 `getList()` 返回 `List<TabsModel>`
  - `com.dongqiudi.news.model.TabsModel` 字段：`tab` / `title` / `type` / `url`
- 实现方向：hook `MatchTabsModel.getList()`（或 TournamentDetailActivity 消费该 list 处），过滤掉要屏蔽的标签后返回新 list。
- 待确认：用 `title`（情报/专家/前瞻，随语言变）还是 `type`/`tab` 的英文 key 过滤。优先用 key，更稳；实现时先打日志把这三个标签的 `type`/`tab` 值抓出来。
- 相关字符串：`match_intelligence=情报`、`top_toolbar_small_guss=情报`、`top_toolbar_small_plan=专家`、`lib_lottery_main_tab2_name=情报`。

## 2. 底部「预测」入口 → 换成「圈子」

- 底部导航第 4 格（view id `R.id.thread`，tag=3）现为「预测」（彩票/推单），改成「圈子」。
- **图标**：需自己画，样式见 `research/ref/circle_tab_ref.png`（圆角说话气泡 + 居中“…”），文字“圈子”。
- **点击跳转**：改为打开圈子，不再进彩票页。圈子线索：
  - `com.dongqiudi.lottery.fragment.LotteryGroupFragment`（scheme `main/group/index`）
  - `com.dongqiudi.lottery.activity.LotteryGroupActivity`
  - `com.dongqiudi.group.NewGroupListActivity`（标题“圈子”）
- 定位线索：
  - `com.dongqiudi.news.view.MainBottomView.onFinishInflate()` —— 第 4 格 `mLotteryView` 的 `setup(icon=2131233305/0x7f080a19, color, text=2131823793/0x7f110cb1「预测」)`；改图标和文字在这里。
  - `com.dongqiudi.news.MainActivity.onLotteryClicked()` —— 点第 4 格后的跳转，改成进圈子。

## 3. 比赛 tab 的「专业版」隐藏

- 比赛 tab 顶部「球迷版 / 专业版」切换，隐藏「专业版」（专业版整页是赔率/推单/情报）。
- 定位线索：
  - `com.dongqiudi.match.fragment.MainTabMatchFragment`：构造里 `mTabs = A1()==1 ? {"比赛"} : {"球迷版","专业版"}`。
  - `com.dongqiudi.match.adapter.i`（FragmentStateAdapter）：`A1()!=1` 时才 `add(MatchBaseFragment)`（专业版页）。
  - `A1()` = `com.dongqiudi.news.util.f.A1()`，是青少年模式状态（0/1），被很多地方用（API 参数、信息流等），**不能全局强改成 1**。只针对这两个点改：让 `mTabs` 只留「比赛/球迷版」、adapter 不加 MatchBaseFragment。

---

## 开关划分（设置页）

- **推单 / 博彩开关**：涵盖 §2（预测→圈子）+ §3（隐藏专业版）。
- **比赛情报 / 专家 / 前瞻开关**：涵盖 §1（比赛详情页三个博彩标签）。
- 两个开关都默认开（装模块即去）。

## 依赖

以上全部是对懂球帝真实类的 hook，走与广告 hook 相同的 `install()` 注入链路。该链路已于 2026-10-23 确认稳定生效（`installed:` 见 `files/fuckdqd.log`），可以开始实现。
