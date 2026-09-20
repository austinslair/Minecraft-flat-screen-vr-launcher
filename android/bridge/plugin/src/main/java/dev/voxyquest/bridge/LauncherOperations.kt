package dev.voxyquest.bridge

import android.app.Activity
import java.io.File
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
        if (isBusy() || MinecraftGameActivity.isRunning) return false
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

    fun isBusy(): Boolean = state == "installing" || state == "importing_mod"

    fun snapshot(): String = JSONObject().put("state", state)
        .put("message", message).put("installed_name", installedName).toString()

    fun versions(activity: Activity): String = runCatching {
        val versions = JSONArray()
        VoxyQuestInstaller.catalog(activity).versions.forEach { versions.put(it.name) }
        versions.toString()
    }.getOrDefault("[]")

    @Synchronized
    fun renameInstance(oldName: String, newName: String): Boolean {
        if (isBusy() || MinecraftGameActivity.isRunning) return false
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
        if (isBusy() || MinecraftGameActivity.isRunning) return false
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

    @Synchronized
    fun importMod(name: String, filename: String, input: java.io.InputStream): String {
        if (isBusy() || MinecraftGameActivity.isRunning) return "Wait for Minecraft and installation to stop."
        if (!filename.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+() -]{0,180}\\.jar", RegexOption.IGNORE_CASE))) {
            return "Choose a mod file ending in .jar."
        }
        val instance = VoxyQuestInstaller.readRegistry().toArray().firstOrNull { it.instanceName == name }
            ?: return "Instance no longer exists."
        val root = File(Constants.USER_HOME, "instances").canonicalFile
        val game = File(instance.gameDir ?: return "Instance has no folder.").canonicalFile
        if (game == root || !game.toPath().startsWith(root.toPath())) return "Invalid instance folder."
        val mods = File(game, "mods").canonicalFile
        if (mods.parentFile != game) return "Invalid mods folder."
        mods.mkdirs()
        val destination = File(mods, filename)
        if (destination.exists()) return "A mod with this filename already exists. It was not replaced."
        val temp = File.createTempFile("import-", ".tmp", mods)
        state = "importing_mod"
        try {
            temp.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 256L * 1024 * 1024) { "Mod is too large" }
                    output.write(buffer, 0, count)
                }
            }
            java.util.jar.JarFile(temp).use { jar ->
                val entry = jar.getJarEntry("fabric.mod.json") ?: return "This is not a Fabric mod JAR."
                val metadata = jar.getInputStream(entry).use { stream ->
                    val bytes = readDescriptor(stream)
                    check(bytes.size <= 1024 * 1024)
                    JSONObject(String(bytes, Charsets.UTF_8))
                }
                val id = metadata.optString("id")
                check(id.isNotBlank())
                for (existing in mods.listFiles().orEmpty().filter { it.extension.equals("jar", true) }) {
                    val sameId = runCatching {
                        java.util.jar.JarFile(existing).use { installed ->
                            val descriptor = installed.getJarEntry("fabric.mod.json")
                            descriptor != null && installed.getInputStream(descriptor).use { stream ->
                                JSONObject(String(readDescriptor(stream), Charsets.UTF_8)).optString("id") == id
                            }
                        }
                    }.getOrDefault(false)
                    if (sameId) return "This mod is already installed. Duplicate mod IDs cannot be added."
                }
            }
            Files.move(temp.toPath(), destination.toPath())
            return "Added $filename. Use mods compatible with Minecraft ${instance.versionName}; dependencies may be required."
        } finally {
            temp.delete()
            state = "idle"
        }
    }

    private fun readDescriptor(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(output.size() + count <= 1024 * 1024) { "Mod metadata is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
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
            GsonUtils.objectToJsonFile(temp.path, registry)
            check(temp.length() > 0L) { "Could not serialize instance registry" }
            check(GsonUtils.jsonFileToObject(temp.path, MinecraftInstances::class.java) != null) {
                "Could not verify instance registry"
            }
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
