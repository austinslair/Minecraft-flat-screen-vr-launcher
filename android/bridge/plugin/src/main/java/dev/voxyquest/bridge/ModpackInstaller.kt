package dev.voxyquest.bridge

import android.app.Activity
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import pojlib.install.VoxyQuestInstaller
import pojlib.util.Constants

/**
 * Installs Modrinth modpacks (.mrpack) as new instances, using the pack's Minecraft version and
 * loader. Every pack gets the launcher's Quest Vivecraft build: packs without Vivecraft get it
 * added, and a pack's own Vivecraft (a desktop build, which cannot start on the Quest) is
 * replaced by it.
 */
internal object ModpackInstaller {
    private const val MAX_PACK = 1024L * 1024 * 1024
    private const val MAX_FILE = 512L * 1024 * 1024
    private const val MAX_INDEX = 16 * 1024 * 1024
    private const val MAX_FILES = 2000
    private const val DOWNLOAD_THREADS = 4
    // The download hosts the .mrpack format allows; redirects from them are followed and every
    // file is checked against the hash in the pack index.
    private val hosts = setOf("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com")
    private val hash = Regex("[a-fA-F0-9]+")

    /** Installs the newest build of a Modrinth modpack project made for this version and loader. */
    fun installFromModrinth(activity: Activity, gameVersion: String, loader: String, project: JSONObject,
            progress: (String) -> Unit): Installed {
        val id = project.getString("id")
        val list = JSONArray(ModrinthClient.read(ModrinthClient.versionsUrl(id, loader, gameVersion)))
        val version = (0 until list.length()).map { list.getJSONObject(it) }
            .maxByOrNull { it.optString("date_published") }
            ?: error("This modpack has no ${loaderName(loader)} build for Minecraft $gameVersion.")
        val files = version.getJSONArray("files")
        val file = (0 until files.length()).map { files.getJSONObject(it) }
            .sortedByDescending { it.optBoolean("primary") }
            .firstOrNull { it.optString("filename").endsWith(".mrpack", true) }
            ?: error("This modpack version has no .mrpack file.")
        val url = URL(file.getString("url"))
        check(url.protocol == "https" && url.host == "cdn.modrinth.com") { "Unsupported modpack download host." }
        val sha512 = file.getJSONObject("hashes").optString("sha512")
        check(sha512.length == 128 && hash.matches(sha512)) { "Modpack checksum missing." }
        val staging = newStaging()
        try {
            progress("Downloading ${project.optString("title", "modpack")}…")
            val pack = File(staging, "pack.mrpack")
            download(url, pack, "SHA-512", sha512, MAX_PACK)
            return install(activity, pack, staging, project.optString("title"), progress)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Installs a .mrpack the user picked. */
    fun installFromStream(activity: Activity, input: InputStream, progress: (String) -> Unit): Installed {
        val staging = newStaging()
        try {
            progress("Reading the modpack…")
            val pack = File(staging, "pack.mrpack")
            pack.outputStream().use { output -> copy(input, output, MAX_PACK) }
            return install(activity, pack, staging, "", progress)
        } finally {
            staging.deleteRecursively()
        }
    }

    class Installed(val name: String, val message: String)

    private class PackFile(val path: String, val urls: List<URL>, val algorithm: String, val hash: String, val size: Long)

    private fun install(activity: Activity, packFile: File, staging: File, fallbackName: String,
            progress: (String) -> Unit): Installed {
        val game = File(staging, "game").apply { mkdirs() }
        val notes = ArrayList<String>()
        var replacedVivecraft = false
        val name: String
        val minecraft: String
        val loader: String
        val files = ArrayList<PackFile>()
        ZipFile(packFile).use { zip ->
            val entry = zip.getEntry("modrinth.index.json")
                ?: error(if (zip.getEntry("manifest.json") != null)
                    "This is a CurseForge modpack. Download the Modrinth (.mrpack) version of it instead."
                    else "This is not a Modrinth modpack (.mrpack).")
            val index = JSONObject(String(zip.getInputStream(entry).use { readLimited(it, MAX_INDEX) }, Charsets.UTF_8)
                .removePrefix("\uFEFF"))
            check(index.optInt("formatVersion") == 1 && index.optString("game") == "minecraft") {
                "This modpack uses a format VoxyQuest does not support."
            }
            val dependencies = index.getJSONObject("dependencies")
            minecraft = dependencies.optString("minecraft").takeIf { it.matches(Regex("[0-9][0-9.]{0,15}")) }
                ?: error("The modpack does not name its Minecraft version.")
            loader = when {
                dependencies.has("neoforge") -> "neoforge"
                dependencies.has("fabric-loader") -> "fabric"
                dependencies.has("forge") -> error("This modpack needs Forge. VoxyQuest supports Fabric and NeoForge packs.")
                dependencies.has("quilt-loader") -> error("This modpack needs Quilt. VoxyQuest supports Fabric and NeoForge packs.")
                else -> error("This modpack does not name a supported mod loader.")
            }
            checkSupported(activity, minecraft, loader)
            name = uniqueName(index.optString("name").ifBlank { fallbackName })

            val list = index.optJSONArray("files") ?: JSONArray()
            check(list.length() <= MAX_FILES) { "The modpack lists too many files." }
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                if (item.optJSONObject("env")?.optString("client") == "unsupported") continue
                val path = safePath(item.optString("path")) ?: error("The modpack contains an unsafe file path.")
                val hashes = item.optJSONObject("hashes") ?: JSONObject()
                val (algorithm, value) = when {
                    hashes.optString("sha512").length == 128 -> "SHA-512" to hashes.getString("sha512")
                    hashes.optString("sha1").length == 40 -> "SHA-1" to hashes.getString("sha1")
                    else -> error("${path.substringAfterLast('/')} has no checksum in the modpack.")
                }
                check(hash.matches(value)) { "Invalid checksum in the modpack." }
                val downloads = item.optJSONArray("downloads") ?: JSONArray()
                val urls = (0 until downloads.length()).mapNotNull { j ->
                    runCatching { URL(downloads.getString(j)) }.getOrNull()
                        ?.takeIf { it.protocol == "https" && it.host in hosts }
                }
                check(urls.isNotEmpty()) { "${path.substringAfterLast('/')} is hosted somewhere modpacks may not download from." }
                files.add(PackFile(path, urls, algorithm, value, item.optLong("fileSize", -1)))
            }

            // client-overrides are applied after overrides so they win, as the format requires.
            for (prefix in listOf("overrides/", "client-overrides/")) {
                for (zipEntry in zip.entries()) {
                    if (zipEntry.isDirectory || !zipEntry.name.startsWith(prefix)) continue
                    val path = safePath(zipEntry.name.removePrefix(prefix)) ?: continue
                    val target = inside(game, path)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(zipEntry).use { input -> target.outputStream().use { copy(input, it, MAX_FILE) } }
                }
            }
        }
        packFile.delete()

        if (files.isNotEmpty()) downloadAll(files, game, progress)

        // A pack's Vivecraft is a desktop build; the launcher installs the Quest one instead.
        val stagedMods = File(game, "mods")
        stagedMods.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".jar", true) }.forEach { jar ->
            if (modId(jar, loader) == "vivecraft") { jar.delete(); replacedVivecraft = true }
        }

        progress("Installing Minecraft $minecraft with ${loaderName(loader)} for $name…")
        val folder = File(Constants.USER_HOME, "instances/" + VoxyQuestInstaller.directoryName(name))
        val instance = try {
            VoxyQuestInstaller.install(activity, name, minecraft, loader) { progress(it) }
        } catch (failure: Exception) {
            // uniqueName picked a folder nothing used, so a failed install leaves nothing worth keeping.
            folder.deleteRecursively()
            throw failure
        }
        val gameDir = File(instance.gameDir).canonicalFile

        progress("Adding the modpack's files…")
        val mods = File(gameDir, "mods").apply { mkdirs() }
        val launcherMods = mods.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".jar", true) }
            .mapNotNull { modId(it, loader) }.toHashSet()
        val duplicates = ArrayList<String>()
        var added = 0
        game.walkTopDown().filter { it.isFile }.forEach { source ->
            val relative = source.relativeTo(game).invariantSeparatorsPath
            val target = inside(gameDir, relative)
            if (relative.startsWith("mods/") && relative.endsWith(".jar", true)) {
                val id = modId(source, loader)
                // The launcher's own copy (Vivecraft and its runtime mods) is the one that works here.
                if (id != null && id in launcherMods) { duplicates.add(source.name); return@forEach }
                if (target.exists()) { duplicates.add(source.name); return@forEach }
            }
            target.parentFile?.mkdirs()
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            added++
        }

        val vr = loader == "fabric" || VoxyQuestInstaller.supportsNeoForgeVr(minecraft)
        notes.add(when {
            !vr -> "VR is not available for NeoForge $minecraft, so play it in Flatscreen."
            replacedVivecraft -> "The pack's Vivecraft was replaced with the Quest VR build."
            else -> "Vivecraft was added for VR."
        })
        if (duplicates.isNotEmpty()) notes.add("Kept the launcher's copy of: ${duplicates.joinToString(", ")}.")
        return Installed(name, "Installed the modpack as instance \"$name\" (Minecraft $minecraft, " +
            "${loaderName(loader)}) with $added file(s). ${notes.joinToString(" ")} Select it in your Library to play.")
    }

    private fun downloadAll(files: List<PackFile>, game: File, progress: (String) -> Unit) {
        val done = AtomicInteger()
        progress("Downloading modpack files (0/${files.size})…")
        val pool = Executors.newFixedThreadPool(DOWNLOAD_THREADS)
        try {
            val tasks = files.map { file ->
                pool.submit {
                    val target = inside(game, file.path)
                    target.parentFile?.mkdirs()
                    var failure: Throwable? = null
                    for (url in file.urls) {
                        failure = runCatching {
                            download(url, target, file.algorithm, file.hash,
                                if (file.size in 1..MAX_FILE) file.size else MAX_FILE)
                        }.exceptionOrNull()
                        if (failure == null) break
                    }
                    failure?.let { throw IllegalStateException("Could not download ${target.name}: ${it.message}") }
                    progress("Downloading modpack files (${done.incrementAndGet()}/${files.size})…")
                }
            }
            tasks.forEach {
                try { it.get() } catch (e: java.util.concurrent.ExecutionException) { throw e.cause ?: e }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun checkSupported(activity: Activity, minecraft: String, loader: String) {
        val versions = if (loader == "neoforge") VoxyQuestInstaller.neoForgeVersions().toList()
            else VoxyQuestInstaller.catalog(activity).versions.map { it.name }
        check(minecraft in versions) {
            "This modpack is for Minecraft $minecraft with ${loaderName(loader)}. VoxyQuest supports " +
                "${loaderName(loader)} on ${versions.joinToString(", ")}."
        }
    }

    /** An instance name the launcher accepts and no instance or folder already uses. */
    private fun uniqueName(raw: String): String {
        val base = raw.replace(Regex("[^A-Za-z0-9 _-]"), " ").replace(Regex(" +"), " ").trim()
            .trimStart(' ', '_', '-').take(40).trim().ifEmpty { "Modpack" }
        val registry = VoxyQuestInstaller.readRegistry().toArray()
        val instances = File(Constants.USER_HOME, "instances")
        fun taken(candidate: String): Boolean {
            val directory = VoxyQuestInstaller.directoryName(candidate)
            return registry.any { it.instanceName?.lowercase(Locale.ROOT)?.replace(' ', '_') == directory } ||
                File(instances, directory).exists()
        }
        if (!taken(base)) return base
        for (n in 2..99) if (!taken("$base $n")) return "$base $n"
        error("Too many instances are named $base.")
    }

    private fun newStaging(): File {
        val root = File(Constants.USER_HOME, "modpack-staging")
        // Left over from an install the system stopped; nothing else uses this folder.
        root.listFiles().orEmpty().forEach { it.deleteRecursively() }
        return File(root, System.currentTimeMillis().toString()).apply { check(mkdirs()) { "Could not prepare the modpack." } }
    }

    private fun safePath(path: String): String? {
        if (path.isEmpty() || path.length > 512 || path.startsWith("/") || path.contains('\\') || path.contains('\u0000')) return null
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.contains(':') }) return null
        return path
    }

    private fun inside(root: File, path: String): File {
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, path).canonicalFile
        check(target.toPath().startsWith(canonicalRoot.toPath()) && target != canonicalRoot) { "Unsafe modpack path." }
        return target
    }

    private fun modId(file: File, loader: String): String? =
        runCatching { JarFile(file).use { ModrinthClient.modId(it, loader) } }.getOrNull()

    private fun loaderName(loader: String) = if (loader == "neoforge") "NeoForge" else "Fabric"

    private fun download(start: URL, file: File, algorithm: String, expected: String, limit: Long) {
        var url = start
        for (redirect in 0..5) {
            check(url.protocol == "https") { "Insecure download." }
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", ModrinthClient.USER_AGENT)
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Download failed ($code)."))
                    continue
                }
                check(code == 200) { "Download failed ($code)." }
                val digest = MessageDigest.getInstance(algorithm)
                connection.inputStream.use { input ->
                    file.outputStream().use { output -> copy(input, output, limit, digest) }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual.equals(expected, true)) { "Checksum did not match." }
                return
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects.")
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copy(input, output, limit.toLong())
        return output.toByteArray()
    }

    private fun copy(input: InputStream, output: java.io.OutputStream, limit: Long, digest: MessageDigest? = null) {
        val buffer = ByteArray(65536)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit) { "A modpack file is too large." }
            digest?.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
    }
}
