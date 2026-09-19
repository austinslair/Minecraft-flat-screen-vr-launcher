package dev.voxyquest.bridge

import android.content.Intent
import android.net.Uri
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.lwjgl.glfw.CallbackBridge
import pojlib.PojlibRuntime
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
        if (url.isBlank()) return false
        return runCatching {
            hostActivity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
