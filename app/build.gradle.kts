import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // 安装身份用反向域名：io.github.pcbuildmate 对应 pcbuildmate.github.io。
    // 邮箱与包名无关，GitHub 账号用 pcbuildmate@<你的域名> 即可。
    // namespace 只决定 R/BuildConfig 的包，保留 pcbuildmate.* 以减小改动；
    // 两者不必相同：applicationId 决定安装身份。
    namespace = "pcbuildmate.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.pcbuildmate"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0-defense"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply { propsFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":ui"))
    implementation(project(":feature"))
    implementation(project(":data"))
    implementation(project(":domain:rules"))
    implementation(project(":domain:replacement"))
    implementation(project(":domain:compare"))
    implementation(project(":domain:recommend"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
