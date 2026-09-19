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

func is_microsoft_login_configured() -> bool:
	return _plugin != null and bool(_plugin.isMicrosoftLoginConfigured())

func start_microsoft_login() -> bool:
	if _plugin == null:
		return false
	return bool(_plugin.startMicrosoftLogin())

func cancel_microsoft_login() -> void:
	if _plugin != null:
		_plugin.cancelMicrosoftLogin()

func open_microsoft_login_page() -> bool:
	if _plugin == null:
		return false
	return bool(_plugin.openMicrosoftLoginPage())

func get_microsoft_login_snapshot() -> Dictionary:
	if _plugin == null:
		return {
			"configured": false,
			"state": "unavailable",
			"message": "Android bridge unavailable.",
			"error": "",
			"device_code": "",
			"verification_url": "",
			"expires_in": 0,
			"signed_in": false,
			"profile_name": "",
			"profile_uuid": "",
			"demo_mode": false
		}

	return {
		"configured": bool(_plugin.isMicrosoftLoginConfigured()),
		"state": str(_plugin.getMicrosoftLoginState()),
		"message": str(_plugin.getMicrosoftLoginMessage()),
		"error": str(_plugin.getMicrosoftLoginError()),
		"device_code": str(_plugin.getMicrosoftDeviceCode()),
		"verification_url": str(_plugin.getMicrosoftVerificationUrl()),
		"expires_in": int(_plugin.getMicrosoftLoginExpiresIn()),
		"signed_in": bool(_plugin.isMicrosoftSignedIn()),
		"profile_name": str(_plugin.getMicrosoftProfileName()),
		"profile_uuid": str(_plugin.getMicrosoftProfileUuid()),
		"demo_mode": bool(_plugin.isMicrosoftDemoMode())
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

## Read-only metadata; tokens and filesystem paths stay on Android.
func get_instance_snapshot() -> Dictionary:
	if _plugin == null or not _plugin.has_method("getInstancesSnapshotJson"):
		return {"available": false, "instances": [], "error": ""}
	var parsed: Variant = JSON.parse_string(str(_plugin.getInstancesSnapshotJson()))
	if not parsed is Dictionary or not parsed.get("instances", null) is Array:
		return {"available": true, "instances": [], "error": "Invalid instance response"}
	return parsed

func get_install_versions() -> Array:
	if _plugin == null or not _plugin.has_method("getInstallVersionsJson"):
		return []
	var parsed: Variant = JSON.parse_string(str(_plugin.getInstallVersionsJson()))
	return parsed if parsed is Array else []

func install_instance(instance_name: String, version: String) -> bool:
	return _plugin != null and _plugin.has_method("installInstance") and bool(_plugin.installInstance(instance_name, version))

func get_install_snapshot() -> Dictionary:
	if _plugin == null or not _plugin.has_method("getInstallSnapshotJson"):
		return {"state": "unavailable", "message": "Installation is available in the Android launcher."}
	var parsed: Variant = JSON.parse_string(str(_plugin.getInstallSnapshotJson()))
	return parsed if parsed is Dictionary else {"state": "error", "message": "Invalid installer response."}

func launch_minecraft_vr(instance_name: String) -> bool:
	return _plugin != null and _plugin.has_method("launchMinecraftVr") and bool(_plugin.launchMinecraftVr(instance_name))
