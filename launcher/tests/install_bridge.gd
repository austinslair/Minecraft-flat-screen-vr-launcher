extends SceneTree

class InstallPlugin extends RefCounted:
	var install_called := false

	func initializePojlib() -> bool:
		return false

	func installInstance(_name: String, _version: String) -> bool:
		install_called = true
		return true

func _initialize() -> void:
	var bridge := VoxyQuestRuntimeBridge.new()
	var plugin := InstallPlugin.new()
	bridge._plugin = plugin
	assert(bridge.install_instance("Minecraft 1-21-5", "1.21.5"))
	assert(plugin.install_called)
	print("PASS: install request reaches Android even before launcher-side Pojlib init")
	quit()
