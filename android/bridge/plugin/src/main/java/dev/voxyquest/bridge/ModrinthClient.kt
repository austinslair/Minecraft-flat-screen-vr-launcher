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
    private val safeFilename = Regex("[A-Za-z0-9][A-Za-z0-9._+()\\[\\], -]{0,180}\\.jar", RegexOption.IGNORE_CASE)
    private const val SEARCH_CHECKS = 6

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
        val candidates = (0 until hits.length()).map { hits.getJSONObject(it) }
            .filter { projectId.matches(it.optString("project_id")) }
        // Search facets match per project, not per build: a mod with a NeoForge build for this
        // version and a Fabric build for another one still matches. Only list mods that have a
        // build for this loader and version, since the rest can only fail to install.
        val pool = java.util.concurrent.Executors.newFixedThreadPool(SEARCH_CHECKS)
        val installable = try {
            candidates.map { hit -> pool.submit<Boolean> { hasBuild(hit.getString("project_id"), loader, gameVersion) } }
                .map { runCatching { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }.getOrDefault(true) }
        } finally { pool.shutdownNow() }
        val results = JSONArray()
        for ((index, hit) in candidates.withIndex()) {
            if (!installable[index]) continue
            val id = hit.getString("project_id")
            results.put(JSONObject().put("id", id).put("title", hit.optString("title"))
                .put("description", hit.optString("description"))
                .put("author", hit.optString("author"))
                .put("icon_url", hit.optString("icon_url"))
                .put("downloads", hit.optLong("downloads"))
                .put("follows", hit.optLong("follows"))
                .put("categories", hit.optJSONArray("categories") ?: JSONArray()))
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

        val installedIds = HashSet<String>()
        mods.listFiles().orEmpty().filter { it.isFile && it.extension.equals("jar", true) }.forEach { file ->
            runCatching { JarFile(file).use { jar -> modId(jar, loader)?.let(installedIds::add) } }
        }

        val versions = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        val skipped = LinkedHashSet<String>()
        fun compatible(version: JSONObject) = version.getJSONArray("game_versions").let { values ->
            (0 until values.length()).any { values.getString(it) == gameVersion }
        } && version.getJSONArray("loaders").let { values ->
            (0 until values.length()).any { values.getString(it) == loader }
        }
        fun latest(id: String): JSONObject? {
            require(projectId.matches(id))
            val list = JSONArray(read(versionsUrl(id, loader, gameVersion)))
            // The API may return featured versions first; choose the most recently published
            // compatible build regardless of how the response is ordered.
            return (0 until list.length()).map { list.getJSONObject(it) }.filter(::compatible)
                .maxByOrNull { it.optString("date_published") }
        }
        // A dependency that cannot be installed is reported instead of failing the whole install:
        // authors list dependencies of their other loader's build, or files hosted elsewhere.
        fun skip(id: String, fallback: String) {
            val project = if (projectId.matches(id)) runCatching { JSONObject(read("$API/project/$id")) }.getOrNull() else null
            val slug = project?.optString("slug").orEmpty()
            if (slug.isNotEmpty() && (slug in installedIds || slug.replace('-', '_') in installedIds)) return
            skipped.add(project?.optString("title")?.takeIf { it.isNotBlank() } ?: fallback)
        }
        fun resolve(id: String, pinnedVersion: String? = null, dependency: Boolean = false) {
            check(versions.size < MAX_DEPENDENCIES) { "Too many required dependencies." }
            var version: JSONObject? = null
            var owner = id
            if (pinnedVersion != null) {
                require(projectId.matches(pinnedVersion))
                val pinned = JSONObject(read("$API/version/$pinnedVersion"))
                owner = pinned.optString("project_id").ifBlank { id }
                // Authors often pin a dependency build made for another Minecraft version or
                // loader. Use the newest compatible build of the same project instead.
                version = if (compatible(pinned)) pinned else if (projectId.matches(owner)) latest(owner) else null
            } else if (projectId.matches(id)) {
                version = latest(id)
            }
            if (version == null) {
                check(dependency) {
                    val name = if (loader == "neoforge") "NeoForge" else "Fabric"
                    "This mod has no $name build for Minecraft $gameVersion."
                }
                skip(owner, "a required mod")
                return
            }
            val versionId = version.getString("id")
            if (!seen.add(versionId)) return
            val dependencies = version.optJSONArray("dependencies") ?: JSONArray()
            for (i in 0 until dependencies.length()) {
                val dep = dependencies.getJSONObject(i)
                if (dep.optString("dependency_type") != "required") continue
                val depVersion = dep.optString("version_id").takeIf { it.isNotBlank() && it != "null" }
                val depProject = dep.optString("project_id").takeIf { it.isNotBlank() && it != "null" }
                if (depVersion != null) resolve(depProject.orEmpty(), depVersion, dependency = true)
                else if (depProject != null) resolve(depProject, dependency = true)
                else skipped.add(dep.optString("file_name").takeIf { it.isNotBlank() && it != "null" } ?: "a file hosted outside Modrinth")
            }
            versions.add(version)
        }
        resolve(project)
        val requestedVersionId = versions.last().getString("id")

        val staged = ArrayList<Pair<File, File>>()
        try {
            for (version in versions) {
                val files = version.getJSONArray("files")
                val chosen = (0 until files.length()).map { files.getJSONObject(it) }
                    .firstOrNull { it.optBoolean("primary") && it.optString("filename").endsWith(".jar", true) }
                    ?: (0 until files.length()).map { files.getJSONObject(it) }
                        .firstOrNull { it.optString("filename").endsWith(".jar", true) }
                    ?: error("A mod version has no JAR.")
                val filename = safeName(chosen.getString("filename"))
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
            val installed = "Installed $count mod file(s) for Minecraft $gameVersion."
            return if (skipped.isEmpty()) installed
            else "$installed Not available for this version, install separately if the game asks: " +
                skipped.joinToString(", ") + "."
        } finally {
            staged.forEach { (temp, _) -> temp.delete() }
        }
    }

    internal fun modId(jar: JarFile, loader: String): String? {
        val descriptor = jar.getJarEntry(if (loader == "neoforge")
            "META-INF/neoforge.mods.toml" else "fabric.mod.json")
        if (descriptor == null) {
            // NeoForge also loads plain libraries that declare FMLModType instead of a mods.toml.
            val attributes = if (loader == "neoforge") jar.manifest?.mainAttributes else null
            if (attributes?.getValue("FMLModType") !in setOf("LIBRARY", "GAMELIBRARY")) return null
            return "library:" + (attributes?.getValue("Automatic-Module-Name") ?: File(jar.name).name)
        }
        jar.getInputStream(descriptor).use { stream ->
            val bytes = stream.readNBytes(MAX_METADATA + 1)
            check(bytes.size <= MAX_METADATA) { "Mod metadata is too large." }
            val metadata = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
            if (loader == "neoforge") {
                // NeoForge's descriptor is TOML; accept a declared mod ID in a [[mods]] block.
                val block = metadata.substringAfter("[[mods]]", "")
                return Regex("(?m)^\\s*modId\\s*=\\s*['\"]([a-z0-9_.-]+)['\"]")
                    .find(block)?.groupValues?.get(1)
            }
            return JSONObject(metadata).optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun versionsUrl(id: String, loader: String, gameVersion: String) =
        "$API/project/$id/version?loaders=%5B%22$loader%22%5D&game_versions=%5B%22$gameVersion%22%5D&include_changelog=false"

    private fun hasBuild(id: String, loader: String, gameVersion: String): Boolean =
        JSONArray(read(versionsUrl(id, loader, gameVersion))).length() > 0

    /** Keeps Modrinth's filename where it is safe, replacing characters a mods folder should not hold. */
    internal fun safeName(raw: String): String {
        var name = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._+()\\[\\], -]"), "_")
        if (name.isNotEmpty() && !name[0].isLetterOrDigit()) name = "mod_$name"
        check(safeFilename.matches(name)) { "Invalid mod filename." }
        return name
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
