plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fuckdqd.hook"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // 打进 APK 会被 LSPosed 拒绝加载
    compileOnly(libs.libxposed.api)
    // 换版本后按特征找混淆过的类 / 方法（带 native 库 libdexkit.so）
    implementation(libs.dexkit)
}
