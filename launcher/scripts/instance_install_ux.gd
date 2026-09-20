extends Node
## Quest-only one-click instance install helper.
## The existing launcher installer remains the source of truth; this only fills a
## safe default instance name before its Install callback runs.

func _ready() -> void:
	if OS.get_name() != "Android":
		return
	get_tree().node_added.connect(_on_node_added)

func _on_node_added(node: Node) -> void:
	if not node is Button:
		return
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
	button.tooltip_text = "Download this Minecraft version and select it when installation finishes."
	button.button_down.connect(_prepare_install.bind(button))
	var name_field: Variant = launcher.get("install_name")
	if name_field is LineEdit:
		name_field.placeholder_text = "Optional — automatic name"

func _prepare_install(_button: Button) -> void:
	var launcher := get_tree().current_scene
	if launcher == null:
		return
	var name_field: Variant = launcher.get("install_name")
	var version_picker: Variant = launcher.get("install_version")
	if not name_field is LineEdit or not version_picker is OptionButton:
		return
	if not name_field.text.strip_edges().is_empty() or version_picker.item_count == 0:
		return
	var selected := version_picker.selected
	if selected < 0:
		selected = 0
	var version := version_picker.get_item_text(selected)
	# VoxyQuest instance names accept letters, numbers, spaces, underscores and hyphens.
	name_field.text = "Minecraft %s" % version.replace(".", "-")
