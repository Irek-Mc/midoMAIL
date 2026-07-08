plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adapter-email"))
    implementation(project(":adapter-websocket"))
    implementation(project(":adapter-rest"))
    implementation(project(":adapter-cli"))
    implementation(project(":ui-web"))
    implementation(project(":notification-webhook"))

    testImplementation(kotlin("test-junit5"))
    // Weryfikacja end-to-end (Iteracja 7.8) - serwer SMTP/IMAP lokalny, już akceptowana
    // zależność testowa :adapter-email od Fazy 2 (docs/faza2-weryfikacja-gmail.md).
    testImplementation("com.icegreen:greenmail:2.1.0")
}

application {
    mainClass.set("midomail.platform.jvm.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
