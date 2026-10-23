# java_init.list 写的是入口类名字符串，不能让 R8 改名
-keep class io.github.kotoriinsky.fuckdqd.hook.HookEntry { *; }

# libxposed api 是 compileOnly，R8 看不见 Hooker 接口，会把 lambda 的 intercept 改名/删掉，
# 结果是 hook 静默失效（fp_verify 实测踩过）
-dontobfuscate
-keepclassmembers class ** {
    public ** intercept(...);
}
-dontwarn io.github.libxposed.api.**
