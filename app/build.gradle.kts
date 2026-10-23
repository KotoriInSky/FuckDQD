import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val moduleVersion = libs.versions.fuckdqd.get()
val versionParts = moduleVersion.split(".")
require(versionParts.size == 3 && versionParts.all { it.toIntOrNull() != null }) {
    "版本号必须写成 major.minor.patch（当前是 \"$moduleVersion\"）"
}

android {
    namespace = "io.github.kotoriinsky.fuckdqd"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.kotoriinsky.fuckdqd"
        minSdk = 26
        targetSdk = 36
        versionCode = versionParts[0].toInt() * 10000 + versionParts[1].toInt() * 100 + versionParts[2].toInt()
        versionName = moduleVersion
        // DexKit 的 native 库只留真机会用到的两个 ABI
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // native 库不压缩、不解压：LSPosed 从模块 APK 里直接按 lib/<abi>/ 加载 libdexkit.so
    packaging { jniLibs { useLegacyPackaging = false } }

    // 正式签名：项目根目录的 keystore.properties（不进 git）里写 storeFile / storePassword / keyAlias / keyPassword。
    // 没有这个文件（别人 clone 下来编）就退回 debug 签名
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // module.prop 等 META-INF 资源的引用者 R8 看不见，不裁资源
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
