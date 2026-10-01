pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}

rootProject.name = "VoxyQuestBridge"
include(":plugin", ":pojlib")
project(":pojlib").projectDir = file("../../third_party/Pojlib")
include(":mobileglues")
project(":mobileglues").projectDir = file("../../flat_screen/renderer/TGS-Renderer-Quest-0.11-source")
