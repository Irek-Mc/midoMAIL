plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":domain"))
    implementation("jakarta.mail:jakarta.mail-api:2.1.3")
    implementation("org.eclipse.angus:angus-mail:2.0.3")

    testImplementation(kotlin("test-junit5"))
    testImplementation("com.icegreen:greenmail:2.1.0")
}

tasks.test {
    useJUnitPlatform()
}

// Harness ręcznej weryfikacji na rzeczywistym Gmailu (docs/faza2-weryfikacja-gmail.md).
// Osobny source set: nie jest częścią głównego jara produkcyjnego, nie uruchamia się
// automatycznie w `gradle test`/CI, nie wpływa na Public API - wyłącznie narzędzie do ręcznej
// walidacji, uruchamiane jawnie przez `gradle :adapter-email:runManualVerification`.
sourceSets {
    create("manualVerification") {
        kotlin.srcDir("src/manualVerification/kotlin")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val manualVerificationImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

tasks.register<JavaExec>("runManualVerification") {
    group = "verification"
    description = "Ręczny harness weryfikacji Gmail (docs/faza2-weryfikacja-gmail.md) - NIE jest kodem produkcyjnym"
    classpath = sourceSets["manualVerification"].runtimeClasspath
    mainClass.set("midomail.adapter.email.manualverification.GmailVerificationHarnessKt")
    standardInput = System.`in`
    // JavaExec dziedziczy katalog roboczy z podprojektu (adapter-email/), nie z korzenia -
    // docs/ leży w korzeniu. Log (VerificationLog) jest defensywny (tworzy katalog sam), ale
    // ścieżka ma być tą samą co w docs/faza2-weryfikacja-gmail.md, nie zagnieżdżoną w adapter-email/.
    workingDir = rootProject.projectDir
}
