package dev.voxyquest.bridge

import android.content.Intent
import android.net.Uri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import pojlib.util.Constants
import pojlib.util.GsonUtils
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

    override fun getPluginName(): String = BuildConfig.GODOT_PLUGIN_NAME

    @UsedByGodot
    fun getBridgeVersion(): String = "0.4.0"

    @UsedByGodot
    fun getHostEngine(): String = "Godot"

    @UsedByGodot
    fun isAndroidRuntimeReady(): Boolean = activity != null

    @UsedByGodot
    fun initializePojlib(): Boolean {
        val hostActivity = activity ?: return false
        return runCatching {
            PojlibRuntime.initialize(hostActivity)
            if (!accountRestoreRequested && BuildConfig.MICROSOFT_CLIENT_ID.isNotBlank()) {
                accountRestoreRequested = LoginHelper.restoreSession(
                    hostActivity,
                    BuildConfig.MICROSOFT_CLIENT_ID,
                )
            }
            PojlibRuntime.isInitialized()
        }.getOrDefault(false)
    }

    @UsedByGodot
    fun getPojlibCompatibilityState(): String =
        if (PojlibRuntime.isInitialized()) "godot_host_ready" else "not_initialized"

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
    fun getMicrosoftVerificationUrl(): String = LoginHelper.getVerificationUri()

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
        val url = LoginHelper.getVerificationUri()
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
        if (!PojlibRuntime.isInitialized()) return false
        return LauncherOperations.install(host, name, version)
    }

    @UsedByGodot
    fun getInstallSnapshotJson(): String = LauncherOperations.snapshot()

    @UsedByGodot
    fun launchMinecraftVr(name: String): Boolean {
        val host = activity ?: return false
        if (!LoginHelper.isSignedIn() || LauncherOperations.isBusy() || MinecraftGameActivity.isRunning) return false
        return runCatching {
            val instance = VoxyQuestInstaller.readRegistry().toArray().firstOrNull { it.instanceName == name }
                ?: return false
            if (!VoxyQuestInstaller.isInstalled(instance)) return false
            host.startActivity(Intent(host, MinecraftGameActivity::class.java).putExtra("instance_name", name))
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
