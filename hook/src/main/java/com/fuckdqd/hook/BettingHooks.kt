package com.fuckdqd.hook

import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * 博彩 / 推单内容，按设置页的两个开关分组。需求见 docs/betting-removal.md（懂球帝 8.7.2 / 10441）。
 */
class BettingHooks(module: HookEntry, cl: ClassLoader) : HookGroup(module, cl) {

    /** 推单 / 博彩：底部第 4 格「预测」换成「圈子」，比赛 tab 去掉「专业版」。 */
    fun betting(): String = group {
        circleTab()
        hideProMatch()
        hideLotteryMatchTabs()
    }

    /** 比赛详情页去掉「情报 / 专家 / 前瞻」标签。 */
    fun matchIntel(): String = group {
        // 老比赛页：TournamentDetailActivity.initViewPager(List<TabsModel> tabs, String defaultPage)。
        // tabs 就是 activity 的 mTabList，原地删，两边保持一致
        method(TOURNAMENT_DETAIL, "initViewPager", LIST, STRING)?.let { m ->
            intercept(m) { chain ->
                val tabs = mutable(chain.getArg(0))
                var defaultPage = chain.getArg(1) as? String
                if (tabs != null && filterTabs(tabs)) {
                    if (tabs.none { tabKey(it) == defaultPage }) defaultPage = tabs.firstOrNull()?.let(::tabKey)
                }
                chain.proceed(arrayOf(tabs, defaultPage))
            }
        }
        // 新比赛页（com.grow.match_business）：MatchTabsModel.getList()，gson 反序列化出来的是 ArrayList，也原地删
        method(GROW_MATCH_TABS, "getList")?.let { m ->
            intercept(m) { chain ->
                chain.proceed().also { mutable(it)?.let(::filterTabs) }
            }
        }
    }

    // ---- 预测 → 圈子 --------------------------------------------------------

    private fun circleTab() {
        // MainBottomItemView.setup(...) 有 4 个重载，普通态 / 选中态 / 服务端下发的远程图标都走它们；
        // 每次跑完都把第 4 格重新盖成圈子图标，颜色取它刚设好的文字颜色，选中 / 未选中自然对得上
        val itemView = cl.loadClass(BOTTOM_ITEM_VIEW)
        val setups = itemView.declaredMethods.filter { it.name == "setup" }
        if (setups.isEmpty()) method(BOTTOM_ITEM_VIEW, "setup")
        for (m in setups) {
            intercept(m) { chain ->
                val result = chain.proceed()
                runCatching { (chain.thisObject as? View)?.let(::applyCircleItem) }
                    .onFailure { module.trace("circle item failed: $it") }
                result
            }
        }
        // MainActivity.onLotteryClicked()：原本切到 BaseLotteryPagerFragment（预测），改成跳到首页的「圈子」频道
        method(MAIN_ACTIVITY, "onLotteryClicked")?.let { m ->
            intercept(m) { chain ->
                runCatching { openHomeCircle(chain.thisObject!!) }
                    .onFailure { module.trace("open circle failed: $it") }
                null
            }
        }
        // 首页切频道都会走 BaseNewsFragment.setBottomTabStatus(position)：底栏高亮跟着当前频道走，
        // 在圈子频道就亮「圈子」，其他频道亮「首页」，从首页顶部点进圈子也一样
        method(BASE_NEWS_FRAGMENT, "setBottomTabStatus", INT)?.let { m ->
            intercept(m) { chain ->
                chain.proceed().also {
                    runCatching { syncBottomHighlight(chain.thisObject!!, chain.getArg(0) as Int) }
                        .onFailure { module.trace("sync bottom highlight failed: $it") }
                }
            }
        }
        // MainActivity.onNewsClicked(fromPush)：底栏点「首页」时首页已经在显示就什么都不做；
        // 如果这时停在圈子频道，就切回上次看的普通频道，免得停在圈子却亮着「首页」
        method(MAIN_ACTIVITY, "onNewsClicked", BOOLEAN)?.let { m ->
            intercept(m) { chain ->
                val activity = chain.thisObject!!
                val back = runCatching { leaveCircleTarget(activity, chain.getArg(0) as Boolean) }.getOrNull()
                if (back == null) return@intercept chain.proceed()
                setField(activity, "mNewsTabId", back)
                setField(activity, "mNewsSubTabType", null)
                chain.proceed(arrayOf(true))
            }
        }
    }

    private fun applyCircleItem(view: View) {
        val threadId = view.resources.getIdentifier("thread", "id", Keys.TARGET_PACKAGE)
        if (threadId == 0 || view.id != threadId) return
        val text = field(view, "mTextView") as? TextView ?: return
        text.text = "圈子"
        // 底栏原生图标：未选中是比文字深的线条（浅色模式 #2D2F32），选中是和文字同色的实心块（比赛那样）。
        // 选中与否看 setup 刚设好的文字颜色：带颜色（绿）的是选中，灰的是未选中
        val textColor = text.currentTextColor
        val hsv = FloatArray(3).also { Color.colorToHSV(textColor, it) }
        val selected = hsv[1] > 0.3f
        val drawable = when {
            selected -> CircleTabDrawable(textColor, filled = true)
            hsv[2] > 0.6f -> CircleTabDrawable(ICON_COLOR_DARK_THEME) // 深色模式：文字是浅灰
            else -> CircleTabDrawable(ICON_COLOR_LIGHT_THEME)
        }
        (field(view, "mImageView") as? ImageView)?.setImageDrawable(drawable)
    }

    /** 首页上次停留的非圈子频道 id，底栏点「首页」从圈子频道切回时用。 */
    private var lastHomeTabId: Int? = null

    /** 首页标签里 type = community 的那个频道 id（首页标签管理单例 news.manager.h0 的 i()）。 */
    private fun communityTabId(): Int? {
        val managerClass = cl.loadClass(HOME_TABS_MANAGER)
        val manager = managerClass.declaredFields
            .first { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == managerClass }
            .apply { isAccessible = true }.get(null)!!
        val tab = (call(manager, "i") as? List<*>)?.firstOrNull { runCatching { field(it!!, "type") }.getOrNull() == "community" }
        return tab?.let { call(it, "getId") as? Int }
    }

    /**
     * 跳到首页「圈子」频道：和推送打开首页某频道同一条路——设好 mNewsTabId，再 onNewsClicked(fromPush = true)，
     * 首页会 setPageTabId 切过去。两个入口打开的就是同一个页面，状态、刷新、红点都共用。
     * 首页没有圈子频道（服务端没下发）时退回懂球帝的独立圈子页。
     */
    private fun openHomeCircle(activity: Any) {
        val id = communityTabId()
        if (id == null) {
            module.trace("no community tab on home, open NewGroupListActivity")
            val act = activity as android.app.Activity
            act.startActivity(android.content.Intent().setClassName(act, NEW_GROUP_LIST_ACTIVITY))
            return
        }
        setField(activity, "mNewsTabId", id.toString())
        setField(activity, "mNewsSubTabType", null)
        call(activity, "onNewsClicked", true)
        module.trace("circle tab -> home channel $id")
    }

    private fun syncBottomHighlight(newsFragment: Any, position: Int) {
        val tabId = call(newsFragment, "getPageTabId", position) as? Int
        val isCircle = tabId != null && tabId == communityTabId()
        val activity = call(newsFragment, "getActivity")
        val index = activity?.let { runCatching { field(it, "index") }.getOrNull() }
        module.trace("home tab pos=$position id=$tabId circle=$isCircle activity=${activity?.javaClass?.simpleName} index=$index")
        if (tabId == null || tabId == -1) return
        if (!isCircle) lastHomeTabId = tabId
        if (activity?.javaClass?.name != MAIN_ACTIVITY || index != 0) return // 首页不在前台
        val bottom = field(activity, "mMainBottomView") ?: return
        call(bottom, "setSelectedItem", if (isCircle) BOTTOM_CIRCLE else BOTTOM_HOME)
    }

    /** 底栏点「首页」（fromPush = false）且首页正停在圈子频道时，返回要切回的频道 id；否则 null 照常处理。 */
    private fun leaveCircleTarget(activity: Any, fromPush: Boolean): String? {
        if (fromPush) return null
        val bottom = field(activity, "mMainBottomView") ?: return null
        if (call(bottom, "getCurrentPosition") != BOTTOM_HOME) return null // 不是点「首页」进来的
        val news = field(activity, "mFragment") ?: return null
        if (news.javaClass.name != BASE_NEWS_FRAGMENT && !cl.loadClass(BASE_NEWS_FRAGMENT).isInstance(news)) return null
        val current = call(news, "getMTabId") as? String
        if (current != communityTabId()?.toString()) return null
        return (lastHomeTabId ?: return null).toString()
    }

    // ---- 专业版 ------------------------------------------------------------

    private fun hideProMatch() {
        // MainTabMatchFragment()：mTabs = 青少年模式 ? {"比赛"} : {"球迷版", "专业版"}，改成只剩一项。
        // 青少年模式判断 f.A1() 别处也在用（接口参数、信息流），不能全局改，只改这里
        constructor(MAIN_TAB_MATCH)?.let { m ->
            intercept(m) { chain ->
                chain.proceed().also { setField(chain.thisObject!!, "mTabs", arrayOf("比赛")) }
            }
        }
        // MainTabMatchAdapter（match.adapter.i）构造里非青少年模式会再加一页 MatchBaseFragment（专业版），删掉
        cl.loadClass(MAIN_TAB_MATCH_ADAPTER).declaredConstructors.forEach { m ->
            intercept(m) { chain ->
                chain.proceed().also {
                    val pages = mutable(field(chain.thisObject!!, "mFragments"))
                    pages?.removeAll { it?.javaClass?.name == MATCH_PRO_FRAGMENT }
                }
            }
        }
    }

    /**
     * 比赛列表顶部的「关注 / 重要 / 全部 / 竞北 / 足球 …」来自全局缓存 com.dongqiudi.lib.a.b()（List<TabsDbModel>），
     * BaseTournamentFragment 建页时复制一份。在 getter 上按标题原地删掉彩票类标签（「竞北」）。
     */
    private fun hideLotteryMatchTabs() {
        method(MATCH_TAB_CACHE, "b")?.let { m ->
            intercept(m) { chain ->
                chain.proceed().also { result ->
                    // 万一缓存被换成不可变 list，removeAll 会抛异常，别让它冒到懂球帝里
                    runCatching {
                        val tabs = mutable(result) ?: return@also
                        val removed = tabs.filter { isLotteryMatchTab(it) }
                        if (removed.isNotEmpty() && tabs.removeAll(removed)) {
                            module.trace("removed match list tabs: ${removed.joinToString { lotteryTabLabel(it).orEmpty() }}")
                        }
                    }.onFailure { module.trace("filter match list tabs failed: $it") }
                }
            }
        }
    }

    // 不能按 type 删：实测「全部」的 type 也是 lottery
    private fun isLotteryMatchTab(tab: Any?): Boolean = lotteryTabLabel(tab)?.trim() in BLOCKED_MATCH_LIST_LABELS

    private fun lotteryTabLabel(tab: Any?): String? = tab?.let { runCatching { call(it, "getLabel") as? String }.getOrNull() }

    // ---- 比赛标签 ----------------------------------------------------------

    /** 删掉要屏蔽的标签，返回是否删过。同时把看到的标签打到日志，方便换版本时对 key。 */
    private fun filterTabs(tabs: MutableList<Any?>): Boolean = runCatching {
        val removed = tabs.filter { isBlockedTab(it) }
        if (removed.isNotEmpty() || seenTabs.add(tabs.joinToString { describeTab(it) })) {
            module.trace("match tabs: ${tabs.joinToString { describeTab(it) }}; removed ${removed.size}")
        }
        removed.isNotEmpty() && tabs.removeAll(removed)
    }.getOrElse {
        module.trace("filter match tabs failed: $it")
        false
    }

    private val seenTabs = HashSet<String>()

    @Suppress("UNCHECKED_CAST")
    private fun mutable(list: Any?): MutableList<Any?>? = list as? MutableList<Any?>

    private fun isBlockedTab(tab: Any?): Boolean {
        tab ?: return false
        val title = runCatching { call(tab, "getTitle") as? String }.getOrNull()?.trim()
        return title in BLOCKED_TITLES || tabKey(tab) in BLOCKED_TAB_KEYS
    }

    private fun tabKey(tab: Any?): String? = tab?.let { runCatching { call(it, "getTab") as? String }.getOrNull() }

    private fun describeTab(tab: Any?): String {
        tab ?: return "null"
        fun get(name: String) = runCatching { call(tab, name) as? String }.getOrNull()
        return "${get("getTitle")}(tab=${get("getTab")},type=${get("getType")})"
    }

    companion object {
        /** 服务端下发的标签标题。日志里会打出每个标签的 key，新出现的推单标签照着补。 */
        private val BLOCKED_TITLES = setOf("情报", "专家", "前瞻")

        /** 实测 key（2026-10-23，足球 / 篮球比赛页）：专家 = lottery，情报 = intelligence；前瞻还没抓到。 */
        private val BLOCKED_TAB_KEYS = setOf("lottery", "intelligence")

        /** 底栏未选中图标的颜色：浅色模式从「首页」图标取色，深色模式取近白。 */
        private const val ICON_COLOR_LIGHT_THEME = 0xFF2D2F32.toInt()
        private const val ICON_COLOR_DARK_THEME = 0xFFE6E6E6.toInt()

        /** 比赛列表顶部要去掉的标签（彩票类）。 */
        private val BLOCKED_MATCH_LIST_LABELS = setOf("竞北")

        private const val MATCH_TAB_CACHE = "com.dongqiudi.lib.a"
        private const val TOURNAMENT_DETAIL = "com.dongqiudi.match.TournamentDetailActivity"
        private const val GROW_MATCH_TABS = "com.grow.business_core.model.MatchTabsModel"
        private const val BOTTOM_ITEM_VIEW = "com.dongqiudi.news.view.MainBottomItemView"
        private const val MAIN_ACTIVITY = "com.dongqiudi.news.MainActivity"
        private const val BASE_NEWS_FRAGMENT = "com.dongqiudi.news.fragment.BaseNewsFragment"
        private const val HOME_TABS_MANAGER = "com.dongqiudi.news.manager.h0"
        private const val NEW_GROUP_LIST_ACTIVITY = "com.dongqiudi.group.NewGroupListActivity"
        private const val MAIN_TAB_MATCH = "com.dongqiudi.match.fragment.MainTabMatchFragment"
        private const val MAIN_TAB_MATCH_ADAPTER = "com.dongqiudi.match.adapter.i"
        private const val MATCH_PRO_FRAGMENT = "com.grow.match_business.fragments.MatchBaseFragment"

        /** MainBottomView 的格子序号（view tag）：0 首页，3 原「预测」现「圈子」。 */
        private const val BOTTOM_HOME = 0
        private const val BOTTOM_CIRCLE = 3
    }
}
