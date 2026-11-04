plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("assets"))
    }
}

dependencies {
    implementation(project(":contracts"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.serialization.json)
}
