package dev.voxyquest.bridge

import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.lwjgl.glfw.CallbackBridge
import pojlib.PojlibRuntime

class VoxyQuestBridgePlugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName(): String = BuildConfig.GODOT_PLUGIN_NAME

    @UsedByGodot
    fun getBridgeVersion(): String = "0.2.0"

    @UsedByGodot
    fun getHostEngine(): String = "Godot"

    @UsedByGodot
    fun isAndroidRuntimeReady(): Boolean = activity != null

    @UsedByGodot
    fun initializePojlib(): Boolean {
        val hostActivity = activity ?: return false
        return runCatching {
            PojlibRuntime.initialize(hostActivity)
            PojlibRuntime.isInitialized()
        }.getOrDefault(false)
    }

    @UsedByGodot
    fun getPojlibCompatibilityState(): String =
        if (PojlibRuntime.isInitialized()) "godot_host_ready" else "not_initialized"

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
