plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // Celowo BRAK zależności na :domain (ADR-0033-Modul-UI-Web.md) - :ui-web jest wyłącznie
    // serwerem plików statycznych; komunikacja z Adapterem REST wyłącznie przez fetch() po
    // stronie przeglądarki, nigdy przez typy Kotlin po stronie serwera.
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Uruchamia serwer statyczny :ui-web (domyślnie port 8081)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("midomail.ui.web.StaticFileServerKt")
    standardInput = System.`in`
}
