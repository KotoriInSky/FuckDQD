package com.fuckdqd.hook

/**
 * 前后端共用的配置契约。设置界面通过 libxposed service 写 RemotePreferences，
 * hook 侧用 getRemotePreferences(GROUP) 读同一份。
 */
object Keys {
    const val GROUP = "settings"
    const val TARGET_PACKAGE = "com.dongqiudi.news"

    const val SPLASH = "block_splash"
    const val FEED = "block_feed"
    const val POPUP = "block_popup"
    const val BANNER = "block_banner"

    /** 开关缺省都是开：装上模块就是要去广告的。 */
    const val DEFAULT_ON = true

    val ALL = listOf(SPLASH, FEED, POPUP, BANNER)
}
