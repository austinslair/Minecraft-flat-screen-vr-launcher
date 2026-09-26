package dev.voxyquest.bridge

import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarFile
import org.json.JSONArray
import org.json.JSONObject
import pojlib.install.VoxyQuestInstaller
import pojlib.util.Constants

/** Modrinth v2 client. All calls and file writes happen on a worker thread. */
internal object ModrinthClient {
    private const val API = "https://api.modrinth.com/v2"
    private const val MAX_METADATA = 1024 * 1024
    private const val MAX_JAR = 256L * 1024 * 1024
    private const val MAX_DEPENDENCIES = 20
    private val projectId = Regex("[A-Za-z0-9]{8,16}")
    private val safeFilename = Regex("[A-Za-z0-9][A-Za-z0-9._+() -]{0,180}\\.jar", RegexOption.IGNORE_CASE)

    fun search(query: String, gameVersion: String, sort: String, category: String, loader: String): JSONArray {
        require(query.length <= 80 && gameVersion.matches(Regex("[0-9.]+")))
        require(loader in setOf("fabric", "neoforge"))
        require(sort in setOf("relevance", "downloads", "follows", "newest", "updated"))
        require(category in setOf("all", "optimization", "utility", "adventure", "library", "decoration"))
        val facets = JSONArray().put(JSONArray().put("project_type:mod"))
            .put(JSONArray().put("categories:$loader"))
            .put(JSONArray().put("versions:$gameVersion"))
        if (category != "all") facets.put(JSONArray().put("categories:$category"))
        val url = "$API/search?query=${Uri.encode(query)}&facets=${Uri.encode(facets.toString())}&index=$sort&limit=20"
        val hits = JSONObject(read(url)).getJSONArray("hits")
        val results = JSONArray()
        for (i in 0 until hits.length()) {
            val hit = hits.getJSONObject(i)
            val id = hit.optString("project_id")
            if (!projectId.matches(id)) continue
            results.put(JSONObject().put("id", id).put("title", hit.optString("title"))
                .put("description", hit.optString("description"))
                .put("author", hit.optString("author"))
                .put("icon_url", hit.optString("icon_url"))
                .put("downloads", hit.optLong("downloads")))
        }
        return results
    }

    fun install(instanceName: String, project: String, progress: (String) -> Unit): String {
        require(projectId.matches(project)) { "Invalid Modrinth project." }
        val instance = VoxyQuestInstaller.readRegistry().toArray().firstOrNull { it.instanceName == instanceName }
            ?: error("Instance no longer exists.")
        val gameVersion = instance.versionName ?: error("Instance has no Minecraft version.")
        val loader = instance.loaderId()
        val root = File(Constants.USER_HOME, "instances").canonicalFile
        val game = File(instance.gameDir ?: error("Instance has no folder.")).canonicalFile
        check(game != root && game.toPath().startsWith(root.toPath())) { "Invalid instance folder." }
        val mods = File(game, "mods").canonicalFile
        check(mods.parentFile == game && mods.isDirectory) { "Instance mods folder is unavailable." }

        val versions = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        fun resolve(id: String, pinnedVersion: String? = null) {
            check(versions.size < MAX_DEPENDENCIES) { "Too many required dependencies." }
            val version = if (pinnedVersion != null) {
                require(projectId.matches(pinnedVersion))
                JSONObject(read("$API/version/$pinnedVersion"))
            } else {
                require(projectId.matches(id))
                val list = JSONArray(read("$API/project/$id/version?loaders=%5B%22$loader%22%5D&game_versions=%5B%22$gameVersion%22%5D&include_changelog=false"))
                check(list.length() > 0) { "No $loader build for Minecraft $gameVersion." }
                // The API may return featured versions first; choose the most recently published
                // compatible build regardless of how the response is ordered.
                (0 until list.length()).map { list.getJSONObject(it) }
                    .maxByOrNull { it.optString("date_published") }!!
            }
            val versionId = version.getString("id")
            if (!seen.add(versionId)) return
            check(version.getJSONArray("game_versions").let { values ->
                (0 until values.length()).any { values.getString(it) == gameVersion }
            } && version.getJSONArray("loaders").let { values ->
                (0 until values.length()).any { values.getString(it) == loader }
            }) {
                "A required mod does not support this Minecraft version."
            }
            for (i in 0 until version.optJSONArray("dependencies").let { it?.length() ?: 0 }) {
                val dep = version.getJSONArray("dependencies").getJSONObject(i)
                if (dep.optString("dependency_type") != "required") continue
                val depVersion = dep.optString("version_id")
                val depProject = dep.optString("project_id")
                if (depVersion.isNotBlank()) resolve(depProject, depVersion)
                else if (depProject.isNotBlank()) resolve(depProject)
                else error("A required dependency cannot be downloaded automatically.")
            }
            versions.add(version)
        }
        resolve(project)
        val requestedVersionId = versions.last().getString("id")

        val staged = ArrayList<Pair<File, File>>()
        val installedIds = HashSet<String>()
        mods.listFiles().orEmpty().filter { it.isFile && it.extension.equals("jar", true) }.forEach { file ->
            runCatching { JarFile(file).use { jar -> modId(jar, loader)?.let(installedIds::add) } }
        }
        try {
            for (version in versions) {
                val files = version.getJSONArray("files")
                val chosen = (0 until files.length()).map { files.getJSONObject(it) }
                    .firstOrNull { it.optBoolean("primary") && it.optString("filename").endsWith(".jar", true) }
                    ?: (0 until files.length()).map { files.getJSONObject(it) }
                        .firstOrNull { it.optString("filename").endsWith(".jar", true) }
                    ?: error("A mod version has no JAR.")
                val filename = chosen.getString("filename")
                check(safeFilename.matches(filename)) { "Invalid mod filename." }
                val destination = File(mods, filename)
                if (destination.exists() && version.getString("id") != requestedVersionId) continue
                check(!destination.exists()) { "$filename already exists. Existing mods were not replaced." }
                val sha512 = chosen.getJSONObject("hashes").getString("sha512")
                check(sha512.matches(Regex("[a-fA-F0-9]{128}"))) { "Mod checksum missing." }
                val url = URL(chosen.getString("url"))
                check(url.protocol == "https" && url.host == "cdn.modrinth.com") { "Unsupported mod download host." }
                progress("Downloading $filename…")
                val temp = File.createTempFile("modrinth-", ".jar", mods)
                staged.add(temp to destination)
                download(url, temp, sha512)
                JarFile(temp).use { jar ->
                    val id = modId(jar, loader) ?: error("$filename is not a $loader mod.")
                    if (!installedIds.add(id)) {
                        if (version.getString("id") == requestedVersionId)
                            error("$id is already installed. Existing mods were not replaced.")
                        staged.removeAt(staged.lastIndex)
                        temp.delete()
                    }
                }
            }
            val count = staged.size
            staged.forEach { (temp, destination) -> Files.move(temp.toPath(), destination.toPath()) }
            return "Installed $count mod file(s) for Minecraft $gameVersion."
        } finally {
            staged.forEach { (temp, _) -> temp.delete() }
        }
    }

    internal fun modId(jar: JarFile, loader: String): String? {
        val descriptor = jar.getJarEntry(if (loader == "neoforge")
            "META-INF/neoforge.mods.toml" else "fabric.mod.json") ?: return null
        jar.getInputStream(descriptor).use { stream ->
            val bytes = stream.readNBytes(MAX_METADATA + 1)
            check(bytes.size <= MAX_METADATA) { "Mod metadata is too large." }
            val metadata = String(bytes, Charsets.UTF_8)
            if (loader == "neoforge") {
                // NeoForge's descriptor is TOML; accept a declared mod ID in a [[mods]] block.
                val block = metadata.substringAfter("[[mods]]", "")
                return Regex("(?m)^\\s*modId\\s*=\\s*['\"]([a-z0-9_.-]+)['\"]")
                    .find(block)?.groupValues?.get(1)
            }
            return JSONObject(metadata).optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun read(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "VoxyQuest/0.6 (github.com/austinslair/Minecraft-flat-screen-vr-launcher)")
        try {
            check(connection.responseCode == 200) { "Modrinth request failed (${connection.responseCode})." }
            connection.inputStream.use { stream ->
                val bytes = stream.readNBytes(MAX_METADATA + 1)
                check(bytes.size <= MAX_METADATA) { "Modrinth response is too large." }
                return String(bytes, Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }

    private fun download(url: URL, file: File, expected: String) {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false
        try {
            check(connection.responseCode == 200) { "Mod download failed (${connection.responseCode})." }
            val digest = MessageDigest.getInstance("SHA-512")
            var total = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val bytes = ByteArray(65536)
                    while (true) {
                        val count = input.read(bytes)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_JAR) { "Mod file is too large." }
                        digest.update(bytes, 0, count)
                        output.write(bytes, 0, count)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(expected, true)) { "Mod checksum did not match." }
        } finally { connection.disconnect() }
    }
}
