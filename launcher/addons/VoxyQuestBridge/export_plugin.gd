@tool
extends EditorPlugin

var _export_plugin: EditorExportPlugin

func _enter_tree() -> void:
	_export_plugin = VoxyQuestAndroidExportPlugin.new()
	add_export_plugin(_export_plugin)

func _exit_tree() -> void:
	if _export_plugin:
		remove_export_plugin(_export_plugin)
	_export_plugin = null

class VoxyQuestAndroidExportPlugin extends EditorExportPlugin:
	const PLUGIN_NAME := "VoxyQuestBridge"

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		return platform is EditorExportPlatformAndroid

	func _get_android_libraries(_platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		var variant := "debug" if debug else "release"
		return PackedStringArray([PLUGIN_NAME + "/bin/" + variant + "/" + PLUGIN_NAME + "-" + variant + ".aar"])

	func _get_name() -> String:
		return PLUGIN_NAME
