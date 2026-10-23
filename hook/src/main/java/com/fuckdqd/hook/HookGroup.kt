package com.fuckdqd.hook

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 一组 hook 的公共工具：按类名 / 方法签名找方法，挂 hook，统计装上的数量。
 * 每个开关对应子类里的一个方法，用 [group] 包起来返回「装上数 + 缺失项」给日志。
 * 原名的类 / 方法直接按名字找；被混淆的从 [targets]（DexKit 查到的）里取。
 */
abstract class HookGroup(
    protected val module: HookEntry,
    protected val cl: ClassLoader,
    private val targets: HookTargets,
) {

    private var hooked = 0
    private val missing = ArrayList<String>()

    protected fun group(block: () -> Unit): String {
        hooked = 0
        missing.clear()
        block()
        return if (missing.isEmpty()) "$hooked" else "$hooked missing=${missing.joinToString("|")}"
    }

    protected fun method(className: String, name: String, vararg params: String): Executable? = try {
        cl.loadClass(className).getDeclaredMethod(name, *params.map(::type).toTypedArray())
    } catch (t: Throwable) {
        missing += "${className.substringAfterLast('.')}.$name"
        module.log(Log.WARN, HookEntry.TAG, "missing $className.$name: ${t.javaClass.simpleName}")
        null
    }

    /** 被混淆的方法：取 [HookTargets] 里 [key] 对应的第一个。 */
    protected fun target(key: String): Method? = targets.method(key) ?: run { missing += key; null }

    /** 被混淆的方法：[key] 对应的全部（同样处理的一组）。 */
    protected fun targetAll(key: String): List<Method> = targets.methods(key).also { if (it.isEmpty()) missing += key }

    protected fun targetClass(key: String): Class<*>? = targets.clazz(key) ?: run { missing += key; null }

    /** 类上声明的、[paramCount] 个参数的 [name] 方法；找不到记为缺失。 */
    protected fun declared(cls: Class<*>, name: String, paramCount: Int): Method? =
        cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == paramCount }
            ?: run { missing += "${cls.simpleName}.$name"; null }

    /**
     * 调回调接口上的方法。接口类是混淆名，从被 hook 方法的参数类型里拿（[type]），方法按名字 + 参数个数找。
     */
    protected fun callback(target: Any, type: Class<*>, name: String, vararg args: Any?) {
        runCatching {
            type.methods.first { it.name == name && it.parameterCount == args.size }.invoke(target, *args)
        }.onFailure { module.log(Log.WARN, HookEntry.TAG, "callback ${type.name}.$name failed", it) }
    }

    protected fun constructor(className: String, vararg params: String): Executable? = try {
        cl.loadClass(className).getDeclaredConstructor(*params.map(::type).toTypedArray())
    } catch (t: Throwable) {
        missing += "${className.substringAfterLast('.')}.<init>"
        module.log(Log.WARN, HookEntry.TAG, "missing $className.<init>: ${t.javaClass.simpleName}")
        null
    }

    protected fun type(name: String): Class<*> = when (name) {
        INT -> Int::class.javaPrimitiveType!!
        BOOLEAN -> Boolean::class.javaPrimitiveType!!
        else -> cl.loadClass(name)
    }

    /** 不调原方法，直接返回 [result] 的结果。 */
    protected fun replace(m: Executable, result: (XposedInterface.Chain) -> Any?) {
        intercept(m) { chain ->
            module.trace("blocked ${m.declaringClass.name}.${m.name}")
            runCatching { result(chain) }.getOrNull()
        }
    }

    protected fun intercept(m: Executable, hooker: XposedInterface.Hooker) {
        module.hook(m).intercept(hooker)
        hooked++
    }

    /** 按名字调 [obj] 自己或父类上声明的方法（含 private）。 */
    protected fun call(obj: Any, name: String, vararg args: Any?): Any? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            c.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }?.let {
                return it.apply { isAccessible = true }.invoke(obj, *args)
            }
            c = c.superclass
        }
        throw NoSuchMethodException("${obj.javaClass.name}.$name")
    }

    protected fun field(obj: Any, name: String): Any? = findField(obj.javaClass, name).get(obj)

    protected fun setField(obj: Any, name: String, value: Any?) {
        findField(obj.javaClass, name).set(obj, value)
    }

    private fun findField(cls: Class<*>, name: String): Field {
        var c: Class<*>? = cls
        while (c != null) {
            runCatching { return c.getDeclaredField(name).apply { isAccessible = true } }
            c = c.superclass
        }
        throw NoSuchFieldException("${cls.name}.$name")
    }

    companion object {
        const val STRING = "java.lang.String"
        const val CONTEXT = "android.content.Context"
        const val LIST = "java.util.List"
        const val MAP = "java.util.Map"
        const val INT = "int"
        const val BOOLEAN = "boolean"
    }
}
