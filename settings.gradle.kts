pluginManagement {
    repositories {
        // 保留既有插件版本，优先使用可达的 Maven 镜像，便于本地和持续集成重复验证。
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
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
        // 与插件仓库使用同一镜像来源，官方仓库保留为回退，避免环境 TLS 问题阻断构建。
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
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
