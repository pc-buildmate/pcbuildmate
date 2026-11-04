pluginManagement {
    repositories {
        // 本机在国内，阿里云镜像优先；GitHub Actions 在海外，直接用官方仓库，
        // 避免 runner 访问阿里云超时/403（CI=true 由 GitHub 自动注入）。
        if (System.getenv("CI") != "true") {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") != "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
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
