extends Node
## Quest one-click install UX and live installer status.

var _active_launcher: Node = null
var _active_button: Button = null
var _status_timer: Timer
var _starting_checks := 0

func _ready() -> void:
	if OS.get_name() != "Android":
		return
	_status_timer = Timer.new()
	_status_timer.wait_time = 0.35
	_status_timer.one_shot = false
	_status_timer.timeout.connect(_poll_install_state)
	add_child(_status_timer)
	get_tree().node_added.connect(_on_node_added)

func _on_node_added(node: Node) -> void:
	if node is Button:
		call_deferred("_maybe_wire_install_button", node)

func _maybe_wire_install_button(button: Button) -> void:
	if not is_instance_valid(button) or button.text != "Install":
		return
	if button.has_meta("voxyquest_one_click_install"):
		return
	var launcher := get_tree().current_scene
	if launcher == null or not launcher.has_method("_start_install"):
		return
	button.set_meta("voxyquest_one_click_install", true)
	button.text = "Install & select"
	button.tooltip_text = "Download Minecraft, Fabric, Vivecraft, required mods and Java, then select the instance."
	button.button_down.connect(_prepare_install.bind(button))
	var name_field: Variant = launcher.get("install_name")
	if name_field is LineEdit:
		name_field.placeholder_text = "Optional — automatic name"

func _prepare_install(button: Button) -> void:
	var launcher := get_tree().current_scene
	if launcher == null:
		return
	var name_field: Variant = launcher.get("install_name")
	var version_picker: Variant = launcher.get("install_version")
	if not name_field is LineEdit or not version_picker is OptionButton:
		return
	if version_picker.item_count == 0:
		_set_status(launcher, "No supported Minecraft versions are available.")
		return
	if name_field.text.strip_edges().is_empty():
		var selected := version_picker.selected
		if selected < 0:
			selected = 0
		var version := version_picker.get_item_text(selected)
		name_field.text = "Minecraft %s" % version.replace(".", "-")

	_active_launcher = launcher
	_active_button = button
	_starting_checks = 0
	button.text = "Starting…"
	_set_status(launcher, "Starting installation…")
	if _status_timer != null:
		_status_timer.start()

func _poll_install_state() -> void:
	if not is_instance_valid(_active_launcher):
		_stop_tracking()
		return
	var runtime: Variant = _active_launcher.get("runtime")
	if runtime == null or not runtime.has_method("get_install_snapshot"):
		_set_status(_active_launcher, "Installer status is unavailable.")
		_stop_tracking()
		return
	var snapshot: Dictionary = runtime.get_install_snapshot()
	var state := str(snapshot.get("state", "idle"))
	var message := str(snapshot.get("message", "")).strip_edges()
	match state:
		"installing":
			_starting_checks = 0
			if is_instance_valid(_active_button):
				_active_button.text = "Installing…"
			_set_status(_active_launcher, "Installing — %s" % (message if not message.is_empty() else "working…"))
		"installed":
			if is_instance_valid(_active_button):
				_active_button.text = "Installed"
			_set_status(_active_launcher, message if not message.is_empty() else "Installed. Instance is ready.")
			_stop_tracking()
		"error":
			if is_instance_valid(_active_button):
				_active_button.text = "Retry install"
			_set_status(_active_launcher, message if not message.is_empty() else "Installation failed. Tap Retry install.")
			_stop_tracking()
		_:
			_starting_checks += 1
			if _starting_checks >= 8:
				if is_instance_valid(_active_button):
					_active_button.text = "Retry install"
				_set_status(_active_launcher, "Install did not start. Tap Retry install.")
				_stop_tracking()
			else:
				_set_status(_active_launcher, "Starting installation…")

func _set_status(launcher: Node, text: String) -> void:
	if not is_instance_valid(launcher):
		return
	var status: Variant = launcher.get("install_status")
	if status is Label:
		status.text = text

func _stop_tracking() -> void:
	if _status_timer != null:
		_status_timer.stop()
	_active_launcher = null
	_active_button = null
	_starting_checks = 0
