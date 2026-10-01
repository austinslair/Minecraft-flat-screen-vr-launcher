package dev.voxyquest.bridge

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import pojlib.util.Logger
import pojlib.util.json.MinecraftInstances

/**
 * Checks an instance's enabled mods the way the loader will, before the game starts: which mod
 * IDs are present (including the ones bundled inside other mods) and which required ones are
 * missing. On the Quest a loader that finds a problem cannot show its error window, so the game
 * just closes; this fixes what it can instead.
 */
internal object ModDoctor {
    private const val MAX_METADATA = 1024 * 1024
    private const val MAX_NESTED = 64L * 1024 * 1024
    private const val MAX_FIXES = 12

    /** IDs the loader itself provides. */
    private val builtIn = mapOf(
        "fabric" to setOf("minecraft", "java", "fabricloader", "fabric-loader", "mixinextras"),
        "neoforge" to setOf("minecraft", "java", "neoforge", "forge", "fml", "javafml", "lowcodefml"),
    )

    class Mod(val file: File, val ids: Set<String>, val required: Set<String>)

    class Report(val mods: List<Mod>, val missing: Map<String, List<String>>, val duplicates: Map<String, List<File>>)

    fun check(gameDir: File, loader: String): Report {
        val mods = File(gameDir, "mods").listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".jar", true) }
            .sortedBy { it.name }
            .mapNotNull { file -> runCatching { read(file, loader) }.getOrNull() }
        val provided = HashSet(builtIn.getValue(loader))
        mods.forEach { provided.addAll(it.ids) }
        val missing = LinkedHashMap<String, MutableList<String>>()
        for (mod in mods) for (id in mod.required) {
            if (id !in provided) missing.getOrPut(id) { ArrayList() }.add(mod.file.name)
        }
        // Only a mod's own top-level ID counts here: libraries bundled inside several mods are
        // expected and the loader picks one copy.
        val owners = LinkedHashMap<String, MutableList<File>>()
        mods.forEach { mod -> mod.ids.firstOrNull()?.let { owners.getOrPut(it) { ArrayList() }.add(mod.file) } }
        return Report(mods, missing, owners.filterValues { it.size > 1 })
    }

    /**
     * Run before each launch: disables older duplicate copies and downloads missing required
     * mods from Modrinth. Never throws; problems it cannot fix are written to the launch log.
     */
    fun prepareForLaunch(instance: MinecraftInstances.Instance) {
        val gameDir = File(instance.gameDir ?: return)
        val loader = instance.loaderId()
        runCatching {
            // Reading every mod takes a moment; skip it while the mods folder is as last checked.
            val stamp = File(gameDir, "voxyquest-mods-checked.txt")
            val listing = listing(gameDir)
            if (stamp.isFile && stamp.readText() == listing) return
            var report = check(gameDir, loader)
            for ((id, files) in report.duplicates) {
                // The loader refuses to start with two copies; keep the launcher's Vivecraft, else the newest file.
                files.sortedWith(compareByDescending<File> { it.name.equals("Vivecraft.jar", true) }
                    .thenByDescending { it.lastModified() }).drop(1).forEach { old ->
                    val disabled = File(old.path + ".disabled")
                    if (!disabled.exists() && old.renameTo(disabled)) log("disabled ${old.name}, an older copy of $id")
                }
            }
            if (report.duplicates.isNotEmpty()) report = check(gameDir, loader)
            if (report.missing.isNotEmpty()) {
                val installed = fixMissing(instance, report)
                if (installed.isNotEmpty()) log("installed missing required mods: ${installed.joinToString(", ")}")
                val still = check(gameDir, loader).missing
                if (still.isNotEmpty()) {
                    log("missing required mods: " + describe(still))
                    return
                }
            }
            stamp.writeText(listing(gameDir))
        }.onFailure { log("mod check skipped: ${it.message}") }
    }

    /** Downloads missing required mods; returns the files installed. Never throws. */
    fun fixMissing(instance: MinecraftInstances.Instance, first: Report): List<String> {
        val gameDir = File(instance.gameDir ?: return emptyList())
        val loader = instance.loaderId()
        val name = instance.instanceName ?: return emptyList()
        val installed = ArrayList<String>()
        val tried = HashSet<String>()
        var report = first
        repeat(MAX_FIXES) {
            if (report.missing.isEmpty()) return installed
            val candidates = LinkedHashSet<String>()
            // Exact answer first: Modrinth knows the dependencies of the builds it hosts.
            runCatching { candidates.addAll(ModrinthClient.requiredProjects(dependents(report), loader)) }
            // Otherwise a mod ID is usually its Modrinth slug.
            report.missing.keys.forEach { id ->
                candidates.add(id)
                candidates.add(id.replace('_', '-'))
            }
            var progress = false
            for (project in candidates) {
                if (!tried.add(project)) continue
                val before = File(gameDir, "mods").list().orEmpty().toSet()
                val result = runCatching {
                    val id = ModrinthClient.projectId(project) ?: return@runCatching null
                    ModrinthClient.install(name, id, update = false) {}
                }
                if (result.getOrNull() == null) continue
                val added = File(gameDir, "mods").list().orEmpty().filter { it !in before }
                if (added.isEmpty()) continue
                val after = check(gameDir, loader)
                if (after.missing.keys.containsAll(report.missing.keys)) {
                    // A slug that matched a different mod: it provides nothing that was missing.
                    added.forEach { File(File(gameDir, "mods"), it).delete() }
                    continue
                }
                installed.addAll(added)
                report = after
                progress = true
                break
            }
            if (!progress) return installed
        }
        return installed
    }

    fun describe(missing: Map<String, List<String>>): String =
        missing.entries.joinToString("; ") { (id, by) -> "$id (needed by ${by.joinToString(", ")})" }

    private fun listing(gameDir: File): String =
        File(gameDir, "mods").listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".jar", true) }
            .sortedBy { it.name }.joinToString("\n") { "${it.name} ${it.length()} ${it.lastModified()}" }

    private fun dependents(report: Report): List<File> {
        val names = report.missing.values.flatten().toSet()
        return report.mods.map { it.file }.filter { it.name in names }
    }

    fun sha512(file: File): String {
        val digest = MessageDigest.getInstance("SHA-512")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun read(file: File, loader: String): Mod? = JarFile(file).use { jar ->
        val ids = LinkedHashSet<String>()
        val required = LinkedHashSet<String>()
        fun entry(name: String): ByteArray? = jar.getJarEntry(name)?.let { e -> jar.getInputStream(e).use { limited(it, MAX_METADATA) } }
        if (loader == "fabric") {
            val metadata = entry("fabric.mod.json") ?: return null
            fabric(metadata, ids, required, { path ->
                jar.getJarEntry(path)?.let { e -> jar.getInputStream(e).use { limited(it, MAX_NESTED) } }
            }, 0)
        } else {
            val toml = entry("META-INF/neoforge.mods.toml")
            if (toml == null) {
                // Plain libraries load by manifest and declare nothing to check.
                if (jar.manifest?.mainAttributes?.getValue("FMLModType") == null) return null
                return Mod(file, setOf("library:" + file.name), emptySet())
            }
            neoforge(String(toml, Charsets.UTF_8), ids, required)
            entry("META-INF/jarjar/metadata.json")?.let { json ->
                val jars = JSONObject(String(json, Charsets.UTF_8)).optJSONArray("jars") ?: JSONArray()
                for (i in 0 until jars.length()) {
                    val path = jars.getJSONObject(i).optString("path")
                    val nested = jar.getJarEntry(path) ?: continue
                    val inner = runCatching { unzip(jar.getInputStream(nested).use { limited(it, MAX_NESTED) }) }.getOrNull()
                    inner?.get("META-INF/neoforge.mods.toml")?.let { neoforge(String(it, Charsets.UTF_8), ids, HashSet()) }
                }
            }
        }
        if (ids.isEmpty()) null else Mod(file, ids, required - ids)
    }

    private fun fabric(metadata: ByteArray, ids: MutableSet<String>, required: MutableSet<String>,
            open: (String) -> ByteArray?, depth: Int) {
        val json = JSONObject(String(metadata, Charsets.UTF_8).removePrefix("\uFEFF"))
        json.optString("id").takeIf { it.isNotBlank() }?.let(ids::add)
        json.optJSONArray("provides")?.let { for (i in 0 until it.length()) ids.add(it.getString(i)) }
        // Nested mods' own needs are met inside their parent or reported by its author.
        if (depth == 0) json.optJSONObject("depends")?.keys()?.forEach(required::add)
        if (depth >= 3) return
        val nested = json.optJSONArray("jars") ?: return
        for (i in 0 until nested.length()) {
            val bytes = open(nested.optJSONObject(i)?.optString("file").orEmpty()) ?: continue
            val inner = runCatching { unzip(bytes) }.getOrNull() ?: continue
            val innerMetadata = inner["fabric.mod.json"] ?: continue
            runCatching { fabric(innerMetadata, ids, required, { inner[it] }, depth + 1) }
        }
    }

    /** The metadata and nested JARs of a JAR held in memory. */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == "fabric.mod.json" || name == "META-INF/neoforge.mods.toml")
                    result[name] = limited(zip, MAX_METADATA.toLong())
                else if (name.endsWith(".jar")) result[name] = limited(zip, MAX_NESTED)
            }
        }
        return result
    }

    private fun neoforge(toml: String, ids: MutableSet<String>, required: MutableSet<String>) {
        val sections = toml.split(Regex("(?m)^\\s*(?=\\[)"))
        for (section in sections) {
            val header = section.lineSequence().firstOrNull()?.trim().orEmpty()
            fun field(name: String) = Regex("(?m)^\\s*$name\\s*=\\s*['\"]?([^'\"\\r\\n#]+?)['\"]?\\s*(#.*)?$")
                .find(section)?.groupValues?.get(1)?.trim()
            if (header == "[[mods]]") field("modId")?.let(ids::add)
            if (header.startsWith("[[dependencies.")) {
                val id = field("modId") ?: continue
                val type = field("type")?.lowercase()
                val mandatory = field("mandatory")
                val side = field("side")?.uppercase()
                val needed = if (type != null) type == "required" else mandatory == "true"
                if (needed && side != "SERVER") required.add(id)
            }
        }
    }

    private fun limited(input: InputStream, limit: Int): ByteArray = limited(input, limit.toLong())

    private fun limited(input: InputStream, limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit) { "Mod metadata is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun log(message: String) = Logger.getInstance().appendToLog("VoxyQuest launch: $message")
}
