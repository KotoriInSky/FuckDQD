package com.fuckdqd.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuckdqd.hook.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private val adToggles = listOf(
    Triple(Keys.SPLASH, "开屏广告", "冷启动、热启动开屏，以及缓存的开屏素材"),
    Triple(Keys.FEED, "信息流 / 列表广告", "新闻流、视频、圈子、赛事、彩票等列表里的广告"),
    Triple(Keys.POPUP, "弹窗广告", "首页插屏弹窗、网页里触发的弹窗广告"),
    Triple(Keys.BANNER, "横幅 / 详情页广告", "赛事页、直播视频页的广告位，文章转发区下方的广告"),
)

private val bettingToggles = listOf(
    Triple(Keys.BETTING, "推单 / 博彩", "底部「预测」换成「圈子」，隐藏比赛页的「专业版」"),
    Triple(Keys.MATCH_INTEL, "比赛情报 / 专家 / 前瞻", "比赛详情页去掉这三个推单导流标签"),
)

@Composable
fun AppScreen(ctx: Context) {
    var darkMode by remember { mutableStateOf(LocalSettings.darkMode(ctx)) }
    val controller = remember(darkMode) {
        ThemeController(
            when (darkMode) {
                LocalSettings.DarkMode.SYSTEM -> ColorSchemeMode.System
                LocalSettings.DarkMode.LIGHT -> ColorSchemeMode.Light
                LocalSettings.DarkMode.DARK -> ColorSchemeMode.Dark
            },
        )
    }
    MiuixTheme(controller = controller) {
        Scaffold(topBar = { SmallTopAppBar(title = "FuckDQD") }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            ) {
                item { StatusSection(ctx) }
                item { ToggleSection(ctx, "广告开关", adToggles) }
                item { ToggleSection(ctx, "博彩 / 推单", bettingToggles) }
                item { RestartHint() }
                item {
                    SettingsSection(ctx, darkMode) {
                        darkMode = it
                        LocalSettings.setDarkMode(ctx, it)
                    }
                }
                item { AboutSection() }
            }
        }
    }
}

@Composable
private fun StatusSection(ctx: Context) {
    val status by FrameworkBridge.status
    SmallTitle(text = "状态")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val framework = when {
            status.loading -> "正在连接框架…"
            !status.connected -> "未激活：请在 LSPosed 里启用本模块"
            else -> "${status.frameworkName} ${status.frameworkVersion}（API ${status.apiVersion}）"
        }
        ArrowPreference(title = "框架", summary = framework, onClick = null)
        val scope = when {
            !status.connected -> "—"
            status.scopeHasTarget -> "已包含懂球帝"
            else -> "未包含懂球帝，点击请求添加"
        }
        ArrowPreference(
            title = "作用域",
            summary = scope,
            onClick = if (status.connected && !status.scopeHasTarget) {
                { FrameworkBridge.requestScope { err -> toast(ctx, err?.let { "添加失败：$it" } ?: "已添加") } }
            } else {
                null
            },
        )
        ArrowPreference(
            title = "懂球帝版本",
            summary = LocalSettings.targetVersion(ctx)?.let { "$it · hook 点按 8.7.2 (10441) 适配" } ?: "未安装",
            onClick = null,
        )
    }
}

@Composable
private fun ToggleSection(ctx: Context, header: String, items: List<Triple<String, String, String>>) {
    val status by FrameworkBridge.status
    val toggles by FrameworkBridge.toggles
    SmallTitle(text = header)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        items.forEach { (key, title, summary) ->
            SwitchPreference(
                checked = toggles[key] ?: Keys.DEFAULT_ON,
                onCheckedChange = { if (!FrameworkBridge.setToggle(key, it)) toast(ctx, "保存失败：框架未连接") },
                title = title,
                summary = summary,
                enabled = status.connected,
            )
        }
    }
}

@Composable
private fun RestartHint() {
    Text(
        text = "改动在懂球帝下次启动时生效。",
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun SettingsSection(
    ctx: Context,
    darkMode: LocalSettings.DarkMode,
    onDarkModeChange: (LocalSettings.DarkMode) -> Unit,
) {
    var iconHidden by remember { mutableStateOf(LocalSettings.isIconHidden(ctx)) }
    val scope = rememberCoroutineScope()
    SmallTitle(text = "设置")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SwitchPreference(
            checked = iconHidden,
            onCheckedChange = {
                LocalSettings.setIconHidden(ctx, it)
                iconHidden = it
            },
            title = "在桌面隐藏图标",
            summary = "隐藏后可从 LSPosed 管理器打开本页",
        )
        ArrowPreference(
            title = "深色模式",
            summary = darkMode.label,
            onClick = {
                val modes = LocalSettings.DarkMode.entries
                onDarkModeChange(modes[(darkMode.ordinal + 1) % modes.size])
            },
        )
        ArrowPreference(
            title = "重启懂球帝",
            summary = "强制停止懂球帝，下次打开即用新配置（需要 root）",
            onClick = {
                scope.launch {
                    val err = withContext(Dispatchers.IO) { LocalSettings.forceStopTarget() }
                    toast(ctx, err?.let { "失败：$it" } ?: "已停止懂球帝")
                }
            },
        )
    }
}

@Composable
private fun AboutSection() {
    SmallTitle(text = "关于")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column {
            ArrowPreference(title = "版本", summary = BuildConfig.MODULE_VERSION, onClick = null)
            ArrowPreference(title = "构建时间", summary = BuildConfig.BUILD_TIME, onClick = null)
        }
    }
    Spacer(Modifier.height(8.dp))
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
