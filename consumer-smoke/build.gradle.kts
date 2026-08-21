plugins {
    id("com.android.application")
}

val sdkVersion = providers.gradleProperty("version").orElse("2.0.0")

android {
    namespace = "com.assinafy.smoke"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.assinafy.smoke"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.assinafy:assinafy-android-sdk:${sdkVersion.get()}")
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(":sdk:publishReleasePublicationToMavenLocal")
}
