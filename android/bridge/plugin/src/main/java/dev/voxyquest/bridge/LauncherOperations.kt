package dev.voxyquest.bridge

import android.app.Activity
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import pojlib.install.VoxyQuestInstaller
import pojlib.util.Constants
import pojlib.util.GsonUtils
import pojlib.util.json.MinecraftInstances

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

    @Synchronized
    fun renameInstance(oldName: String, newName: String): Boolean {
        if (state == "installing" || MinecraftGameActivity.isRunning) return false
        return runCatching {
            val cleanName = newName.trim()
            val desiredDirectoryName = VoxyQuestInstaller.directoryName(cleanName)
            val registry = VoxyQuestInstaller.readRegistry()
            val target = registry.toArray().firstOrNull { it.instanceName == oldName } ?: return false
            val duplicate = registry.toArray().any {
                it !== target && it.instanceName != null &&
                    it.instanceName.lowercase(Locale.ROOT).replace(' ', '_') == desiredDirectoryName
            }
            if (duplicate) {
                message = "An instance with that name already exists."
                state = "error"
                return false
            }

            val instancesRoot = File(Constants.USER_HOME, "instances").canonicalFile
            val oldDirectory = File(target.gameDir ?: return false).canonicalFile
            val newDirectory = File(instancesRoot, desiredDirectoryName).canonicalFile
            if (!oldDirectory.toPath().startsWith(instancesRoot.toPath()) ||
                !newDirectory.toPath().startsWith(instancesRoot.toPath())) return false
            if (!oldDirectory.exists()) return false
            if (oldDirectory != newDirectory && newDirectory.exists()) {
                message = "The destination instance folder already exists."
                state = "error"
                return false
            }

            val previousName = target.instanceName
            val previousGameDir = target.gameDir
            var moved = false
            try {
                if (oldDirectory != newDirectory) {
                    moveDirectory(oldDirectory, newDirectory)
                    moved = true
                }
                target.instanceName = cleanName
                target.gameDir = newDirectory.path
                saveRegistry(registry)
            } catch (failure: Exception) {
                target.instanceName = previousName
                target.gameDir = previousGameDir
                if (moved && newDirectory.exists() && !oldDirectory.exists()) {
                    runCatching { moveDirectory(newDirectory, oldDirectory) }
                }
                throw failure
            }

            installedName = cleanName
            message = "Renamed $oldName to $cleanName"
            state = "idle"
            true
        }.getOrElse {
            if (state != "error") {
                message = "Could not rename the instance."
                state = "error"
            }
            false
        }
    }

    @Synchronized
    fun removeInstance(name: String): Boolean {
        if (state == "installing" || MinecraftGameActivity.isRunning) return false
        return runCatching {
            val registry = VoxyQuestInstaller.readRegistry()
            val target = registry.toArray().firstOrNull { it.instanceName == name } ?: return false
            val instancesRoot = File(Constants.USER_HOME, "instances").canonicalFile
            val gameDirectory = File(target.gameDir ?: return false).canonicalFile
            if (!gameDirectory.toPath().startsWith(instancesRoot.toPath())) return false

            registry.instances = registry.toArray().filter { it !== target }.toTypedArray()
            saveRegistry(registry)

            var fullyDeleted = true
            if (gameDirectory.exists()) {
                gameDirectory.walkBottomUp().forEach { file ->
                    if (file.exists() && !file.delete()) fullyDeleted = false
                }
            }
            if (installedName == name) installedName = ""
            message = if (fullyDeleted) "Removed $name" else "Removed $name; some files could not be deleted."
            state = "idle"
            true
        }.getOrElse {
            message = "Could not remove the instance."
            state = "error"
            false
        }
    }

    fun mods(name: String): String = runCatching {
        val result = JSONObject().put("available", true).put("mods", JSONArray()).put("error", "")
        val instance = VoxyQuestInstaller.readRegistry().toArray().firstOrNull { it.instanceName == name }
            ?: return JSONObject().put("available", true).put("mods", JSONArray())
                .put("error", "Instance not found").toString()
        val modsDirectory = File(instance.gameDir ?: "", "mods")
        val mods = JSONArray()
        if (modsDirectory.isDirectory) {
            modsDirectory.listFiles()
                ?.filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
                ?.sortedBy { it.name.lowercase(Locale.ROOT) }
                ?.forEach { mods.put(it.name) }
        }
        result.put("mods", mods).toString()
    }.getOrElse {
        JSONObject().put("available", false).put("mods", JSONArray())
            .put("error", "Could not read instance mods").toString()
    }

    private fun moveDirectory(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun saveRegistry(registry: MinecraftInstances) {
        val destination = File(Constants.USER_HOME, "instances.json")
        destination.parentFile?.mkdirs()
        val temp = File.createTempFile("instances-", ".json", destination.parentFile)
        try {
            Files.write(
                temp.toPath(),
                GsonUtils.GLOBAL_GSON.toJson(registry).toByteArray(StandardCharsets.UTF_8),
            )
            try {
                Files.move(
                    temp.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp.toPath())
        }
    }
}
