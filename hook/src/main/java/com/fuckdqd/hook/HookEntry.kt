package com.fuckdqd.hook

import android.app.Application
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 入口（META-INF/xposed/java_init.list）。
 *
 * 懂球帝加了爱加密壳，实测（LSPosed 2.2.1）：
 *  - onPackageReady 不回调，只有 onModuleLoaded（在 handleBindApplication 里被调用）；
 *  - 壳的 AppComponentFactory（s.h.e.l.l.A）在 instantiateClassLoader 里触发 N.<clinit>
 *    加载 libexec.so，再调 N.al 解密出真实 ClassLoader；
 *  - 在这之前或这期间装的 hook 之后都不再触发（壳在这一段清 hook）。
 * 所以在 onModuleLoaded 里先挂 LoadedApk 上包住这一步的外层方法：壳清 hook 时这些帧
 * 已经在栈上，proceed() 返回时清理已经做完、真实 dex 已就位、Application 还没创建，
 * 这时用 LoadedApk.mClassLoader 装广告 hook。
 */
class HookEntry : XposedModule() {

    private val installed = AtomicBoolean(false)

    // 只管主进程：:pushcore / :core 这些子进程不展示广告
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        if (param.processName != Keys.TARGET_PACKAGE) return
        runCatching {
            val loadedApk = Class.forName("android.app.LoadedApk")
            val mClassLoader = loadedApk.getDeclaredField("mClassLoader").apply { isAccessible = true }
            val anchors = loadedApk.declaredMethods.filter { it.name in ANCHORS }
            for (m in anchors) {
                hook(m).intercept { chain ->
                    val result = chain.proceed()
                    if (!installed.get()) {
                        val cl = listOfNotNull(
                            runCatching { mClassLoader.get(chain.thisObject) as? ClassLoader }.getOrNull(),
                            (result as? Application)?.javaClass?.classLoader,
                        ).firstOrNull { runCatching { it.loadClass(AdHooks.DQD_ADS) }.isSuccess }
                        trace("${m.name} returned, real dex ${if (cl != null) "ready" else "missing"}")
                        if (cl != null && installed.compareAndSet(false, true)) {
                            runCatching { install(cl) }.onFailure { log(Log.ERROR, TAG, "install failed", it) }
                        }
                    }
                    result
                }
            }
            // 这几个都在 boot image 里，小方法会被调用方内联，绕过 hook 入口，得把调用方反优化
            for (m in loadedApk.declaredMethods) if (m.name in ANCHORS) deoptimize(m)
            Class.forName("android.app.ActivityThread").declaredMethods
                .filter { it.name == "handleBindApplication" }.forEach { deoptimize(it) }
            trace("armed ${anchors.joinToString { it.name }}")
        }.onFailure { log(Log.ERROR, TAG, "arm LoadedApk anchors failed", it) }
    }

    private fun install(cl: ClassLoader) {
        val prefs = runCatching { getRemotePreferences(Keys.GROUP) }.getOrNull()
        fun on(key: String) = runCatching { prefs?.getBoolean(key, Keys.DEFAULT_ON) }.getOrNull() ?: Keys.DEFAULT_ON

        val hooks = AdHooks(this, cl)
        val report = buildList {
            if (on(Keys.SPLASH)) add("splash=" + hooks.splash())
            if (on(Keys.FEED)) add("feed=" + hooks.feed())
            if (on(Keys.POPUP)) add("popup=" + hooks.popup())
            if (on(Keys.BANNER)) add("banner=" + hooks.banner())
        }
        trace("installed: " + report.joinToString(", ").ifEmpty { "nothing (all off)" })    }

    private val traceFile = File("/data/data/${Keys.TARGET_PACKAGE}/files/fuckdqd.log")
    private val traceStarted = AtomicBoolean(false)

    /**
     * 写 LSPosed 模块日志、logcat，再追加到懂球帝私有目录的 fuckdqd.log（每次启动清空）。
     * 壳在 N.al 之后会让本模块的 logcat / 模块日志都打不出来，实际只有文件靠得住：
     * adb shell su -c cat /data/data/com.dongqiudi.news/files/fuckdqd.log
     */
    internal fun trace(msg: String) {
        log(Log.INFO, TAG, msg)
        Log.i(TAG, msg)
        runCatching {
            val line = "${timeFormat.format(Date())} $msg\n"
            if (traceStarted.compareAndSet(false, true)) traceFile.writeText(line) else traceFile.appendText(line)
        }
    }

    companion object {
        const val TAG = "FuckDQD"
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        /**
         * 壳在 A.instantiateClassLoader 里调 N.al（Android 10+），它的调用链是
         * makeApplicationInner → getClassLoader → createOrUpdateClassLoaderLocked。
         * 挂整条链，谁的帧包住了 N.al 谁就能在返回时装上；onModuleLoaded 时已经进到哪一层不确定。
         */
        private val ANCHORS = setOf(
            "createOrUpdateClassLoaderLocked", "getClassLoader", "makeApplication", "makeApplicationInner",
        )
    }
}
