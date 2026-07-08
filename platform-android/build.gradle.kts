plugins {
    id("com.android.application")
    kotlin("android")
}

// Podpisywanie Release - klucz generowany lokalnie przez release.sh (KEYSTORE.md), NIGDY
// commitowany. Ścieżka/alias nadpisywalne zmiennymi środowiskowymi dla osób z własnym kluczem.
// Brak pliku keystore -> signingConfig "release" pozostaje pusty -> assembleRelease nadal
// się kompiluje (dowód poprawności kodu), ale wynikowy APK jest niepodpisany i niemożliwy do
// zainstalowania - patrz KEYSTORE.md.
val releaseKeystoreFile = rootProject.file(System.getenv("MIDOMAIL_KEYSTORE_FILE") ?: "keys/midomail-release.jks")
val releaseKeystorePassword = System.getenv("MIDOMAIL_KEYSTORE_PASSWORD") ?: ""
val releaseKeyAlias = System.getenv("MIDOMAIL_KEY_ALIAS") ?: "midomail"

android {
    namespace = "midomail.platform.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "midomail.platform.android"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "V2.0 powered by Ci"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystoreFile.exists()) {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (releaseKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Jakarta Mail/Angus (Iteracja 2.0, teraz wchodzące przez :adapter-email) pakują własne pliki
    // META-INF/LICENSE.md/NOTICE.md - kolidują przy scalaniu do jednego APK, nie mają wpływu na
    // działanie aplikacji.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md"
            )
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adapter-gsm"))
    // Pełny łańcuch SMS/MMS <-> Email (Iteracja 3.13).
    implementation(project(":adapter-email"))
    // Kanał powiadomień Webhook (Iteracja 4.13).
    implementation(project(":notification-webhook"))

    // FileProvider - udostępnienie zapisywalnego content:// Uri dla
    // SmsManager.downloadMultimediaMessage (Iteracja 3.10).
    implementation("androidx.core:core:1.13.1")

    testImplementation(kotlin("test-junit5"))

    // Testy instrumentowane - uruchamiane na rzeczywistym urządzeniu (connectedDebugAndroidTest),
    // nie Robolectric (50-Quality/50-Testy.md, §5: preferowane testy na czystym Kotlinie z
    // atrapami; kod dotykający realnego API Android weryfikowany na urządzeniu).
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
