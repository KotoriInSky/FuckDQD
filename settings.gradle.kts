pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FuckDQD"

// :hook 注入懂球帝进程，不能依赖 Compose；:ui 是设置界面；:app 只负责打包和 META-INF/xposed
include(":hook")
include(":ui")
include(":app")
