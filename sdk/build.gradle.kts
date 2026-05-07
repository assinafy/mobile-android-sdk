plugins {
    id("com.android.library")
    kotlin("android")
}

group = "com.assinafy"
version = "1.0.0"

val okHttpVersion = "4.12.0"
val gsonVersion = "2.10.1"
val coroutinesVersion = "1.8.1"
val junitVersion = "5.10.2"
val mockWebServerVersion = "4.12.0"
val assertjVersion = "3.25.3"

android {
    namespace = "com.assinafy.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}