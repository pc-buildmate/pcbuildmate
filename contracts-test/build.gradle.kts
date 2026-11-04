plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":contracts"))
    api(libs.kotlin.test)
    api(libs.kotlin.test.junit)
    api(libs.junit)
}
