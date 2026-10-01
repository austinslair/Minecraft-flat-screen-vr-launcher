extends SceneTree

class InstallPlugin extends RefCounted:
	var install_called := false

	func initializePojlib() -> bool:
		return false

	func installInstance(_name: String, _version: String) -> bool:
		install_called = true
		return true

class JavaMethodProbe extends RefCounted:
	func has_java_method(method_name: StringName) -> bool:
		return method_name == &"installInstance"

func _initialize() -> void:
	var bridge := VoxyQuestRuntimeBridge.new()

	# Android plugin methods are Java methods on JNISingleton and are not reported
	# by Object.has_method(). Verify the bridge prefers has_java_method() when present.
	var java_probe := JavaMethodProbe.new()
	assert(not java_probe.has_method("installInstance"))
	assert(bridge._plugin_has_method(java_probe, &"installInstance"))
	assert(not bridge._plugin_has_method(java_probe, &"removeInstance"))

	var plugin := InstallPlugin.new()
	bridge._plugin = plugin
	assert(bridge.install_instance("Minecraft 1-21-5", "1.21.5"))
	assert(plugin.install_called)
	print("PASS: Java Android methods are detected and install requests reach the bridge")
	quit()
