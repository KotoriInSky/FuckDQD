plugins {
    alias(libs.plugins.android.application)
}

val moduleVersion = libs.versions.fuckdqd.get()
val versionParts = moduleVersion.split(".")
require(versionParts.size == 3 && versionParts.all { it.toIntOrNull() != null }) {
    "版本号必须写成 major.minor.patch（当前是 \"$moduleVersion\"）"
}

android {
    namespace = "com.fuckdqd"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fuckdqd"
        minSdk = 26
        targetSdk = 36
        versionCode = versionParts[0].toInt() * 10000 + versionParts[1].toInt() * 100 + versionParts[2].toInt()
        versionName = moduleVersion
        // DexKit 的 native 库只留真机会用到的两个 ABI
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // native 库不压缩、不解压：LSPosed 从模块 APK 里直接按 lib/<abi>/ 加载 libdexkit.so
    packaging { jniLibs { useLegacyPackaging = false } }

    buildTypes {
        release {
            isMinifyEnabled = true
            // module.prop 等 META-INF 资源的引用者 R8 看不见，不裁资源
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":hook"))
    implementation(project(":ui"))
    // 不打进 APK，只给 R8 当 library 看：:hook 里的 compileOnly 传不到这里，R8 看不见
    // Hooker 接口就会把 lambda 的 intercept 当死代码删掉，运行时 AbstractMethodError
    compileOnly(libs.libxposed.api)
}
