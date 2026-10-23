package io.github.kotoriinsky.fuckdqd.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.kotoriinsky.fuckdqd.hook.Keys

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FrameworkBridge.start()
        // 模块没启用时框架不会回调，等一会儿还没连上就判定为未激活
        Handler(Looper.getMainLooper()).postDelayed(FrameworkBridge::markTimedOut, BIND_TIMEOUT_MS)
        setContent { AppScreen(this) }
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚在 LSPosed 管理器里改了作用域
        FrameworkBridge.refresh()
    }

    companion object {
        private const val BIND_TIMEOUT_MS = 4_000L
    }
}

/** 只属于设置界面自己的偏好（深色模式、图标），不需要给 hook 侧看。 */
object LocalSettings {
    private const val FILE = "ui"
    private const val KEY_DARK = "dark_mode"
    private const val LAUNCHER_ALIAS = "io.github.kotoriinsky.fuckdqd.ui.LauncherAlias"

    enum class DarkMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

    fun darkMode(ctx: Context): DarkMode =
        runCatching { DarkMode.valueOf(prefs(ctx).getString(KEY_DARK, null)!!) }.getOrDefault(DarkMode.SYSTEM)

    fun setDarkMode(ctx: Context, mode: DarkMode) = prefs(ctx).edit().putString(KEY_DARK, mode.name).apply()

    fun isIconHidden(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx, LAUNCHER_ALIAS)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setIconHidden(ctx: Context, hidden: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, LAUNCHER_ALIAS),
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun targetVersion(ctx: Context): String? = runCatching {
        val info = ctx.packageManager.getPackageInfo(Keys.TARGET_PACKAGE, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName} ($code)"
    }.getOrNull()

    /** 强制停止懂球帝让新配置生效。需要 root；返回 null 表示成功，否则是失败原因。 */
    fun forceStopTarget(): String? = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop ${Keys.TARGET_PACKAGE}"))
        if (p.waitFor() == 0) null else "su 返回 ${p.exitValue()}"
    }.getOrElse { it.message ?: it.javaClass.simpleName }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
