package dev.voxyquest.bridge

import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class VoxyQuestBridgePlugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName(): String = BuildConfig.GODOT_PLUGIN_NAME

    @UsedByGodot
    fun getBridgeVersion(): String = "0.1.0"

    @UsedByGodot
    fun getHostEngine(): String = "Godot"

    @UsedByGodot
    fun isAndroidRuntimeReady(): Boolean = activity != null

    @UsedByGodot
    fun getPojlibCompatibilityState(): String = "host_adapter_required"
}
