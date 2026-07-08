plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "midomail.adapter.gsm"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":domain"))

    testImplementation(kotlin("test-junit5"))

    // Testy instrumentowane - uruchamiane na rzeczywistym urządzeniu (connectedDebugAndroidTest),
    // nie Robolectric (50-Quality/50-Testy.md, §5).
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
