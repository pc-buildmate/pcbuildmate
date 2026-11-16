plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "pcbuildmate.ui"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31
    }

    buildFeatures {
        compose = true
    }

    // Compose 语义测试跑在 JVM 上（Robolectric），需要资源与 manifest。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric 运行时按需下载 android-all；默认仓库在国内不可达，指到阿里云镜像。
            all { it.systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":contracts"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
