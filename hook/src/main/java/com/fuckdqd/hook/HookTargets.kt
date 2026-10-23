package com.fuckdqd.hook

import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 懂球帝里被 R8 改了名的 hook 目标（类 / 方法），换版本后会变。
 *
 * 每个目标一条 DexKit 查询，锚点都选换版本也不容易变的东西：原名类、方法里的字符串常量、
 * 参数类型、Kotlin 编译进去的参数名字符串、调用关系。查到的结果按「versionCode + apk 指纹 + 查询版本」
 * 缓存在懂球帝私有目录，同一个版本只跑一次 DexKit。
 *
 * 8.7.2（10441）是逆向时的版本，保留写死的结果：DexKit 某一项查不到 / 不唯一时兜底，
 * 同时把 DexKit 的结果和写死的逐项对比打进日志，用来验证查询本身没写错。
 */
class HookTargets private constructor(private val cl: ClassLoader, private val found: Map<String, List<String>>) {

    fun clazz(key: String): Class<*>? = found[key]?.firstOrNull()?.let { runCatching { DexClass(it).getInstance(cl) }.getOrNull() }

    fun method(key: String): Method? = methods(key).firstOrNull()

    fun methods(key: String): List<Method> =
        found[key].orEmpty().mapNotNull { runCatching { DexMethod(it).getMethodInstance(cl) }.getOrNull() }

    companion object {
        /** 改了任何查询都要加一，旧缓存作废。 */
        private const val QUERY_VERSION = 3
        private const val VERIFIED_VERSION_CODE = 10441L

        // ---- 目标 key --------------------------------------------------------
        const val SPLASH_REQUEST = "ads.splashRequest"           // DQDAds.K0(f, String, String)
        const val FEED_REQUEST = "ads.feedRequest"               // DQDAds.E0(d, String×5, int, int)
        const val NEWS_FEED_REQUEST = "ads.newsFeedRequest"      // DQDAds.R0(e, AdsRequestModel, int, String, Map)
        const val BANNER_REQUEST = "ads.bannerRequest"           // DQDAds.O0(f, String, String, String)
        const val CACHE_SPLASH_SDK = "ads.cacheSplashSdk"        // ads.sdk.k.F(Context, List)
        const val CACHED_FEED_SDK = "ads.cachedFeedSdk"          // ads.sdk.k.f(Context, String)
        const val HOT_SPLASH_CACHE = "ads.hotSplashCache"        // lib.b.i()
        const val POP_REQUEST = "ads.popRequest"                 // MainPopAdsManager.S(AdsRequestModel, Runnable)
        const val POP_PRELOAD_SHOW = "ads.popPreloadShow"        // MainPopAdsManager.W / d0(AdsRequestModel): boolean
        const val ARTICLE_AD_PLUGIN = "web.articleAdPlugin"      // web.plugins.i（ArticleADPlugin.kt）
        const val HTML_REQUEST_PLUGIN = "web.htmlRequestPlugin"  // web.plugins.t0（HtmlRequestPlugin.kt）
        const val CLOSE_NATIVE_AD = "web.closeNativeAd"          // web.base.n.K()（WebViewWrapper.kt）
        const val HOME_TABS = "home.tabs"                        // news.manager.h0.i()
        const val MATCH_LIST_TABS = "match.listTabs"             // lib.a.b()（ConstPool.kt）
        const val MATCH_TAB_ADAPTER = "match.tabAdapter"         // match.adapter.i（MainTabMatchAdapter.kt）

        private const val DQD_ADS = "com.dongqiudi.ads.sdk.DQDAds"
        private const val ADS_REQUEST_MODEL = "com.dongqiudi.ads.sdk.model.AdsRequestModel"
        private const val ADS_RESPONSE_MODEL = "com.dongqiudi.ads.sdk.model.AdsResponseModel"
        private const val MAIN_POP_ADS_MANAGER = "com.dongqiudi.news.tab.main.MainPopAdsManager"
        private const val AD_SPLASH_VIEW = "com.dongqiudi.news.view.AdSplashView"
        private const val TAB_INTERSTITIAL_ACTION = "com.dongqiudi.news.tab.main.TabInterstitialRequestAction"
        private const val BASE_TOURNAMENT_FRAGMENT = "com.dongqiudi.match.fragment.BaseTournamentFragment"
        private const val FRAGMENT_STATE_ADAPTER = "androidx.viewpager2.adapter.FragmentStateAdapter"

        /** 8.7.2 / 10441 逆向得到的结果（DexKit 描述符格式）。 */
        private val VERIFIED: Map<String, List<String>> = mapOf(
            SPLASH_REQUEST to listOf("Lcom/dongqiudi/ads/sdk/DQDAds;->K0(Lcom/dongqiudi/ads/sdk/DQDAds\$f;Ljava/lang/String;Ljava/lang/String;)V"),
            FEED_REQUEST to listOf("Lcom/dongqiudi/ads/sdk/DQDAds;->E0(Lcom/dongqiudi/ads/sdk/DQDAds\$d;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)V"),
            NEWS_FEED_REQUEST to listOf("Lcom/dongqiudi/ads/sdk/DQDAds;->R0(Lcom/dongqiudi/ads/sdk/utils/e;Lcom/dongqiudi/ads/sdk/model/AdsRequestModel;ILjava/lang/String;Ljava/util/Map;)V"),
            BANNER_REQUEST to listOf("Lcom/dongqiudi/ads/sdk/DQDAds;->O0(Lcom/dongqiudi/ads/sdk/DQDAds\$f;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"),
            CACHE_SPLASH_SDK to listOf("Lcom/dongqiudi/ads/sdk/k;->F(Landroid/content/Context;Ljava/util/List;)Z"),
            CACHED_FEED_SDK to listOf("Lcom/dongqiudi/ads/sdk/k;->f(Landroid/content/Context;Ljava/lang/String;)Ljava/util/List;"),
            HOT_SPLASH_CACHE to listOf("Lcom/dongqiudi/lib/b;->i()Lcom/dongqiudi/ads/sdk/model/AdsResponseModel;"),
            POP_REQUEST to listOf("Lcom/dongqiudi/news/tab/main/MainPopAdsManager;->S(Lcom/dongqiudi/ads/sdk/model/AdsRequestModel;Ljava/lang/Runnable;)V"),
            POP_PRELOAD_SHOW to listOf(
                "Lcom/dongqiudi/news/tab/main/MainPopAdsManager;->W(Lcom/dongqiudi/ads/sdk/model/AdsRequestModel;)Z",
                "Lcom/dongqiudi/news/tab/main/MainPopAdsManager;->d0(Lcom/dongqiudi/ads/sdk/model/AdsRequestModel;)Z",
            ),
            ARTICLE_AD_PLUGIN to listOf("Lcom/dongqiudi/news/web/plugins/i;"),
            HTML_REQUEST_PLUGIN to listOf("Lcom/dongqiudi/news/web/plugins/t0;"),
            CLOSE_NATIVE_AD to listOf("Lcom/dongqiudi/news/web/base/n;->K()V"),
            HOME_TABS to listOf("Lcom/dongqiudi/news/manager/h0;->i()Ljava/util/List;"),
            MATCH_LIST_TABS to listOf("Lcom/dongqiudi/lib/a;->b()Ljava/util/List;"),
            MATCH_TAB_ADAPTER to listOf("Lcom/dongqiudi/match/adapter/i;"),
        )

        /**
         * 拿到本次可用的目标。[versionCode] 拿不到时传 -1；[apkFingerprint] 用于同 versionCode 重装时让缓存失效。
         */
        fun load(module: HookEntry, cl: ClassLoader, versionCode: Long, apkFingerprint: String): HookTargets {
            val cacheKey = "$QUERY_VERSION:$versionCode:$apkFingerprint"
            val cacheFile = File("/data/data/${Keys.TARGET_PACKAGE}/files/fuckdqd_targets.json")
            readCache(cacheFile, cacheKey)?.let {
                module.trace("targets from cache (${it.size} keys, version $versionCode)")
                return HookTargets(cl, it)
            }

            val start = System.currentTimeMillis()
            val searched = runCatching { search(module, cl) }
                .onFailure { module.trace("dexkit failed: $it") }
                .getOrDefault(emptyMap())
            val verified = versionCode == VERIFIED_VERSION_CODE
            val result = LinkedHashMap<String, List<String>>()
            val report = ArrayList<String>()
            for ((key, fallback) in VERIFIED) {
                val hit = searched[key].orEmpty()
                when {
                    hit.isNotEmpty() -> result[key] = hit
                    verified -> result[key] = fallback
                }
                if (verified) {
                    report += when {
                        hit.isEmpty() -> "$key=MISS"
                        hit.sorted() == fallback.sorted() -> "$key=ok"
                        else -> "$key=DIFF${hit}"
                    }
                } else if (hit.isEmpty()) {
                    report += "$key=MISS"
                }
            }
            module.trace("dexkit ${System.currentTimeMillis() - start}ms, version $versionCode: " +
                report.ifEmpty { listOf("all ${result.size} found") }.joinToString(", "))
            // 只有 DexKit 真跑成功才缓存，免得一次失败把兜底结果钉死
            if (searched.isNotEmpty()) writeCache(cacheFile, cacheKey, result)
            return HookTargets(cl, result)
        }

        private fun search(module: HookEntry, cl: ClassLoader): Map<String, List<String>> {
            System.loadLibrary("dexkit")
            val out = LinkedHashMap<String, List<String>>()
            DexKitBridge.create(cl, true).use { bridge ->
                fun methods(key: String, expect: IntRange = 1..1, query: () -> List<MethodData>) {
                    runCatching(query).onSuccess { hits ->
                        if (hits.size in expect) out[key] = hits.map { it.toDexMethod().serialize() }
                        else module.trace("dexkit $key: ${hits.size} hits ${hits.take(4).map { it.descriptor }}")
                    }.onFailure { module.trace("dexkit $key failed: $it") }
                }
                fun classes(key: String, query: () -> List<ClassData>) {
                    runCatching(query).onSuccess { hits ->
                        if (hits.size == 1) out[key] = hits.map { it.toDexClass().serialize() }
                        else module.trace("dexkit $key: ${hits.size} hits ${hits.take(4).map { it.name }}")
                    }.onFailure { module.trace("dexkit $key failed: $it") }
                }

                // 开屏请求：静态，拼 plat/v4 广告位接口，ct 固定 tab_1，3 个参数
                methods(SPLASH_REQUEST) {
                    bridge.findMethod {
                        matcher {
                            declaredClass(DQD_ADS)
                            modifiers = Modifier.STATIC
                            paramCount = 3
                            usingStrings("plat/v4?pgid=", "&ct=tab_1")
                        }
                    }
                }
                // 单个广告位（横幅 / 详情页）：同一个 plat/v4 接口，ct 由调用方给，4 个参数
                methods(BANNER_REQUEST) {
                    bridge.findMethod {
                        matcher {
                            declaredClass(DQD_ADS)
                            paramCount = 4
                            usingStrings("plat/v4?pgid=")
                        }
                    }
                }
                // 信息流：plat/v3 接口，带视频播放时长参数
                methods(FEED_REQUEST) {
                    bridge.findMethod {
                        matcher {
                            declaredClass(DQD_ADS)
                            usingStrings("plat/v3?pgid=", "&vpduration=")
                        }
                    }
                }
                // 新闻流：参数里有原名类 AdsRequestModel，签名 (callback, AdsRequestModel, int, String, Map)
                methods(NEWS_FEED_REQUEST) {
                    bridge.findMethod {
                        matcher {
                            declaredClass(DQD_ADS)
                            modifiers = Modifier.STATIC
                            paramTypes(null, ADS_REQUEST_MODEL, "int", "java.lang.String", "java.util.Map")
                        }
                    }
                }
                // 广告 SDK 配置缓存：按 MMKV key 找
                methods(CACHE_SPLASH_SDK) {
                    bridge.findMethod {
                        matcher {
                            paramTypes("android.content.Context", "java.util.List")
                            usingStrings(listOf("sdk_ids_332"), StringMatchType.Equals)
                        }
                    }
                }
                methods(CACHED_FEED_SDK) {
                    bridge.findMethod {
                        matcher {
                            paramTypes("android.content.Context", "java.lang.String")
                            usingStrings("feed_sdk_ad_list_332_")
                        }
                    }
                }
                // 热启动开屏缓存：AdSplashView（原名）里调用的、无参返回 AdsResponseModel 的方法
                methods(HOT_SPLASH_CACHE) {
                    bridge.findMethod {
                        matcher {
                            paramCount = 0
                            returnType = ADS_RESPONSE_MODEL
                            callerMethods { add { declaredClass(AD_SPLASH_VIEW) } }
                        }
                    }.filter { it.className != AD_SPLASH_VIEW }
                }
                // 首页插屏弹窗：MainPopAdsManager 是原名类，方法按签名找
                methods(POP_REQUEST) {
                    bridge.findMethod {
                        matcher {
                            declaredClass(MAIN_POP_ADS_MANAGER)
                            paramTypes(ADS_REQUEST_MODEL, "java.lang.Runnable")
                            returnType = "void"
                        }
                    }
                }
                // 预加载 W / 展示 d0：都是 boolean (AdsRequestModel)，都直接返回 false。
                // 同签名还有个内部判断 c0 不能动。W 和 c0 在 dex 里都是 private（jadx 把 W 显示成 public 是错的），
                // 所以：d0 = 唯一非 private 的；W = 会调「算出 TabInterstitialRequestAction（原名枚举）」那个方法的
                methods(POP_PRELOAD_SHOW, 2..2) {
                    val all = bridge.findMethod {
                        matcher {
                            declaredClass(MAIN_POP_ADS_MANAGER)
                            paramTypes(ADS_REQUEST_MODEL)
                            returnType = "boolean"
                        }
                    }
                    val preload = bridge.findMethod {
                        matcher {
                            declaredClass(MAIN_POP_ADS_MANAGER)
                            paramTypes(ADS_REQUEST_MODEL)
                            returnType = "boolean"
                            invokeMethods {
                                add { returnType = TAB_INTERSTITIAL_ACTION }
                                matchType = MatchType.Contains
                            }
                        }
                    }
                    all.filter { !Modifier.isPrivate(it.modifiers) } + preload
                }
                // 文章页 H5 广告插件：getName() 返回 jsbridge 方法名 androidSdkAdsRequest
                classes(ARTICLE_AD_PLUGIN) {
                    bridge.findClass {
                        matcher {
                            usingStrings(listOf("androidSdkAdsRequest"), StringMatchType.Equals)
                            methods { add { name = "onHandle" } }
                        }
                    }
                }
                // 代发 H5 请求的插件：getName() 返回 adRequest / htmlRequest
                classes(HTML_REQUEST_PLUGIN) {
                    bridge.findClass {
                        matcher {
                            usingStrings(listOf("adRequest", "htmlRequest"), StringMatchType.Equals)
                            methods { add { name = "onHandle" } }
                        }
                    }
                }
                // WebViewWrapper 里给 H5 发 closeNativeAd 的无参方法
                methods(CLOSE_NATIVE_AD) {
                    bridge.findMethod {
                        matcher {
                            paramCount = 0
                            returnType = "void"
                            usingStrings(listOf("closeNativeAd"), StringMatchType.Equals)
                        }
                    }
                }
                // 首页频道列表：首页标签管理单例里打 "[getNewsTabs] get data from share" 日志的无参 List 方法
                methods(HOME_TABS) {
                    bridge.findMethod {
                        matcher {
                            paramCount = 0
                            returnType = "java.util.List"
                            usingStrings("[getNewsTabs]")
                        }
                    }
                }
                // 比赛列表顶部标签缓存：BaseTournamentFragment.onCreate（原名）里读的那个无参 List getter
                methods(MATCH_LIST_TABS) {
                    bridge.findMethod {
                        matcher {
                            paramCount = 0
                            returnType = "java.util.List"
                            declaredClass { source("ConstPool.kt") }
                            callerMethods { add { declaredClass(BASE_TOURNAMENT_FRAGMENT); name = "onCreate" } }
                        }
                    }
                }
                // 比赛 tab 的 ViewPager2 适配器：构造参数名 tabId / onTabChange 会被 Kotlin 编进判空字符串
                classes(MATCH_TAB_ADAPTER) {
                    bridge.findClass {
                        matcher {
                            superClass(FRAGMENT_STATE_ADAPTER)
                            usingStrings(listOf("onTabChange", "tabId"), StringMatchType.Equals)
                        }
                    }
                }
            }
            return out
        }

        private fun readCache(file: File, key: String): Map<String, List<String>>? = runCatching {
            val json = JSONObject(file.readText())
            if (json.getString("key") != key) return null
            val targets = json.getJSONObject("targets")
            targets.keys().asSequence().associateWith { k ->
                val arr = targets.getJSONArray(k)
                List(arr.length()) { arr.getString(it) }
            }
        }.getOrNull()

        private fun writeCache(file: File, key: String, targets: Map<String, List<String>>) {
            runCatching {
                val json = JSONObject()
                    .put("key", key)
                    .put("targets", JSONObject().also { o -> targets.forEach { (k, v) -> o.put(k, JSONArray(v)) } })
                file.writeText(json.toString())
            }
        }
    }
}
