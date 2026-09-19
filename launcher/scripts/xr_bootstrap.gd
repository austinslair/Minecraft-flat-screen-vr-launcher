extends Node3D

@onready var status_label: Label3D = $XROrigin3D/StatusLabel

var runtime_bridge := VoxyQuestRuntimeBridge.new()

func _ready() -> void:
	Engine.physics_ticks_per_second = 90

	var xr_interface := XRServer.find_interface("OpenXR")
	var xr_ready := xr_interface != null and xr_interface.is_initialized()

	if xr_ready:
		get_viewport().use_xr = true

	var pojlib_ready := runtime_bridge.initialize()
	var bridge_info := runtime_bridge.get_info()
	var xr_text := "OpenXR ready" if xr_ready else "OpenXR unavailable"
	var bridge_text := "Godot + Pojlib ready" if pojlib_ready else "Android runtime not ready"

	status_label.text = "VoxyQuest\n%s\n%s" % [xr_text, bridge_text]
	print("VoxyQuest XR: ", xr_text)
	print("VoxyQuest bridge: ", bridge_info)
