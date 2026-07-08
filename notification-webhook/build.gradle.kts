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
