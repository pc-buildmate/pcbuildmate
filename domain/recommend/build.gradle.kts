plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":contracts"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(project(":domain:rules"))
}
