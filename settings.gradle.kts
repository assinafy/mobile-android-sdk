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
        if (providers.environmentVariable("ASSINAFY_REMOTE_PACKAGE_VERIFY").orNull == "true") {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/assinafy/mobile-android-sdk")
                credentials {
                    username = providers.environmentVariable("GITHUB_ACTOR").orNull
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull
                }
                content {
                    includeModule("com.assinafy", "assinafy-android-sdk")
                }
            }
        } else {
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
