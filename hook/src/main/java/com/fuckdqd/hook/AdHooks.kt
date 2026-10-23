package com.fuckdqd.hook

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/**
 * 广告 hook 点，按设置页的开关分组。依据见 docs/hook-points.md（懂球帝 8.7.2 / 10441）。
 *
 * 所有广告位都先经过懂球帝自己的调度层 com.dongqiudi.ads.sdk.DQDAds：它向
 * ap.dongqiudi.com/plat/v4 拿广告位配置，再决定拉哪家三方 SDK。所以在这一层
 * 直接回调「没有广告」，后面的穿山甲 / 优量汇 / 快手 / 百度等 SDK 就都不会被调起。
 *
 * 类名/方法名是混淆后的，换版本大概率要重新对；每组返回装上的数量，
 * 日志里出现 0 或 missing 就说明该重新逆向了。
 */
class AdHooks(private val module: HookEntry, private val cl: ClassLoader) {

    /** 开屏：冷启动 / 热启动请求、开屏选广告、热启动缓存。 */
    fun splash(): String = group {
        // DQDAds.K0(f callback, String tag, String pgid)：开屏广告请求（requestLaunch）
        method(DQD_ADS, "K0", DQD_ADS_F, STRING, STRING)?.let { m ->
            replace(m) { chain ->
                chain.getArg(0)?.let { invoke(it, DQD_ADS_F, "onError") }
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
        // com.dongqiudi.lib.b.i()：热启动用的缓存开屏响应（loadCacheAds 读它）
        method(LIB_B, "i")?.let { m -> replace(m) { null } }
        // k.F(Context, List)：缓存开屏 SDK 配置，改成写 null
        method(ADS_CACHE, "F", CONTEXT, LIST)?.let { m ->
            intercept(m) { chain -> chain.proceed(arrayOf(chain.getArg(0), null)) }
        }
    }

    /** 信息流 / 列表：新闻流、视频页、圈子、赛事、彩票等列表里的广告。 */
    fun feed(): String = group {
        // DQDAds.E0(d, tag, pgId, ct, fullTime, vpDuration, page, video_pos)：
        // A0/B0/C0/D0/F0 最终都走这里
        method(DQD_ADS, "E0", DQD_ADS_D, STRING, STRING, STRING, STRING, STRING, INT, INT)?.let { m ->
            replace(m) { chain ->
                chain.getArg(0)?.let { invoke(it, DQD_ADS_D, "onSuccess", ArrayList<Any>(), argTypes = arrayOf(LIST)) }
                null
            }
        }
        // DQDAds.R0(e callback, AdsRequestModel, int page, String key, Map extra)：新闻流 DqdFeedAdRequest
        method(DQD_ADS, "R0", AD_REQUEST_CALLBACK, ADS_REQUEST_MODEL, INT, STRING, MAP)?.let { m ->
            replace(m) { chain ->
                // 与 DqdFeedAdRequest 自己「没有缓存配置」时的回调一致
                chain.getArg(0)?.let {
                    invoke(it, AD_REQUEST_CALLBACK, "onADLoadSuccess", null, -1, 0, argTypes = arrayOf(LIST, INT, INT))
                }
                null
            }
        }
        // k.f(Context, String pgId)：MMKV 里缓存的信息流 SDK 配置
        method(ADS_CACHE, "f", CONTEXT, STRING)?.let { m -> replace(m) { null } }
    }

    /** 弹窗：首页 tab 插屏、H5 里的 showPopupAD（j2 插件同样走 MainPopAdsManager）。 */
    fun popup(): String = group {
        // S(AdsRequestModel, Runnable)：请求并在就绪后展示
        method(MAIN_POP_ADS_MANAGER, "S", ADS_REQUEST_MODEL, "java.lang.Runnable")?.let { m -> replace(m) { null } }
        // W(AdsRequestModel)：预加载
        method(MAIN_POP_ADS_MANAGER, "W", ADS_REQUEST_MODEL)?.let { m -> replace(m) { false } }
        // d0(AdsRequestModel)：从缓存取出并展示
        method(MAIN_POP_ADS_MANAGER, "d0", ADS_REQUEST_MODEL)?.let { m -> replace(m) { false } }
    }

    /** 横幅 / 详情页：赛事页、直播视频页的单个广告位，文章详情页转发区上方的广告。 */
    fun banner(): String = group {
        // 文章 H5 的广告分两步：先经 HtmlRequestPlugin「adRequest」让客户端代请求广告接口，
        // 再把结果经 ArticleADPlugin「androidSdkAdsRequest」交回客户端，由 AdWebContainer
        // 盖在网页预留位上，并把缩放比回调给 H5 定预留位高度。

        // HtmlRequestPlugin.onHandle：adRequest 直接回「网络错误」（与插件自己请求失败时的格式一致），
        // htmlRequest 是正文等普通请求，照常放行
        method(HTML_REQUEST_PLUGIN, "onHandle", STRING, JSON_OBJECT, JS_CALLBACK)?.let { m ->
            intercept(m) { chain ->
                if (chain.getArg(0) != "adRequest") return@intercept chain.proceed()
                module.trace("blocked $HTML_REQUEST_PLUGIN.onHandle(adRequest)")
                runCatching {
                    val now = System.currentTimeMillis()
                    val body = """{"_responseWrapedByNative":{"code":1002},""" +
                        """"native_ajax_start_time":$now,"native_ajax_end_time":$now}"""
                    chain.getArg(2)?.let { invoke(it, JS_CALLBACK, "onCallBack", body, argTypes = arrayOf(STRING)) }
                    closeNativeAd(chain.thisObject)
                }
                null
            }
        }
        // ArticleADPlugin.onHandle：万一 H5 还是走到了这一步，回调缩放比 0 并收起预留位
        method(ARTICLE_AD_PLUGIN, "onHandle", STRING, JSON_OBJECT, JS_CALLBACK)?.let { m ->
            replace(m) { chain ->
                chain.getArg(2)?.let { invoke(it, JS_CALLBACK, "onCallBack", "0", argTypes = arrayOf(STRING)) }
                closeNativeAd(chain.thisObject)
                null
            }
        }
        // DQDAds.O0(f callback, String tag, String pgid, String ct)
        method(DQD_ADS, "O0", DQD_ADS_F, STRING, STRING, STRING)?.let { m ->
            replace(m) { chain ->
                chain.getArg(0)?.let { invoke(it, DQD_ADS_F, "onError") }
                null
            }
        }
    }

    /**
     * 走「用户点了关闭广告」的路径：webviewWrapper.K() 给 H5 发 closeNativeAd，网页收起预留位。
     * 调用时 H5 还在这次 bridge 调用里，可能还没准备好处理 close，所以稍后再补发一次。
     */
    private fun closeNativeAd(plugin: Any) {
        val wrapper = cl.loadClass(WEB_PLUGIN_BASE).getDeclaredMethod("getWebviewWrapper")
            .apply { isAccessible = true }.invoke(plugin) ?: return
        val close = wrapper.javaClass.getMethod("K")
        val closeAd = Runnable { runCatching { close.invoke(wrapper) } }
        closeAd.run()
        Handler(Looper.getMainLooper()).postDelayed(closeAd, 600)
    }

    // ---- 工具 -------------------------------------------------------------

    private var hooked = 0
    private val missing = ArrayList<String>()

    private inline fun group(block: () -> Unit): String {
        hooked = 0
        missing.clear()
        block()
        return if (missing.isEmpty()) "$hooked" else "$hooked missing=${missing.joinToString("|")}"
    }

    private fun method(className: String, name: String, vararg params: String): Executable? = try {
        cl.loadClass(className).getDeclaredMethod(name, *params.map(::type).toTypedArray())
    } catch (t: Throwable) {
        missing += "${className.substringAfterLast('.')}.$name"
        module.log(Log.WARN, HookEntry.TAG, "missing $className.$name: ${t.javaClass.simpleName}")
        null
    }

    private fun type(name: String): Class<*> = when (name) {
        INT -> Int::class.javaPrimitiveType!!
        else -> cl.loadClass(name)
    }

    /** 不调原方法，直接返回 [result] 的结果。 */
    private fun replace(m: Executable, result: (XposedInterface.Chain) -> Any?) {
        intercept(m) { chain ->
            module.trace("blocked ${m.declaringClass.name}.${m.name}")
            runCatching { result(chain) }.getOrNull()
        }
    }

    private fun intercept(m: Executable, hooker: XposedInterface.Hooker) {
        module.hook(m).intercept(hooker)
        hooked++
    }

    private fun invoke(target: Any, iface: String, name: String, vararg args: Any?, argTypes: Array<String> = emptyArray()) {
        runCatching {
            cl.loadClass(iface).getMethod(name, *argTypes.map(::type).toTypedArray()).invoke(target, *args)
        }.onFailure { module.log(Log.WARN, HookEntry.TAG, "callback $iface.$name failed", it) }
    }

    private fun field(obj: Any, name: String): Any? =
        obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)

    private fun setField(obj: Any, name: String, value: Any?) {
        obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(obj, value)
    }

    companion object {
        const val DQD_ADS = "com.dongqiudi.ads.sdk.DQDAds"
        private const val DQD_ADS_D = "com.dongqiudi.ads.sdk.DQDAds\$d"
        private const val DQD_ADS_F = "com.dongqiudi.ads.sdk.DQDAds\$f"
        private const val AD_REQUEST_CALLBACK = "com.dongqiudi.ads.sdk.utils.e"
        private const val ADS_REQUEST_MODEL = "com.dongqiudi.ads.sdk.model.AdsRequestModel"
        private const val ADS_CACHE = "com.dongqiudi.ads.sdk.k"
        private const val AD_SPLASH_VIEW = "com.dongqiudi.news.view.AdSplashView"
        private const val LIB_B = "com.dongqiudi.lib.b"
        private const val MAIN_POP_ADS_MANAGER = "com.dongqiudi.news.tab.main.MainPopAdsManager"
        private const val ARTICLE_AD_PLUGIN = "com.dongqiudi.news.web.plugins.i"
        private const val WEB_PLUGIN_BASE = "com.dongqiudi.news.web.base.h"
        private const val HTML_REQUEST_PLUGIN = "com.dongqiudi.news.web.plugins.t0"
        private const val JS_CALLBACK = "com.github.lzyzsd.jsbridge.f"
        private const val JSON_OBJECT = "com.google.gson.JsonObject"

        private const val STRING = "java.lang.String"
        private const val CONTEXT = "android.content.Context"
        private const val LIST = "java.util.List"
        private const val MAP = "java.util.Map"
        private const val INT = "int"
    }
}
