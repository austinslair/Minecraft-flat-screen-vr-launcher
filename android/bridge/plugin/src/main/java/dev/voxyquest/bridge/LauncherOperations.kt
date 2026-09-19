package dev.voxyquest.bridge

import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject
import pojlib.install.VoxyQuestInstaller
import java.util.concurrent.Executors

/** One install at a time, independently of Godot rendering and browser focus. */
object LauncherOperations {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var state = "idle"
    @Volatile private var message = ""
    @Volatile private var installedName = ""

    @Synchronized
    fun install(activity: Activity, name: String, version: String): Boolean {
        if (state == "installing" || MinecraftGameActivity.isRunning) return false
        try { VoxyQuestInstaller.directoryName(name) } catch (_: IllegalArgumentException) {
            state = "error"
            message = "Use 1–48 letters, numbers, spaces, underscores or hyphens."
            return false
        }
        state = "installing"
        message = "Preparing installation…"
        installedName = ""
        worker.execute {
            try {
                val result = VoxyQuestInstaller.install(activity, name, version) { message = it }
                installedName = result.instanceName
                message = "Installed ${result.instanceName}"
                state = "installed"
            } catch (failure: Exception) {
                message = "Installation failed during: $message Check your connection and free space, then retry."
                state = "error"
            }
        }
        return true
    }

    fun isBusy(): Boolean = state == "installing"

    fun snapshot(): String = JSONObject().put("state", state)
        .put("message", message).put("installed_name", installedName).toString()

    fun versions(activity: Activity): String = runCatching {
        val versions = JSONArray()
        VoxyQuestInstaller.catalog(activity).versions.forEach { versions.put(it.name) }
        versions.toString()
    }.getOrDefault("[]")
}
