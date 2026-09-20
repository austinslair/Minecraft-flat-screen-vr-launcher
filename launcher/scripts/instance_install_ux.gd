extends Node
## Quest install-button shim.
## Starts the Android installer immediately on button-down and keeps visible status
## on screen so a failed bridge call can never look like a dead button.

var _launcher: Node = null
var _install_button: Button = null
var _last_start_ms := 0

func _ready() -> void:
	if OS.get_name() != "Android":
		return
	process_mode = Node.PROCESS_MODE_ALWAYS
	set_process(true)
	get_tree().node_added.connect(_on_node_added)
	call_deferred("_scan_existing_buttons")

func _scan_existing_buttons() -> void:
	var scene := get_tree().current_scene
	if scene == null:
		return
	for node in scene.find_children("*", "Button", true, false):
		_maybe_wire_install_button(node)

func _on_node_added(node: Node) -> void:
	if node is Button:
		call_deferred("_maybe_wire_install_button", node)

func _maybe_wire_install_button(button: Button) -> void:
	if not is_instance_valid(button):
		return
	if button.text not in ["Install", "Install & select", "Retry install"]:
		return
	var launcher := get_tree().current_scene
	if launcher == null or not launcher.has_method("_start_install"):
		return
	_launcher = launcher
	_install_button = button
	if button.has_meta("voxyquest_direct_install"):
		return
	button.set_meta("voxyquest_direct_install", true)
	button.text = "Install & select"
	button.tooltip_text = "Download this Minecraft version. Live progress appears below."
	button.button_down.connect(_start_from_button)
	var name_field: Variant = launcher.get("install_name")
	if name_field is LineEdit:
		name_field.placeholder_text = "Optional — automatic name"

func _process(_delta: float) -> void:
	if not is_instance_valid(_launcher) or not is_instance_valid(_install_button):
		return
	var runtime_bridge: Variant = _launcher.get("runtime")
	if runtime_bridge == null or not runtime_bridge.has_method("get_install_snapshot"):
		return
	var snapshot: Dictionary = runtime_bridge.get_install_snapshot()
	var state := str(snapshot.get("state", "idle"))
	var message := str(snapshot.get("message", ""))
	var status: Variant = _launcher.get("install_status")
	var busy := state == "installing"
	_install_button.disabled = busy
	if busy:
		_install_button.text = "Installing…"
		if status is Label:
			status.text = message if not message.is_empty() else "Installation is running…"
	elif state == "installed":
		_install_button.text = "Installed"
		if status is Label and not message.is_empty():
			status.text = message
	elif state == "error":
		_install_button.text = "Retry install"
		if status is Label:
			status.text = message if not message.is_empty() else "Installation failed. Tap Retry install."
	elif Time.get_ticks_msec() - _last_start_ms > 1200:
		_install_button.text = "Install & select"

func _start_from_button() -> void:
	var launcher := get_tree().current_scene
	if launcher == null:
		return
	_launcher = launcher
	var name_field: Variant = launcher.get("install_name")
	var version_picker: Variant = launcher.get("install_version")
	var status: Variant = launcher.get("install_status")
	var runtime_bridge: Variant = launcher.get("runtime")
	if not name_field is LineEdit or not version_picker is OptionButton:
		if status is Label:
			status.text = "Installer controls are not ready. Reopen Instances and try again."
		return
	if version_picker.item_count == 0:
		if status is Label:
			status.text = "No installable Minecraft versions were loaded."
		return
	var selected := version_picker.selected
	if selected < 0:
		selected = 0
		version_picker.select(0)
	var version := version_picker.get_item_text(selected)
	if name_field.text.strip_edges().is_empty():
		name_field.text = "Minecraft %s" % version.replace(".", "-")
	var instance_name := name_field.text.strip_edges()
	_last_start_ms = Time.get_ticks_msec()
	if is_instance_valid(_install_button):
		_install_button.text = "Starting…"
		_install_button.disabled = false
	if status is Label:
		status.text = "Starting installation for Minecraft %s…" % version
	if runtime_bridge == null or not runtime_bridge.has_method("install_instance"):
		if status is Label:
			status.text = "Android installer bridge is missing from this APK."
		if is_instance_valid(_install_button):
			_install_button.text = "Retry install"
		return
	var started := bool(runtime_bridge.install_instance(instance_name, version))
	if started:
		if status is Label:
			status.text = "Installer started. Waiting for download progress…"
		var timer: Variant = launcher.get("install_timer")
		if timer is Timer:
			timer.start()
	else:
		# Preserve this message; home.gd may poll an empty snapshot in the same frame.
		if status is Label:
			status.text = "Installer did not start. Tap Retry install."
		if is_instance_valid(_install_button):
			_install_button.text = "Retry install"
