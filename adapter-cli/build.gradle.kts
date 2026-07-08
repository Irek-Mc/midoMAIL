plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":domain"))

    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

// Harness ręcznej weryfikacji end-to-end (Iteracja 5.17). Zobacz komentarz w
// adapter-rest/build.gradle.kts - ta sama konwencja (ADR-0022-Moduly-Adapter-REST-CLI.md).
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
    description = "Ręczny harness weryfikacji Adaptera CLI (docs/faza5-weryfikacja.md) - NIE jest kodem produkcyjnym"
    classpath = sourceSets["manualVerification"].runtimeClasspath
    mainClass.set("midomail.adapter.cli.manualverification.CliVerificationHarnessKt")
    standardInput = System.`in`
    workingDir = rootProject.projectDir
}
