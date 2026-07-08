pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "midoMAIL2"

include(":domain")
include(":adapter-email")
include(":adapter-gsm")
include(":platform-android")
include(":notification-webhook")
include(":adapter-rest")
include(":adapter-cli")
include(":ui-web")
include(":adapter-websocket")
include(":platform-jvm")
