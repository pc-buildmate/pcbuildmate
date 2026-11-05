plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
        getByName("main") {
            assets.srcDir(rootProject.file("assets"))
        }
        getByName("test") {
            resources.srcDir(rootProject.file("assets"))
        }
    }
}

dependencies {
    implementation(project(":contracts"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(project(":contracts-test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
