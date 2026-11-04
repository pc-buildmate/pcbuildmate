plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pcbuildmate.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("test") {
            resources.srcDir(rootProject.file("assets"))
        }
    }
}

dependencies {
    implementation(project(":contracts"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":contracts-test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
