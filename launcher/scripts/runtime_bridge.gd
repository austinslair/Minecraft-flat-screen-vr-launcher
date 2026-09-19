class_name VoxyQuestRuntimeBridge
extends RefCounted

const PLUGIN_NAME := "VoxyQuestBridge"

var _plugin: Object = null

func _init() -> void:
	if Engine.has_singleton(PLUGIN_NAME):
		_plugin = Engine.get_singleton(PLUGIN_NAME)

func is_available() -> bool:
	return _plugin != null

func initialize() -> bool:
	if _plugin == null:
		return false
	return bool(_plugin.initializePojlib())

func get_info() -> Dictionary:
	if _plugin == null:
		return {
			"available": false,
			"engine": "Godot",
			"bridge_version": "none",
			"pojlib": "not_loaded"
		}

	return {
		"available": true,
		"engine": str(_plugin.getHostEngine()),
		"bridge_version": str(_plugin.getBridgeVersion()),
		"pojlib": str(_plugin.getPojlibCompatibilityState())
	}

func send_key(key_code: int, pressed: bool) -> void:
	if _plugin != null:
		_plugin.sendKey(key_code, pressed)

func send_mouse_button(button: int, pressed: bool) -> void:
	if _plugin != null:
		_plugin.sendMouseButton(button, pressed)

func send_cursor_position(x: float, y: float) -> void:
	if _plugin != null:
		_plugin.sendCursorPosition(x, y)

func send_scroll(x: float, y: float) -> void:
	if _plugin != null:
		_plugin.sendScroll(x, y)
