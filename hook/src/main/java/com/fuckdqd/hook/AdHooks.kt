package com.fuckdqd.hook

import android.os.Handler
import android.os.Looper

/**
 * 广告 hook 点，按设置页的开关分组。依据见 docs/hook-points.md（懂球帝 8.7.2 / 10441）。
 *
 * 所有广告位都先经过懂球帝自己的调度层 com.dongqiudi.ads.sdk.DQDAds：它向
 * ap.dongqiudi.com/plat/v4 拿广告位配置，再决定拉哪家三方 SDK。所以在这一层
 * 直接回调「没有广告」，后面的穿山甲 / 优量汇 / 快手 / 百度等 SDK 就都不会被调起。
 *
 * 混淆过的类 / 方法从 [HookTargets] 取（DexKit 按特征查，换版本自动重查）；每组返回装上的数量，
 * 日志里出现 missing 就说明那条查询在新版本上失效了。
 */
class AdHooks(module: HookEntry, cl: ClassLoader, targets: HookTargets) : HookGroup(module, cl, targets) {

    /** 开屏：冷启动 / 热启动请求、开屏选广告、热启动缓存。 */
    fun splash(): String = group {
        // DQDAds.K0(f callback, String tag, String pgid)：开屏广告请求（requestLaunch），直接回 onError
        target(HookTargets.SPLASH_REQUEST)?.let { m ->
            replace(m) { chain ->
                chain.getArg(0)?.let { callback(it, m.parameterTypes[0], "onError") }
                null
            }
        }
        // AdSplashView.checkAds()：清空候选并标记请求结束，走 setupSplash(null) 默认启动图
        method(AD_SPLASH_VIEW, "checkAds")?.let { m ->
            intercept(m) { chain ->
                val view = chain.thisObject
                runCatching {
                    (field(view, "mAdCheckModels") as? MutableList<*>)?.clear()
                    setField(view, "mAdRequestFinished", true)
                }
                chain.proceed()
            }
        }
        // lib.b.i()：热启动用的缓存开屏响应（AdSplashView.loadCacheAds 读它）
        target(HookTargets.HOT_SPLASH_CACHE)?.let { m -> replace(m) { null } }
        // ads.sdk.k.F(Context, List)：缓存开屏 SDK 配置，改成写 null
        target(HookTargets.CACHE_SPLASH_SDK)?.let { m ->
            intercept(m) { chain -> chain.proceed(arrayOf(chain.getArg(0), null)) }
        }
    }

    /** 信息流 / 列表：新闻流、视频页、圈子、赛事、彩票等列表里的广告。 */
    fun feed(): String = group {
        // DQDAds.E0(d, tag, pgId, ct, fullTime, vpDuration, page, video_pos)：A0/B0/C0/D0/F0 最终都走这里
        target(HookTargets.FEED_REQUEST)?.let { m ->
            replace(m) { chain ->
                chain.getArg(0)?.let { callback(it, m.parameterTypes[0], "onSuccess", ArrayList<Any>()) }
                null
            }
        }
        // DQDAds.R0(e callback, AdsRequestModel, int page, String key, Map extra)：新闻流 DqdFeedAdRequest
        target(HookTargets.NEWS_FEED_REQUEST)?.let { m ->
            replace(m) { chain ->
                // 与 DqdFeedAdRequest 自己「没有缓存配置」时的回调一致
                chain.getArg(0)?.let { callback(it, m.parameterTypes[0], "onADLoadSuccess", null, -1, 0) }
                null
            }
        }
        // ads.sdk.k.f(Context, String pgId)：MMKV 里缓存的信息流 SDK 配置
        target(HookTargets.CACHED_FEED_SDK)?.let { m -> replace(m) { null } }
    }

    /** 弹窗：首页 tab 插屏、H5 里的 showPopupAD（j2 插件同样走 MainPopAdsManager）。 */
    fun popup(): String = group {
        // S(AdsRequestModel, Runnable)：请求并在就绪后展示
        target(HookTargets.POP_REQUEST)?.let { m -> replace(m) { null } }
        // W(AdsRequestModel) 预加载、d0(AdsRequestModel) 从缓存取出并展示：都回 false
        targetAll(HookTargets.POP_PRELOAD_SHOW).forEach { m -> replace(m) { false } }
    }

    /** 横幅 / 详情页：赛事页、直播视频页的单个广告位，文章详情页转发区上方的广告。 */
    fun banner(): String = group {
        // 文章 H5 的广告分两步：先经 HtmlRequestPlugin「adRequest」让客户端代请求广告接口，
        // 再把结果经 ArticleADPlugin「androidSdkAdsRequest」交回客户端，由 AdWebContainer
        // 盖在网页预留位上，并把缩放比回调给 H5 定预留位高度。
        // 两个插件的 onHandle(String functionName, JsonObject data, CallBackFunction callback)：
        // 回调接口 com.github.lzyzsd.jsbridge.f 也是混淆名，从参数类型里拿

        // HtmlRequestPlugin.onHandle：adRequest 直接回「网络错误」（与插件自己请求失败时的格式一致），
        // htmlRequest 是正文等普通请求，照常放行
        targetClass(HookTargets.HTML_REQUEST_PLUGIN)?.let { declared(it, "onHandle", 3) }?.let { m ->
            intercept(m) { chain ->
                if (chain.getArg(0) != "adRequest") return@intercept chain.proceed()
                module.trace("blocked ${m.declaringClass.name}.onHandle(adRequest)")
                runCatching {
                    val now = System.currentTimeMillis()
                    val body = """{"_responseWrapedByNative":{"code":1002},""" +
                        """"native_ajax_start_time":$now,"native_ajax_end_time":$now}"""
                    chain.getArg(2)?.let { callback(it, m.parameterTypes[2], "onCallBack", body) }
                    closeNativeAd(chain.thisObject!!)
                }
                null
            }
        }
        // ArticleADPlugin.onHandle：万一 H5 还是走到了这一步，回调缩放比 0 并收起预留位
        targetClass(HookTargets.ARTICLE_AD_PLUGIN)?.let { declared(it, "onHandle", 3) }?.let { m ->
            replace(m) { chain ->
                chain.getArg(2)?.let { callback(it, m.parameterTypes[2], "onCallBack", "0") }
                closeNativeAd(chain.thisObject!!)
                null
            }
        }
        closeAdMethod = target(HookTargets.CLOSE_NATIVE_AD)
        // DQDAds.O0(f callback, String tag, String pgid, String ct)
        target(HookTargets.BANNER_REQUEST)?.let { m ->
            replace(m) { chain ->
                chain.getArg(0)?.let { callback(it, m.parameterTypes[0], "onError") }
                null
            }
        }
    }

    /** WebViewWrapper 里给 H5 发 closeNativeAd 的方法（原 n.K()）。 */
    private var closeAdMethod: java.lang.reflect.Method? = null

    /**
     * 走「用户点了关闭广告」的路径：webviewWrapper 给 H5 发 closeNativeAd，网页收起预留位。
     * 调用时 H5 还在这次 bridge 调用里，可能还没准备好处理 close，所以稍后再补发一次。
     * 插件基类的 getWebviewWrapper() 是原名。
     */
    private fun closeNativeAd(plugin: Any) {
        val close = closeAdMethod ?: return
        val wrapper = call(plugin, "getWebviewWrapper") ?: return
        val closeAd = Runnable { runCatching { close.invoke(wrapper) } }
        closeAd.run()
        Handler(Looper.getMainLooper()).postDelayed(closeAd, 600)
    }

    companion object {
        /** 广告调度层，原名类；能加载到它说明真实 dex 已经解密就位。 */
        const val DQD_ADS = "com.dongqiudi.ads.sdk.DQDAds"
        private const val AD_SPLASH_VIEW = "com.dongqiudi.news.view.AdSplashView"
    }
}
