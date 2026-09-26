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
	const POJLIB_NAME := "PojlibRuntime"

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		return platform is EditorExportPlatformAndroid

	func _get_android_libraries(_platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		var variant := "debug" if debug else "release"
		return PackedStringArray([
			PLUGIN_NAME + "/bin/" + variant + "/" + PLUGIN_NAME + "-" + variant + ".aar",
			PLUGIN_NAME + "/bin/" + variant + "/" + POJLIB_NAME + "-" + variant + ".aar"
		])

	func _get_android_dependencies(_platform: EditorExportPlatform, _debug: bool) -> PackedStringArray:
		return PackedStringArray([
			"org.apache.commons:commons-math3:3.6.1",
			"org.ow2.asm:asm:9.7.1",
			"com.google.guava:guava:31.0.1-jre",
			"org.jetbrains:annotations:24.0.1",
			"com.google.code.gson:gson:2.12.1",
			"org.json:json:20220924",
			"commons-io:commons-io:2.13.0",
			"commons-codec:commons-codec:1.15",
			"androidx.annotation:annotation:1.7.1",
			"androidx.core:core:1.13.1",
			"com.microsoft.azure:msal4j:1.17.2",
			"com.github.Mathias-Boulay:android_gamepad_remapper:2.0.3"
		])

	func _get_android_dependencies_maven_repos(_platform: EditorExportPlatform, _debug: bool) -> PackedStringArray:
		return PackedStringArray(["https://jitpack.io"])

	func _get_name() -> String:
		return PLUGIN_NAME
