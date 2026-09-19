import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val pluginName = "VoxyQuestBridge"
val pluginPackageName = "dev.voxyquest.bridge"
val addonDir = rootProject.projectDir.resolve("../../launcher/addons/$pluginName")
val microsoftClientId = providers.gradleProperty("voxyquestMicrosoftClientId")
    .orElse(providers.environmentVariable("VOXYQUEST_MICROSOFT_CLIENT_ID"))
    .orElse("d17a73a2-707c-40f5-8c90-d3eda0956f10")
    .get()

android {
    namespace = pluginPackageName
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 29
        manifestPlaceholders["godotPluginName"] = pluginName
        manifestPlaceholders["godotPluginPackageName"] = pluginPackageName
        buildConfigField("String", "GODOT_PLUGIN_NAME", "\"$pluginName\"")
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"$microsoftClientId\"")
        setProperty("archivesBaseName", pluginName)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("org.godotengine:godot:4.7.2.stable")
    compileOnly(project(":pojlib"))
}

val syncToGodot by tasks.registering(Copy::class) {
    dependsOn("assembleDebug", "assembleRelease", ":pojlib:assembleDebug", ":pojlib:assembleRelease")
    into(addonDir)
    from("export_scripts_template")
    from("build/outputs/aar/$pluginName-debug.aar") { into("bin/debug") }
    from("build/outputs/aar/$pluginName-release.aar") { into("bin/release") }
    from(project(":pojlib").layout.buildDirectory.file("outputs/aar/PojlibRuntime-debug.aar")) { into("bin/debug") }
    from(project(":pojlib").layout.buildDirectory.file("outputs/aar/PojlibRuntime-release.aar")) { into("bin/release") }
}
