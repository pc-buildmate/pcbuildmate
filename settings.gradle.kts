pluginManagement {
    repositories {
        // 国内直连优先；官方仓库作为回退（CI / 海外机器）。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "pc-buildmate"

include(
    ":contracts",
    ":contracts-test",
    ":domain:build",
    ":domain:rules",
    ":domain:replacement",
    ":domain:recommend",
    ":domain:compare",
    ":domain:state",
    ":data",
    ":feature",
    ":ui",
    ":app",
    ":testing",
    ":test-oracle",
)
