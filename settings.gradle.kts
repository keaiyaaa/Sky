pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 阿里云 Maven 镜像（加速依赖下载）
        maven { url = uri("https://mirrors.aliyun.com/nexus/content/groups/public/") }
        // DJI 大疆 SDK Maven 仓库
        maven { url = uri("https://sdkrepo.dji.com/releases") }
    }
}

rootProject.name = "SkyPivotInspection"
include(":app")
