package dev.voxyquest.bridge

import android.app.AlertDialog
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import pojlib.util.Constants
import pojlib.util.GsonUtils
import pojlib.util.Logger
import pojlib.util.json.MinecraftInstances
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.lwjgl.glfw.CallbackBridge
import pojlib.PojlibRuntime
import pojlib.install.VoxyQuestInstaller
import pojlib.account.LoginHelper

class VoxyQuestBridgePlugin(godot: Godot) : GodotPlugin(godot) {
    private var accountRestoreRequested = false
    private var previousLaunchReportShown = false

    companion object {
        private const val MICROSOFT_DEVICE_LOGIN_FALLBACK = "https://microsoft.com/devicelogin"
        private const val MICROPHONE_PERMISSION_REQUEST = 2471
    }

    override fun getPluginName(): String = BuildConfig.GODOT_PLUGIN_NAME

    @UsedByGodot
    fun getBridgeVersion(): String = "0.6.0"

    @UsedByGodot
    fun getHostEngine(): String = "Godot"

    @UsedByGodot
    fun isAndroidRuntimeReady(): Boolean = activity != null

    @UsedByGodot
    fun initializePojlib(): Boolean {
        val hostActivity = activity ?: return false
        return runCatching {
            PojlibRuntime.initialize(hostActivity)
            // Creating the logger rotates the previous process's latestlog.txt into
            // previouslog.txt. This lets us recover the final launch breadcrumbs even
            // when Android/native code killed the process without a Java exception.
            Logger.getInstance()
            maybeShowPreviousLaunchReport(hostActivity)
            if (!accountRestoreRequested && BuildConfig.MICROSOFT_CLIENT_ID.isNotBlank()) {
                accountRestoreRequested = LoginHelper.restoreSession(
                    hostActivity,
                    BuildConfig.MICROSOFT_CLIENT_ID,
                )
            }
            PojlibRuntime.isInitialized()
        }.getOrDefault(false)
    }

    private fun maybeShowPreviousLaunchReport(hostActivity: android.app.Activity) {
        if (previousLaunchReportShown) return
        previousLaunchReportShown = true

        val previous = File(Constants.USER_HOME, "previouslog.txt")
        if (!previous.isFile || previous.length() <= 0L) return
        val text = runCatching { previous.readText() }.getOrDefault("")
        if (!text.contains("VoxyQuest launch: game activity created")) return
        if (text.contains("VoxyQuest launch: Java VM returned") ||
            text.contains("VoxyQuest launch failure:")) return

        val usefulLines = text.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() && (
                    line.contains("VoxyQuest launch:") ||
                    line.contains("QuestCraft: Setting JVM memory") ||
                    line.contains("Java Exit code")
                )
            }
            .toList()
            .takeLast(24)

        val details = if (usefulLines.isEmpty()) {
            "The previous Minecraft process ended before it could write a normal exit or Java exception."
        } else {
            usefulLines.joinToString("\n")
        }
        val report = "The previous Minecraft launch ended unexpectedly. Copy this report and send it back so the exact crash stage can be fixed.\n\n$details"

        hostActivity.runOnUiThread {
            if (hostActivity.isFinishing || hostActivity.isDestroyed) return@runOnUiThread
            AlertDialog.Builder(hostActivity)
                .setTitle("Previous Minecraft crash")
                .setMessage(report)
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy report") { _, _ ->
                    val clipboard = hostActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("VoxyQuest crash report", report))
                }
                .show()
        }
    }

    @UsedByGodot
    fun getPojlibCompatibilityState(): String =
        if (PojlibRuntime.isInitialized()) "godot_host_ready" else "not_initialized"

    @UsedByGodot
    fun getMicrophonePermissionState(): String {
        val host = activity ?: return "unavailable"
        return if (host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            "granted" else "denied"
    }

    @UsedByGodot
    fun requestMicrophoneAccess(): Boolean {
        val host = activity ?: return false
        if (getMicrophonePermissionState() == "granted") return true
        host.runOnUiThread {
            host.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_REQUEST)
        }
        return true
    }

    @UsedByGodot
    fun openMicrophoneAppSettings(): Boolean {
        val host = activity ?: return false
        return runCatching {
            host.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", host.packageName, null)))
            true
        }.getOrDefault(false)
    }

    @UsedByGodot
    fun copyInputReport(): Boolean {
        val host = activity ?: return false
        val candidates = listOf("previouslog.txt", "latestlog.txt")
            .map { File(Constants.USER_HOME, it) }
            .filter { it.isFile && it.length() > 0 }
            .sortedByDescending { it.lastModified() }
        val log = candidates.firstOrNull { file ->
            runCatching { file.useLines { lines -> lines.any { it.contains("flatscreen input ready") } } }
                .getOrDefault(false)
        } ?: candidates.firstOrNull() ?: return false
        val details = runCatching {
            log.useLines { lines ->
                lines.filter { line ->
                    line.startsWith("VoxyQuest launch:") &&
                        (line.contains("input", ignoreCase = true) ||
                         line.contains("controller", ignoreCase = true) ||
                         line.contains("mouse", ignoreCase = true))
                }.toList().takeLast(30).joinToString("\n")
            }
        }.getOrDefault("")
        val report = "VoxyQuest flatscreen input report\n" +
            (details.ifBlank { "No Android input events were recorded in the latest launch." })
        val clipboard = host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VoxyQuest input report", report))
        return true
    }

    @UsedByGodot
    fun isMicrosoftLoginConfigured(): Boolean = BuildConfig.MICROSOFT_CLIENT_ID.isNotBlank()

    @UsedByGodot
    fun startMicrosoftLogin(): Boolean {
        val hostActivity = activity ?: return false
        if (BuildConfig.MICROSOFT_CLIENT_ID.isBlank()) return false
        PojlibRuntime.initialize(hostActivity)
        return LoginHelper.startLogin(hostActivity, BuildConfig.MICROSOFT_CLIENT_ID)
    }

    @UsedByGodot
    fun cancelMicrosoftLogin() {
        LoginHelper.cancelLogin()
    }

    @UsedByGodot
    fun getMicrosoftLoginState(): String = LoginHelper.getStateName()

    @UsedByGodot
    fun getMicrosoftLoginMessage(): String = LoginHelper.getMessage()

    @UsedByGodot
    fun getMicrosoftLoginError(): String = LoginHelper.getError()

    @UsedByGodot
    fun getMicrosoftDeviceCode(): String = LoginHelper.getDeviceUserCode()

    @UsedByGodot
    fun getMicrosoftVerificationUrl(): String {
        val verificationUrl = LoginHelper.getVerificationUri()
        if (verificationUrl.isNotBlank()) return verificationUrl
        return if (LoginHelper.getDeviceUserCode().isNotBlank()) {
            MICROSOFT_DEVICE_LOGIN_FALLBACK
        } else {
            ""
        }
    }

    @UsedByGodot
    fun getMicrosoftLoginExpiresIn(): Int =
        LoginHelper.getDeviceCodeExpiresInSeconds().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    @UsedByGodot
    fun isMicrosoftSignedIn(): Boolean = LoginHelper.isSignedIn()

    @UsedByGodot
    fun getMicrosoftProfileName(): String = LoginHelper.getProfileName()

    @UsedByGodot
    fun getMicrosoftProfileUuid(): String = LoginHelper.getProfileUuid()

    @UsedByGodot
    fun isMicrosoftDemoMode(): Boolean = LoginHelper.isDemoMode()

    @UsedByGodot
    fun openMicrosoftLoginPage(): Boolean {
        val hostActivity = activity ?: return false
        val url = getMicrosoftVerificationUrl()
        if (url.isBlank()) return false
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return false
        if (uri.scheme != "https" || !(host == "microsoft.com" || host.endsWith(".microsoft.com") || host == "microsoftonline.com" || host.endsWith(".microsoftonline.com") || host == "live.com" || host.endsWith(".live.com"))) return false
        return runCatching {
            hostActivity.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            true
        }.getOrDefault(false)
    }

    /** Read registry metadata without creating or rewriting instances.json. */
    @UsedByGodot
    fun getInstancesSnapshotJson(): String {
        val result = JSONObject().put("available", PojlibRuntime.isInitialized())
            .put("instances", JSONArray()).put("error", "")
        if (!PojlibRuntime.isInitialized()) return result.toString()
        return runCatching {
            val registry = File(Constants.USER_HOME, "instances.json")
            if (registry.isFile) {
                val saved = GsonUtils.jsonFileToObject(registry.path, MinecraftInstances::class.java)
                    ?: error("Invalid instance registry")
                val items = JSONArray()
                for (instance in saved.toArray()) {
                    items.put(JSONObject()
                        .put("name", instance.instanceName ?: "Unnamed instance")
                        .put("version", instance.versionName ?: "")
                        .put("loader", instance.loaderId())
                        .put("installed", VoxyQuestInstaller.isInstalled(instance)))
                }
                result.put("instances", items)
            }
            result.toString()
        }.getOrElse {
            result.put("error", "Could not read saved instances").toString()
        }
    }

    @UsedByGodot
    fun getInstallVersionsJson(): String = activity?.let { LauncherOperations.versions(it) } ?: "[]"

    @UsedByGodot
    fun installInstance(name: String, version: String): Boolean {
        val host = activity ?: return false
        // VoxyQuestInstaller.install() calls PojlibRuntime.ensureInitialized(activity)
        // on the install worker. Do not reject the click merely because launcher
        // initialization has not completed yet.
        return LauncherOperations.install(host, name, version)
    }

    @UsedByGodot
    fun installInstanceWithLoader(name: String, version: String, loader: String): Boolean {
        val host = activity ?: return false
        return LauncherOperations.install(host, name, version, loader)
    }

    @UsedByGodot
    fun getInstallSnapshotJson(): String = LauncherOperations.snapshot()

    @UsedByGodot
    fun renameInstance(oldName: String, newName: String): Boolean {
        if (!PojlibRuntime.isInitialized()) return false
        return LauncherOperations.renameInstance(oldName, newName)
    }

    @UsedByGodot
    fun removeInstance(name: String): Boolean {
        if (!PojlibRuntime.isInitialized()) return false
        return LauncherOperations.removeInstance(name)
    }

    @UsedByGodot
    fun getInstanceModsJson(name: String): String {
        if (!PojlibRuntime.isInitialized()) {
            return JSONObject().put("available", false).put("mods", JSONArray())
                .put("error", "Android runtime unavailable").toString()
        }
        return LauncherOperations.mods(name)
    }

    @UsedByGodot
    fun getModrinthSnapshotJson(): String = LauncherOperations.modrinthSnapshot()

    @UsedByGodot
    fun searchModrinthMods(instanceName: String, query: String, sort: String, category: String): Boolean {
        if (!PojlibRuntime.isInitialized()) return false
        return LauncherOperations.searchModrinth(instanceName, query, sort, category)
    }

    @UsedByGodot
    fun installModrinthMod(instanceName: String, projectId: String): Boolean {
        if (!PojlibRuntime.isInitialized()) return false
        return LauncherOperations.installModrinth(instanceName, projectId)
    }

    @UsedByGodot
    fun launchMinecraftVr(name: String): Boolean = launchMinecraft(name, true)

    @UsedByGodot
    fun launchMinecraftFlat(name: String): Boolean = launchMinecraft(name, false)

    @UsedByGodot
    fun addInstanceMod(name: String): Boolean {
        val host = activity ?: return false
        if (!PojlibRuntime.isInitialized() || LauncherOperations.isBusy() || MinecraftGameActivity.isRunning) return false
        return runCatching {
            check(VoxyQuestInstaller.readRegistry().toArray().any { it.instanceName == name })
            host.startActivity(Intent(host, ModImportActivity::class.java).putExtra("instance_name", name))
            true
        }.getOrDefault(false)
    }

    private fun launchMinecraft(name: String, vr: Boolean): Boolean {
        val host = activity ?: return false
        if (!LoginHelper.isSignedIn() || LauncherOperations.isBusy() || MinecraftGameActivity.isRunning) return false
        return runCatching {
            val instance = VoxyQuestInstaller.readRegistry().toArray().firstOrNull { it.instanceName == name }
                ?: return false
            if (!VoxyQuestInstaller.isInstalled(instance)) return false
            val intent = Intent(
                host,
                if (vr) MinecraftGameActivity::class.java else MinecraftFlatActivity::class.java,
            ).putExtra("instance_name", name)
            host.startActivity(intent)
            // Keep the Godot host Activity alive behind Minecraft. Finishing the Godot Activity
            // tears down the process on Android, which also terminates the Minecraft Activity.
            true
        }.getOrDefault(false)
    }

    @UsedByGodot
    fun sendKey(keyCode: Int, pressed: Boolean) {
        CallbackBridge.sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), pressed)
    }

    @UsedByGodot
    fun sendMouseButton(button: Int, pressed: Boolean) {
        CallbackBridge.sendMouseButton(button, pressed)
    }

    @UsedByGodot
    fun sendCursorPosition(x: Float, y: Float) {
        CallbackBridge.sendCursorPos(x, y)
    }

    @UsedByGodot
    fun sendScroll(x: Float, y: Float) {
        CallbackBridge.sendScroll(x.toDouble(), y.toDouble())
    }
}
