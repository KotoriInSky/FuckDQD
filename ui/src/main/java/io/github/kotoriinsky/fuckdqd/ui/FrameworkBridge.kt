package io.github.kotoriinsky.fuckdqd.ui

import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.kotoriinsky.fuckdqd.hook.Keys
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

data class FrameworkStatus(
    /** 还没收到框架答复。模块没启用时框架不会回调，靠 [FrameworkBridge.markTimedOut] 收尾。 */
    val loading: Boolean = true,
    val connected: Boolean = false,
    val frameworkName: String? = null,
    val frameworkVersion: String? = null,
    val apiVersion: Int = 0,
    val scopeHasTarget: Boolean = false,
)

/**
 * 与 LSPosed 的桥。registerListener 是进程级、只该调一次的 API，所以做成单例。
 *
 * 广告开关存在框架的 RemotePreferences 里（hook 侧读同一份），
 * 框架没连上时开关显示默认值且不可改。
 */
object FrameworkBridge {

    private val _status = mutableStateOf(FrameworkStatus())
    val status: State<FrameworkStatus> = _status

    private val _toggles = mutableStateOf(Keys.ALL.associateWith { Keys.DEFAULT_ON })
    val toggles: State<Map<String, Boolean>> = _toggles

    private val started = AtomicBoolean(false)
    private var service: XposedService? = null
    private var prefs: SharedPreferences? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(svc: XposedService) {
                service = svc
                prefs = runCatching { svc.getRemotePreferences(Keys.GROUP) }.getOrNull()
                refresh()
            }

            override fun onServiceDied(svc: XposedService) {
                service = null
                prefs = null
                _status.value = FrameworkStatus(loading = false)
            }
        })
    }

    fun markTimedOut() {
        if (service == null) _status.value = FrameworkStatus(loading = false)
    }

    fun refresh() {
        val svc = service ?: return
        _status.value = FrameworkStatus(
            loading = false,
            connected = true,
            frameworkName = runCatching { svc.frameworkName }.getOrNull(),
            frameworkVersion = runCatching { svc.frameworkVersion }.getOrNull(),
            apiVersion = runCatching { svc.apiVersion }.getOrDefault(0),
            scopeHasTarget = runCatching { Keys.TARGET_PACKAGE in svc.scope }.getOrDefault(false),
        )
        val p = prefs ?: return
        _toggles.value = Keys.ALL.associateWith { key ->
            runCatching { p.getBoolean(key, Keys.DEFAULT_ON) }.getOrDefault(Keys.DEFAULT_ON)
        }
    }

    /** 写入成功才更新界面，避免显示一个其实没存下来的状态。 */
    fun setToggle(key: String, value: Boolean): Boolean {
        val p = prefs ?: return false
        return runCatching {
            p.edit().putBoolean(key, value).apply()
            _toggles.value = _toggles.value + (key to value)
        }.isSuccess
    }

    /** 作用域缺了懂球帝时请求补上；框架会弹确认框，用户同意才生效。 */
    fun requestScope(onResult: (String?) -> Unit) {
        val svc = service ?: return onResult("框架未连接")
        runCatching {
            svc.requestScope(listOf(Keys.TARGET_PACKAGE), object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: MutableList<String>) {
                    refresh()
                    onResult(null)
                }

                override fun onScopeRequestFailed(message: String) = onResult(message)
            })
        }.onFailure { onResult(it.javaClass.simpleName) }
    }
}
