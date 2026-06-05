import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    `maven-publish`
}

group = "com.assinafy"
// Honor a -Pversion override (used by CI to publish unique snapshot coordinates) and fall back
// to the released version. Gradle sets project.version to "unspecified" when no -Pversion is given.
version = (findProperty("version") as String?)?.takeIf { it.isNotBlank() && it != "unspecified" } ?: "1.0.3"

val okHttpVersion = "4.12.0" // last 4.x release; 5.x is a separate migration
val gsonVersion = "2.11.0"
val coroutinesVersion = "1.9.0"
val junitVersion = "5.11.4"
val junitPlatformVersion = "1.11.4"
val mockWebServerVersion = "4.12.0"
val assertjVersion = "3.27.3"

android {
    namespace = "com.assinafy.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        // No targetSdk here: a library does not own it — the consuming app sets its own targetSdkVersion.
        // consumerProguardFiles ships the Gson keep rules INSIDE the published AAR so they are applied
        // during the consuming app's R8 pass — without this, minified release apps strip model fields.
        consumerProguardFiles("proguard-rules.pro")
        // proguardFiles only matters if this library is ever self-minified (it is not by default);
        // kept for completeness and parity with the consumer rules.
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "assinafy-android-sdk"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/assinafy/mobile-android-sdk")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$mockWebServerVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Forward live-integration credentials (when present) to the forked test JVM so the opt-in
    // LiveIntegrationTest can read them via System.getenv. Absent vars leave the tests disabled.
    listOf("ASSINAFY_API_KEY", "ASSINAFY_ACCOUNT_ID", "ASSINAFY_BASE_URL", "ASSINAFY_LIVE_WRITES")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
}
