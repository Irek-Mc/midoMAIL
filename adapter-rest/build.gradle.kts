plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":domain"))
    // ADR-0024-Biblioteka-JSON.md - drugi świadomy wyjątek od minimalizacji zależności (po
    // jakarta.mail, Faza 2). Wyłącznie tutaj - nigdy w :domain.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // ADR-0032-Konfiguracja-YAML-Pelna.md - trzeci świadomy wyjątek od minimalizacji zależności.
    // kaml jest zbudowany na kotlinx.serialization (już zaakceptowanym powyżej) - naturalne
    // rozszerzenie tego samego frameworka o format YAML, nie nowy, niepowiązany toolchain.
    // Wyłącznie tutaj - nigdy w :domain.
    implementation("com.charleskorn.kaml:kaml:0.61.0")

    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

// Harness ręcznej weryfikacji end-to-end (Iteracja 5.17, docs/faza5-weryfikacja.md - do utworzenia
// w tamtej iteracji). Osobny source set: nie jest częścią głównego jara produkcyjnego, nie
// uruchamia się automatycznie w `gradle test`/CI, nie wpływa na Public API - wyłącznie narzędzie
// do ręcznej walidacji (ADR-0022-Moduly-Adapter-REST-CLI.md).
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
    description = "Ręczny harness weryfikacji Adaptera REST (docs/faza5-weryfikacja.md) - NIE jest kodem produkcyjnym"
    classpath = sourceSets["manualVerification"].runtimeClasspath
    mainClass.set("midomail.adapter.rest.manualverification.RestVerificationHarnessKt")
    standardInput = System.`in`
    workingDir = rootProject.projectDir
}

// Iteracja 6.29 - harness dodatków Fazy 6 (Alerty/Komunikaty/Monitoring/Historia routingu/
// Konfiguracja adaptera/Konfiguracja YAML/RBAC/Dashboard/Logi/Audyt). Ten sam source set,
// osobny task - nie zastępuje harnessu Fazy 5, go uzupełnia.
tasks.register<JavaExec>("runManualVerificationFaza6") {
    group = "verification"
    description = "Ręczny harness weryfikacji dodatków Fazy 6 (docs/faza6-weryfikacja.md) - NIE jest kodem produkcyjnym"
    classpath = sourceSets["manualVerification"].runtimeClasspath
    mainClass.set("midomail.adapter.rest.manualverification.Faza6VerificationHarnessKt")
    standardInput = System.`in`
    workingDir = rootProject.projectDir
}
