import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fuckdqd.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$stamp\"")
        buildConfigField("String", "MODULE_VERSION", "\"${libs.versions.fuckdqd.get()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // 只用 Keys；:hook 不依赖 Compose，反向依赖是安全的
    implementation(project(":hook"))
    implementation(libs.libxposed.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
}
