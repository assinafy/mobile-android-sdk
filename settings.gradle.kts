import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.environmentVariable("ASSINAFY_REMOTE_PACKAGE_VERIFY").orNull != "true") {
            mavenLocal {
                content {
                    includeModule("com.assinafy", "assinafy-android-sdk")
                }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "assinafy-android-sdk"
include(":sdk")
include(":consumer-smoke")
