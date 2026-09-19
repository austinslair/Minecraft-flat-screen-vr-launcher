import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val pluginName = "VoxyQuestBridge"
val pluginPackageName = "dev.voxyquest.bridge"
val addonDir = rootProject.projectDir.resolve("../../launcher/addons/$pluginName")

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
}

val syncToGodot by tasks.registering(Copy::class) {
    dependsOn("assembleDebug", "assembleRelease")
    into(addonDir)
    from("export_scripts_template")
    from("build/outputs/aar/$pluginName-debug.aar") { into("bin/debug") }
    from("build/outputs/aar/$pluginName-release.aar") { into("bin/release") }
}
