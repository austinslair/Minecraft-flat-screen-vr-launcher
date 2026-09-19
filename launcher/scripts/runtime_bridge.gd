class_name VoxyQuestRuntimeBridge
extends RefCounted

const PLUGIN_NAME := "VoxyQuestBridge"

var _plugin: Object = null

func _init() -> void:
	if Engine.has_singleton(PLUGIN_NAME):
		_plugin = Engine.get_singleton(PLUGIN_NAME)

func is_available() -> bool:
	return _plugin != null

func get_info() -> Dictionary:
	if _plugin == null:
		return {
			"available": false,
			"engine": "Godot",
			"bridge_version": "none",
			"pojlib": "adapter_pending"
		}

	return {
		"available": true,
		"engine": str(_plugin.getHostEngine()),
		"bridge_version": str(_plugin.getBridgeVersion()),
		"pojlib": str(_plugin.getPojlibCompatibilityState())
	}
