class_name VoxyQuestRuntimeBridge
extends RefCounted

const PLUGIN_NAME := "VoxyQuestBridge"
const BUNDLED_INSTALL_VERSIONS := [
	"1.21.5",
	"1.21.4",
	"1.21.1",
	"1.20.6",
	"1.20.4",
	"1.20.1",
	"1.19.4",
	"1.19.2",
]

var _plugin: Object = null
var _install_request_error := ""

func _init() -> void:
	_refresh_plugin()

## Android plugins can finish registering after this RefCounted is constructed.
## Re-check the singleton instead of permanently caching an early null result.
func _refresh_plugin() -> Object:
	if _plugin == null and Engine.has_singleton(PLUGIN_NAME):
		_plugin = Engine.get_singleton(PLUGIN_NAME)
	return _plugin

func is_available() -> bool:
	# On Android, keep launcher actions available while the Godot plugin finishes
	# attaching. Individual actions still verify the singleton before invoking it.
	return _refresh_plugin() != null or OS.get_name() == "Android"

func initialize() -> bool:
	var plugin: Object = _refresh_plugin()
	if plugin == null:
		return false
	return bool(plugin.initializePojlib())

func get_info() -> Dictionary:
	var plugin: Object = _refresh_plugin()
	if plugin == null:
		return {
			"available": false,
			"engine": "Godot",
			"bridge_version": "none",
			"pojlib": "not_loaded"
		}

	return {
		"available": true,
		"engine": str(plugin.getHostEngine()),
		"bridge_version": str(plugin.getBridgeVersion()),
		"pojlib": str(plugin.getPojlibCompatibilityState())
	}

func is_microsoft_login_configured() -> bool:
	var plugin: Object = _refresh_plugin()
	return plugin != null and bool(plugin.isMicrosoftLoginConfigured())

func start_microsoft_login() -> bool:
	var plugin: Object = _refresh_plugin()
	if plugin == null:
		return false
	return bool(plugin.startMicrosoftLogin())

func cancel_microsoft_login() -> void:
	var plugin: Object = _refresh_plugin()
	if plugin != null:
		plugin.cancelMicrosoftLogin()

func open_microsoft_login_page() -> bool:
	var plugin: Object = _refresh_plugin()
	if plugin == null:
		return false
	return bool(plugin.openMicrosoftLoginPage())

func get_microsoft_login_snapshot() -> Dictionary:
	var plugin: Object = _refresh_plugin()
	if plugin == null:
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
		"configured": bool(plugin.isMicrosoftLoginConfigured()),
		"state": str(plugin.getMicrosoftLoginState()),
		"message": str(plugin.getMicrosoftLoginMessage()),
		"error": str(plugin.getMicrosoftLoginError()),
		"device_code": str(plugin.getMicrosoftDeviceCode()),
		"verification_url": str(plugin.getMicrosoftVerificationUrl()),
		"expires_in": int(plugin.getMicrosoftLoginExpiresIn()),
		"signed_in": bool(plugin.isMicrosoftSignedIn()),
		"profile_name": str(plugin.getMicrosoftProfileName()),
		"profile_uuid": str(plugin.getMicrosoftProfileUuid()),
		"demo_mode": bool(plugin.isMicrosoftDemoMode())
	}

func send_key(key_code: int, pressed: bool) -> void:
	var plugin: Object = _refresh_plugin()
	if plugin != null:
		plugin.sendKey(key_code, pressed)

func send_mouse_button(button: int, pressed: bool) -> void:
	var plugin: Object = _refresh_plugin()
	if plugin != null:
		plugin.sendMouseButton(button, pressed)

func send_cursor_position(x: float, y: float) -> void:
	var plugin: Object = _refresh_plugin()
	if plugin != null:
		plugin.sendCursorPosition(x, y)

func send_scroll(x: float, y: float) -> void:
	var plugin: Object = _refresh_plugin()
	if plugin != null:
		plugin.sendScroll(x, y)

## Read-only metadata; tokens and filesystem paths stay on Android.
func get_instance_snapshot() -> Dictionary:
	var plugin: Object = _refresh_plugin()
	if plugin == null or not plugin.has_method("getInstancesSnapshotJson"):
		return {"available": false, "instances": [], "error": ""}
	var parsed: Variant = JSON.parse_string(str(plugin.getInstancesSnapshotJson()))
	if not parsed is Dictionary or not parsed.get("instances", null) is Array:
		return {"available": true, "instances": [], "error": "Invalid instance response"}
	return parsed

func get_install_versions() -> Array:
	var plugin: Object = _refresh_plugin()
	if plugin == null or not plugin.has_method("getInstallVersionsJson"):
		return BUNDLED_INSTALL_VERSIONS.duplicate()
	var json := JSON.new()
	if json.parse(str(plugin.getInstallVersionsJson())) == OK:
		var parsed: Variant = json.data
		if parsed is Array and not parsed.is_empty():
			return parsed
	# The same versions are bundled in assets/voxyquest/runtime_mods.json. Keep the
	# installer usable if the Android bridge returns an empty catalog response.
	return BUNDLED_INSTALL_VERSIONS.duplicate()

func install_instance(instance_name: String, version: String) -> bool:
	var plugin: Object = _refresh_plugin()
	_install_request_error = ""
	if plugin == null:
		_install_request_error = "The Android runtime is missing or has not loaded. Install an APK that includes VoxyQuestBridge and Pojlib."
		return false
	if not plugin.has_method("installInstance"):
		_install_request_error = "This APK contains an older runtime without instance installation. Update the complete APK."
		return false
	# Do not block the request on launcher-side initialization. The Android
	# installer worker initializes Pojlib itself before downloading anything.
	return bool(plugin.installInstance(instance_name, version))

func get_install_snapshot() -> Dictionary:
	if not _install_request_error.is_empty():
		return {"state": "error", "message": _install_request_error}
	var plugin: Object = _refresh_plugin()
	if plugin == null or not plugin.has_method("getInstallSnapshotJson"):
		if OS.get_name() == "Android":
			return {"state": "idle", "message": "Android installer not connected. Tap Install to check again, or update the complete APK."}
		return {"state": "unavailable", "message": "Installation is available in the Android launcher."}
	var parsed: Variant = JSON.parse_string(str(plugin.getInstallSnapshotJson()))
	return parsed if parsed is Dictionary else {"state": "error", "message": "Invalid installer response."}

func rename_instance(old_name: String, new_name: String) -> bool:
	var plugin: Object = _refresh_plugin()
	return plugin != null and plugin.has_method("renameInstance") and bool(plugin.renameInstance(old_name, new_name))

func remove_instance(instance_name: String) -> bool:
	var plugin: Object = _refresh_plugin()
	return plugin != null and plugin.has_method("removeInstance") and bool(plugin.removeInstance(instance_name))

func get_instance_mods(instance_name: String) -> Dictionary:
	var plugin: Object = _refresh_plugin()
	if plugin == null or not plugin.has_method("getInstanceModsJson"):
		return {"available": false, "mods": [], "error": "Android runtime unavailable"}
	var parsed: Variant = JSON.parse_string(str(plugin.getInstanceModsJson(instance_name)))
	if not parsed is Dictionary or not parsed.get("mods", null) is Array:
		return {"available": true, "mods": [], "error": "Invalid mods response"}
	return parsed

func launch_minecraft_vr(instance_name: String) -> bool:
	var plugin: Object = _refresh_plugin()
	return plugin != null and plugin.has_method("launchMinecraftVr") and bool(plugin.launchMinecraftVr(instance_name))

func launch_minecraft_flat(instance_name: String) -> bool:
	var plugin: Object = _refresh_plugin()
	return plugin != null and plugin.has_method("launchMinecraftFlat") and bool(plugin.launchMinecraftFlat(instance_name))

func add_instance_mod(instance_name: String) -> bool:
	var plugin: Object = _refresh_plugin()
	return plugin != null and plugin.has_method("addInstanceMod") and bool(plugin.addInstanceMod(instance_name))
