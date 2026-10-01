package dev.voxyquest.bridge

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject
import pojlib.util.Logger

/**
 * Updates the launcher from its GitHub releases, inside the launcher.
 *
 * On start it reads the latest release; when its version is newer than the installed one it
 * offers the update in a dialog. The APK is downloaded, checked against the release's SHA-256,
 * the launcher's package name, a higher version code and the installed signing key, then handed
 * to Android's package installer, which asks the user to confirm.
 */
internal object LauncherUpdater {
    private const val LATEST = "https://api.github.com/repos/austinslair/Minecraft-flat-screen-vr-launcher/releases/latest"
    private const val APK_ASSET = "VoxyQuest-Quest.apk"
    private const val MAX_APK = 400L * 1024 * 1024
    private const val PREFERENCES = "voxyquest_updates"
    private const val KEY_DISMISSED = "dismissed_tag"
    private const val KEY_DISMISSED_AT = "dismissed_at"
    private const val REMIND_AFTER_MS = 24L * 60 * 60 * 1000

    class Release(val tag: String, val name: String, val notes: String, val apkUrl: String, val shaUrl: String?, val versionCode: Long)

    @Volatile private var checking = false
    @Volatile private var installing = false

    /** Checks in the background; offers the update if there is one and it was not put off today. */
    fun checkOnStart(activity: Activity) {
        if (checking || installing) return
        checking = true
        Thread({
            try {
                val release = latest() ?: return@Thread
                val installed = installedVersionCode(activity)
                if (release.versionCode <= installed) return@Thread
                val prefs = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                if (prefs.getString(KEY_DISMISSED, null) == release.tag &&
                    System.currentTimeMillis() - prefs.getLong(KEY_DISMISSED_AT, 0L) < REMIND_AFTER_MS) return@Thread
                activity.runOnUiThread { offer(activity, release) }
            } catch (failure: Exception) {
                Logger.getInstance().appendToLog("VoxyQuest update check failed: ${failure.message}")
            } finally {
                checking = false
            }
        }, "VoxyQuest-UpdateCheck").start()
    }

    private fun offer(activity: Activity, release: Release) {
        if (activity.isFinishing || activity.isDestroyed || MinecraftGameActivity.isRunning) return
        val notes = release.notes.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(12).joinToString("\n")
        AlertDialog.Builder(activity)
            .setTitle("Update available: ${release.name}")
            .setMessage("You have ${installedVersionName(activity)}.\n\n$notes")
            .setPositiveButton("Update") { _, _ -> download(activity, release) }
            .setNegativeButton("Later") { _, _ ->
                activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                    .putString(KEY_DISMISSED, release.tag)
                    .putLong(KEY_DISMISSED_AT, System.currentTimeMillis()).apply()
            }
            .show()
    }

    private fun download(activity: Activity, release: Release) {
        // Installing requires the user to allow this app to install apps once.
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Allow updates")
                .setMessage("To update itself, VoxyQuest needs permission to install apps. Allow it on the next screen, then come back and choose Update again.")
                .setPositiveButton("Open settings") { _, _ ->
                    runCatching {
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.packageName)))
                    }.onFailure { openReleasePage(activity, release) }
                    // Ask again on the next start rather than waiting a day.
                    activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(KEY_DISMISSED).apply()
                }
                .setNegativeButton("Download in browser") { _, _ -> openReleasePage(activity, release) }
                .show()
            return
        }
        installing = true
        val progress = AlertDialog.Builder(activity).setTitle("Updating VoxyQuest")
            .setMessage("Downloading ${release.name}…").setCancelable(false).show()
        Thread({
            val result = runCatching { fetchAndVerify(activity, release) { done, total ->
                activity.runOnUiThread {
                    progress.setMessage("Downloading ${release.name}… ${done / (1024 * 1024)} / ${total / (1024 * 1024)} MB")
                }
            } }
            activity.runOnUiThread {
                progress.dismiss()
                result.onSuccess { apk -> install(activity, apk) }.onFailure { failure ->
                    installing = false
                    Logger.getInstance().appendToLog("VoxyQuest update failed: " + android.util.Log.getStackTraceString(failure))
                    AlertDialog.Builder(activity).setTitle("Update failed")
                        .setMessage((failure.message ?: "The download failed.") + "\n\nYou can also download it from the release page.")
                        .setPositiveButton("Release page") { _, _ -> openReleasePage(activity, release) }
                        .setNegativeButton("Close", null).show()
                }
            }
        }, "VoxyQuest-UpdateDownload").start()
    }

    private fun fetchAndVerify(activity: Activity, release: Release, progress: (Long, Long) -> Unit): File {
        val directory = File(activity.cacheDir, "launcher-update").apply { deleteRecursively(); mkdirs() }
        val apk = File(directory, "VoxyQuest-${release.tag}.apk")
        val expected = release.shaUrl?.let { url ->
            String(readBytes(URL(url), 4096), Charsets.UTF_8).trim().split(Regex("\\s+")).first().lowercase()
        }
        val actual = download(URL(release.apkUrl), apk, progress)
        if (expected != null && expected.matches(Regex("[0-9a-f]{64}")) && actual != expected)
            error("The downloaded update is damaged (checksum mismatch). Try again.")

        val manager = activity.packageManager
        val archive = archiveInfo(manager, apk) ?: error("The downloaded file is not an Android app.")
        check(archive.packageName == activity.packageName) { "The download is not a VoxyQuest update." }
        check(versionCode(archive) > installedVersionCode(activity)) { "The download is not newer than this version." }
        val installed = signers(manager.getPackageInfo(activity.packageName, signingFlags()))
        val incoming = signers(archive)
        // Some Android versions report no certificates for an archive; the installer still checks.
        check(incoming.isEmpty() || installed == incoming) {
            "This update is signed with a different key, so Android would refuse it. Uninstall VoxyQuest and install the new APK from the release page."
        }
        return apk
    }

    private fun install(activity: Activity, apk: File) {
        try {
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(activity.packageName)
            params.setSize(apk.length())
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("VoxyQuest.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output, 65536) }
                    session.fsync(output)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getBroadcast(activity, sessionId,
                    Intent(activity, UpdateInstallReceiver::class.java), flags)
                session.commit(status.intentSender)
            }
            Logger.getInstance().appendToLog("VoxyQuest update: handed ${apk.name} to the package installer")
        } catch (failure: Exception) {
            installing = false
            AlertDialog.Builder(activity).setTitle("Update failed")
                .setMessage(failure.message ?: "Android could not start the installation.")
                .setPositiveButton("Close", null).show()
        }
    }

    fun installFinished() {
        installing = false
    }

    private fun openReleasePage(activity: Activity, release: Release) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/austinslair/Minecraft-flat-screen-vr-launcher/releases/tag/${release.tag}")))
        }
    }

    /** The latest release with an APK, or null when it cannot be read. */
    private fun latest(): Release? {
        val json = JSONObject(String(readBytes(URL(LATEST), 1024 * 1024), Charsets.UTF_8))
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tag = json.optString("tag_name")
        val code = versionCodeFromTag(tag) ?: return null
        var apk: String? = null
        var sha: String? = null
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when (asset.optString("name")) {
                APK_ASSET -> apk = asset.optString("browser_download_url")
                "$APK_ASSET.sha256" -> sha = asset.optString("browser_download_url")
            }
        }
        if (apk.isNullOrBlank()) return null
        return Release(tag, json.optString("name").ifBlank { tag }, json.optString("body"), apk, sha, code)
    }

    /** Release tags are v0.1.0-alpha.N, and N is the Android version code of that build. */
    internal fun versionCodeFromTag(tag: String): Long? =
        Regex("^v\\d+\\.\\d+\\.\\d+-alpha\\.(\\d+)$").find(tag)?.groupValues?.get(1)?.toLongOrNull()

    private fun installedVersionCode(context: Context): Long =
        versionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "this version"

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun archiveInfo(manager: PackageManager, apk: File): PackageInfo? =
        manager.getPackageArchiveInfo(apk.path, signingFlags())

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun connect(url: URL): HttpURLConnection {
        var current = url
        repeat(6) {
            check(current.protocol == "https") { "Insecure update download." }
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", ModrinthClient.USER_AGENT)
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: error("Update download failed ($code).")
                connection.disconnect()
                current = URL(current, location)
                return@repeat
            }
            if (code != 200) {
                connection.disconnect()
                error("Update server returned $code.")
            }
            return connection
        }
        error("Too many redirects.")
    }

    private fun readBytes(url: URL, limit: Int): ByteArray {
        val connection = connect(url)
        try {
            connection.inputStream.use { input ->
                val bytes = input.readNBytes(limit + 1)
                check(bytes.size <= limit) { "Update information is too large." }
                return bytes
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads to [file] and returns its SHA-256. */
    private fun download(url: URL, file: File, progress: (Long, Long) -> Unit): String {
        val connection = connect(url)
        try {
            val total = connection.contentLengthLong
            check(total <= MAX_APK) { "The update is unexpectedly large." }
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        done += count
                        check(done <= MAX_APK) { "The update is unexpectedly large." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (done - reported >= 2L * 1024 * 1024) {
                            reported = done
                            progress(done, if (total > 0) total else done)
                        }
                    }
                }
            }
            check(total <= 0 || done == total) { "The update download was cut off. Try again." }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }
}

/** Receives the package installer's result; shows its confirmation screen when it asks for one. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> LauncherUpdater.installFinished()
            else -> {
                LauncherUpdater.installFinished()
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Logger.getInstance().appendToLog("VoxyQuest update was not installed: $message")
                Toast.makeText(context, "VoxyQuest update was not installed: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
