extends Control
## VoxyQuest launcher shell: flat Godot UI backed by the Android/Pojlib runtime.
signal navigation_requested(section: String)
signal launch_requested
signal sign_in_requested
signal install_requested
signal change_instance_requested

const AUTH_POLL_INTERVAL := 0.5
const ACTIVE_AUTH_STATES := ["starting", "waiting_for_user", "exchanging"]
const HOME_CONTENT_NODES := [
	"InstancePanel", "InstanceHeading", "InstanceCard", "ChangeInstance",
	"QuickInfoPanel", "QuickInfoTitle", "NewsPanel", "NewsHeading",
	"InstanceEmpty", "InstanceDescription", "QuickEmpty", "NewsEmpty",
	"NewsDescription", "Play", "FeatureBanner"
]

var nav_buttons: Array[Button] = []
var runtime: RefCounted = VoxyQuestRuntimeBridge.new()
var selected_name := ""
var installed_instances: Array = []
var signed_in := false
var install_busy := false
var current_section := "Home"

var account_code: Label
var _auth_poll_elapsed := 0.0
var _open_browser_when_ready := false

var workspace: Panel
var workspace_title: Label
var workspace_subtitle: Label
var workspace_body: VBoxContainer

var instance_list: ItemList
var instance_rename: LineEdit
var instance_rename_button: Button
var instance_remove_button: Button
var instance_status: Label
var instance_notice := ""
var pending_remove_name := ""
var remove_confirm: ConfirmationDialog

var install_name: LineEdit
var install_version: OptionButton
var install_submit: Button
var install_status: Label
var install_timer: Timer
var handled_install_name := ""

var mods_list: ItemList
var mods_status: Label

var account_page_title: Label
var account_page_status: Label
var account_page_code: LineEdit
var account_page_action: Button
var account_page_copy: Button
var account_page_cancel: Button

var settings_status: Label

func _ready() -> void:
	set_process(false)
	for section in ["Home", "Instances", "Mods", "Accounts", "Settings"]:
		var button := get_node(section) as Button
		nav_buttons.append(button)
		button.pressed.connect(_navigate.bind(button))

	_build_workspace()
	_build_inline_account_status()
	_build_remove_confirmation()
	_build_install_timer()

	for item in find_children("*", "Button", true, false):
		style_button(item)

	_select_nav($Home)
	if has_node("AccountWindow"):
		$AccountWindow.queue_free()
	if runtime.is_available():
		runtime.initialize()
	_refresh_auth_ui()
	_refresh_instances()

	if OS.get_name() == "Android":
		$Minimize.hide()
		$Close.hide()

	$ChangeInstance.pressed.connect(_open_section.bind("Instances"))
	$Account.pressed.connect(_on_account_pressed)
	$Play.pressed.connect(_on_play_pressed)
	$Close.pressed.connect(func(): get_tree().quit())
	$Minimize.pressed.connect(func(): DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_MINIMIZED))

func _process(delta: float) -> void:
	_auth_poll_elapsed += delta
	if _auth_poll_elapsed >= AUTH_POLL_INTERVAL:
		_auth_poll_elapsed = 0.0
		_refresh_auth_ui()

func _set_auth_polling(enabled: bool) -> void:
	if is_processing() == enabled:
		return
	_auth_poll_elapsed = 0.0
	set_process(enabled)

func style_box(fill: Color, border: Color, radius := 8) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = fill
	box.border_color = border
	box.set_border_width_all(1)
	box.set_corner_radius_all(radius)
	return box

func style_button(button: Button) -> void:
	button.add_theme_stylebox_override("normal", style_box(Color(0, 0, 0, 0), Color(0, 0, 0, 0)))
	button.add_theme_stylebox_override("hover", style_box(Color(0.85, 0.88, 0.86, 0.07), Color(0.8, 0.85, 0.81, 0.22)))
	button.add_theme_stylebox_override("pressed", style_box(Color(0.8, 0.85, 0.82, 0.12), Color(0.8, 0.85, 0.81, 0.35)))
	button.add_theme_stylebox_override("focus", style_box(Color(0, 0, 0, 0), Color(0.85, 0.9, 0.87, 0.8)))
	button.add_theme_color_override("font_color", Color(0.95, 0.97, 0.94, 1))
	if button == $ChangeInstance:
		_style_primary_button(button)

func _style_primary_button(button: Button) -> void:
	button.add_theme_stylebox_override("normal", style_box(Color(0.08, 0.25, 0.08, 0.94), Color(0.55, 0.86, 0.36, 0.9)))
	button.add_theme_stylebox_override("hover", style_box(Color(0.11, 0.32, 0.1, 0.98), Color(0.65, 0.94, 0.43, 1)))
	button.add_theme_stylebox_override("pressed", style_box(Color(0.055, 0.19, 0.055, 1), Color(0.48, 0.78, 0.31, 1)))
	button.add_theme_color_override("font_color", Color(0.84, 1.0, 0.72, 1))

func _style_danger_button(button: Button) -> void:
	button.add_theme_stylebox_override("normal", style_box(Color(0.24, 0.07, 0.07, 0.72), Color(0.72, 0.31, 0.31, 0.75)))
	button.add_theme_stylebox_override("hover", style_box(Color(0.32, 0.09, 0.09, 0.9), Color(0.88, 0.4, 0.4, 0.95)))
	button.add_theme_color_override("font_color", Color(1.0, 0.83, 0.83, 1))

func _make_button(text: String, callback: Callable, primary := false, danger := false) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size = Vector2(150, 44)
	button.add_theme_font_size_override("font_size", 17)
	style_button(button)
	if primary:
		_style_primary_button(button)
	if danger:
		_style_danger_button(button)
	button.pressed.connect(callback)
	return button

func _make_label(text: String, font_size := 18, muted := false) -> Label:
	var label := Label.new()
	label.text = text
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.add_theme_font_size_override("font_size", font_size)
	label.add_theme_color_override(
		"font_color",
		Color(0.67, 0.72, 0.68, 1) if muted else Color(0.95, 0.97, 0.94, 1)
	)
	return label

func _build_workspace() -> void:
	workspace = Panel.new()
	workspace.name = "Workspace"
	workspace.position = Vector2(290, 280)
	workspace.size = Vector2(1211, 660)
	workspace.visible = false
	workspace.add_theme_stylebox_override("panel", style_box(Color(0.028, 0.041, 0.031, 0.965), Color(0.42, 0.49, 0.4, 0.52), 10))
	add_child(workspace)

	var margin := MarginContainer.new()
	margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	for side in ["left", "top", "right", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 26)
	workspace.add_child(margin)

	var content := VBoxContainer.new()
	content.add_theme_constant_override("separation", 11)
	margin.add_child(content)

	workspace_title = _make_label("", 29)
	workspace_title.add_theme_color_override("font_color", Color(0.68, 0.93, 0.5, 1))
	content.add_child(workspace_title)
	workspace_subtitle = _make_label("", 16, true)
	workspace_subtitle.custom_minimum_size.y = 28
	content.add_child(workspace_subtitle)
	content.add_child(HSeparator.new())
	workspace_body = VBoxContainer.new()
	workspace_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	workspace_body.add_theme_constant_override("separation", 10)
	content.add_child(workspace_body)

func _build_inline_account_status() -> void:
	account_code = Label.new()
	account_code.name = "AccountCode"
	account_code.visible = false
	account_code.position = Vector2(1213, 188)
	account_code.size = Vector2(286, 40)
	account_code.mouse_filter = Control.MOUSE_FILTER_IGNORE
	account_code.add_theme_font_size_override("font_size", 18)
	account_code.add_theme_color_override("font_color", Color(0.95, 0.97, 0.94, 1))
	add_child(account_code)

func _build_remove_confirmation() -> void:
	remove_confirm = ConfirmationDialog.new()
	remove_confirm.title = "Remove instance"
	remove_confirm.dialog_text = "Remove this instance and its VoxyQuest game files?"
	remove_confirm.ok_button_text = "Remove"
	remove_confirm.confirmed.connect(_confirm_remove_instance)
	add_child(remove_confirm)

func _build_install_timer() -> void:
	install_timer = Timer.new()
	install_timer.wait_time = 0.5
	install_timer.one_shot = false
	install_timer.timeout.connect(_poll_install)
	add_child(install_timer)

func _clear_workspace() -> void:
	for child in workspace_body.get_children():
		workspace_body.remove_child(child)
		child.queue_free()
	instance_list = null
	instance_rename = null
	instance_rename_button = null
	instance_remove_button = null
	instance_status = null
	install_name = null
	install_version = null
	install_submit = null
	install_status = null
	mods_list = null
	mods_status = null
	account_page_title = null
	account_page_status = null
	account_page_code = null
	account_page_action = null
	account_page_copy = null
	account_page_cancel = null
	settings_status = null

func _set_home_content_visible(visible: bool) -> void:
	for node_name in HOME_CONTENT_NODES:
		var node := get_node_or_null(node_name)
		if node is CanvasItem:
			node.visible = visible

func _select_nav(button: Button) -> void:
	for item in nav_buttons:
		var selected := item == button and item != $Home
		item.add_theme_stylebox_override("normal", style_box(
			Color(0.85, 0.88, 0.86, 0.06) if selected else Color.TRANSPARENT,
			Color(0.8, 0.85, 0.81, 0.15) if selected else Color.TRANSPARENT
		))
		var caption := get_node(str(item.name) + "Text") as Label
		caption.modulate = Color.WHITE if item == button else Color(0.78, 0.82, 0.79)

func _navigate(button: Button) -> void:
	_open_section(str(button.name))

func _open_section(section: String) -> void:
	var nav_button := get_node_or_null(section)
	if nav_button is Button and nav_button in nav_buttons:
		_select_nav(nav_button)
	current_section = section
	navigation_requested.emit(section)
	if section == "Home":
		workspace.visible = false
		_set_home_content_visible(true)
		_refresh_instances()
		return

	_set_home_content_visible(false)
	workspace.visible = true
	match section:
		"Instances":
			_render_instances_page()
		"Mods":
			_render_mods_page()
		"Accounts":
			_render_accounts_page()
		"Settings":
			_render_settings_page()
		_:
			workspace_title.text = section
			workspace_subtitle.text = ""
			_clear_workspace()

func _show_message(heading: String, message: String) -> void:
	$ActionDialog.title = heading
	$ActionDialog.dialog_text = message
	$ActionDialog.popup_centered()

func _on_play_pressed() -> void:
	if $Play.disabled:
		return
	if not runtime.launch_minecraft_vr(selected_name):
		_show_message("Could not start Minecraft", "Check your sign-in and installed instance, then try again.")

func _set_account_code(code: String) -> void:
	var show_code := not code.is_empty()
	account_code.visible = show_code
	account_code.text = "Code: %s" % code if show_code else ""
	$AccountPanel.offset_bottom = 244.0 if show_code else 208.0
	$Account.offset_bottom = 244.0 if show_code else 208.0

func _on_account_pressed() -> void:
	var auth: Dictionary = runtime.get_microsoft_login_snapshot()
	if bool(auth.get("signed_in", false)):
		_open_section("Accounts")
		return
	if not runtime.is_available():
		$AccountSubtitle.text = "Android build required"
		return
	if not bool(auth.get("configured", false)):
		$AccountSubtitle.text = "Microsoft login not configured"
		return
	var state := str(auth.get("state", "idle"))
	var code := str(auth.get("device_code", ""))
	if state == "waiting_for_user" and not code.is_empty():
		if not runtime.open_microsoft_login_page():
			$AccountSubtitle.text = "Code ready — tap to retry browser"
		return
	if state in ACTIVE_AUTH_STATES:
		return
	_open_browser_when_ready = runtime.start_microsoft_login()
	if not _open_browser_when_ready:
		$AccountSubtitle.text = "Could not start Microsoft sign-in"
	_refresh_auth_ui()

func _refresh_auth_ui() -> void:
	var auth: Dictionary = runtime.get_microsoft_login_snapshot()
	var state := str(auth.get("state", "unavailable"))
	var code := str(auth.get("device_code", ""))
	var is_signed_in := bool(auth.get("signed_in", false))

	_sync_account(auth)
	_set_auth_polling(state in ACTIVE_AUTH_STATES)
	_set_account_code(code if state == "waiting_for_user" and not code.is_empty() else "")

	if _open_browser_when_ready and state == "waiting_for_user" and not code.is_empty():
		_open_browser_when_ready = false
		if not runtime.open_microsoft_login_page():
			$AccountSubtitle.text = "Code ready — tap to retry browser"
	if state in ["error", "cancelled", "signed_in"]:
		_open_browser_when_ready = false

	if not is_signed_in:
		if not runtime.is_available():
			$AccountSubtitle.text = "Android build required"
		elif not bool(auth.get("configured", false)):
			$AccountSubtitle.text = "Microsoft login not configured"
		elif state == "starting":
			$AccountSubtitle.text = "Getting Microsoft code..."
		elif state == "waiting_for_user":
			if $AccountSubtitle.text != "Code ready — tap to retry browser":
				$AccountSubtitle.text = "Sign in with Microsoft"
		elif state == "exchanging":
			$AccountSubtitle.text = "Finishing Microsoft sign-in..."
		elif state == "error":
			$AccountSubtitle.text = "Sign-in failed — tap to retry"
		else:
			$AccountSubtitle.text = "Sign in with Microsoft"

	_refresh_account_page_fields(auth)

func _sync_account(auth: Dictionary) -> void:
	signed_in = bool(auth.get("signed_in", false))
	_update_play()
	$AccountTitle.text = str(auth.get("profile_name", "")) if signed_in else "Not Signed In"
	$AccountSubtitle.text = "Microsoft account" if signed_in else "Sign in with Microsoft"
	if signed_in:
		_set_account_code("")

func _refresh_instances() -> void:
	var snapshot: Dictionary = runtime.get_instance_snapshot()
	installed_instances = snapshot.get("instances", [])
	if not selected_name.is_empty() and not _has_instance(selected_name):
		selected_name = ""

	if not bool(snapshot.get("available", false)):
		$InstanceEmpty.text = "No instances available"
		$InstanceDescription.text = "Install Minecraft in the Android launcher to get started."
	elif not str(snapshot.get("error", "")).is_empty():
		$InstanceEmpty.text = "Could not load instances"
		$InstanceDescription.text = "Your saved profiles could not be read. Try refreshing."
	elif installed_instances.is_empty():
		$InstanceEmpty.text = "No instances installed"
		$InstanceDescription.text = "Open Instances to install Minecraft with Fabric and Vivecraft."
	elif selected_name.is_empty():
		$InstanceEmpty.text = "%d saved instance(s)" % installed_instances.size()
		$InstanceDescription.text = "Open Instances and choose which one you want to play."
	else:
		var selected := _selected_instance()
		$InstanceEmpty.text = selected_name
		$InstanceDescription.text = "Minecraft %s · Fabric · Vivecraft" % str(selected.get("version", ""))

	_update_play()
	_populate_instance_list()

func _has_instance(name: String) -> bool:
	for instance in installed_instances:
		if str(instance.get("name", "")) == name:
			return true
	return false

func _selected_instance() -> Dictionary:
	for instance in installed_instances:
		if str(instance.get("name", "")) == selected_name:
			return instance
	return {}

func _update_play() -> void:
	var selected := _selected_instance()
	$Play.disabled = not (signed_in and not install_busy and bool(selected.get("installed", false)))
	$Play.modulate = Color(0.42, 0.46, 0.41) if $Play.disabled else Color.WHITE
	$Play.tooltip_text = "Sign in and select a fully installed instance to play Minecraft VR." if $Play.disabled else "Play Minecraft VR"
	$QuickEmpty.text = "No version selected" if selected.is_empty() else "Minecraft %s · VR" % str(selected.get("version", ""))

func _render_instances_page() -> void:
	_clear_workspace()
	workspace_title.text = "Instances"
	workspace_subtitle.text = "Choose what to play, rename an existing instance, remove it, or install another version."
	_refresh_instances()

	instance_list = ItemList.new()
	instance_list.custom_minimum_size = Vector2(0, 218)
	instance_list.size_flags_vertical = Control.SIZE_EXPAND_FILL
	instance_list.add_theme_font_size_override("font_size", 18)
	instance_list.add_theme_stylebox_override("panel", style_box(Color(0.015, 0.025, 0.018, 0.7), Color(0.35, 0.43, 0.34, 0.48)))
	instance_list.item_selected.connect(_on_instance_selected)
	workspace_body.add_child(instance_list)

	var edit_row := HBoxContainer.new()
	edit_row.add_theme_constant_override("separation", 10)
	workspace_body.add_child(edit_row)
	instance_rename = LineEdit.new()
	instance_rename.placeholder_text = "Select an instance to rename"
	instance_rename.max_length = 48
	instance_rename.custom_minimum_size = Vector2(420, 44)
	instance_rename.add_theme_font_size_override("font_size", 17)
	edit_row.add_child(instance_rename)
	instance_rename_button = _make_button("Rename", _rename_selected_instance, true)
	edit_row.add_child(instance_rename_button)
	instance_remove_button = _make_button("Remove", _request_remove_selected, false, true)
	edit_row.add_child(instance_remove_button)
	edit_row.add_child(_make_button("Refresh", _refresh_instances_page))

	instance_status = _make_label(instance_notice, 15, true)
	instance_status.custom_minimum_size.y = 24
	workspace_body.add_child(instance_status)
	workspace_body.add_child(HSeparator.new())
	workspace_body.add_child(_make_label("Install a new Minecraft VR instance", 19))

	var install_row := HBoxContainer.new()
	install_row.add_theme_constant_override("separation", 10)
	workspace_body.add_child(install_row)
	install_name = LineEdit.new()
	install_name.placeholder_text = "Instance name"
	install_name.max_length = 48
	install_name.custom_minimum_size = Vector2(330, 44)
	install_name.add_theme_font_size_override("font_size", 17)
	install_row.add_child(install_name)
	install_version = OptionButton.new()
	install_version.custom_minimum_size = Vector2(230, 44)
	install_version.add_theme_font_size_override("font_size", 17)
	for version in runtime.get_install_versions():
		install_version.add_item(str(version))
	install_row.add_child(install_version)
	install_submit = _make_button("Install", _start_install, true)
	install_submit.custom_minimum_size.x = 180
	install_row.add_child(install_submit)

	install_status = _make_label("", 15, true)
	install_status.custom_minimum_size.y = 24
	workspace_body.add_child(install_status)
	_populate_instance_list()
	_poll_install()

func _populate_instance_list() -> void:
	if not is_instance_valid(instance_list):
		return
	instance_list.clear()
	var selected_index := -1
	for index in range(installed_instances.size()):
		var instance: Dictionary = installed_instances[index]
		var name := str(instance.get("name", "Unnamed instance"))
		var version := str(instance.get("version", "Unknown"))
		var readiness := "Ready" if bool(instance.get("installed", false)) else "Needs repair"
		instance_list.add_item("%s    ·    Minecraft %s    ·    %s" % [name, version, readiness])
		if name == selected_name:
			selected_index = index
	if selected_index >= 0:
		instance_list.select(selected_index)
	_update_instance_editor_state()

func _update_instance_editor_state() -> void:
	if not is_instance_valid(instance_rename):
		return
	var has_selection := not selected_name.is_empty() and _has_instance(selected_name)
	instance_rename.editable = has_selection and not install_busy
	instance_rename.text = selected_name if has_selection else ""
	instance_rename_button.disabled = not has_selection or install_busy
	instance_remove_button.disabled = not has_selection or install_busy

func _on_instance_selected(index: int) -> void:
	if index < 0 or index >= installed_instances.size():
		return
	selected_name = str(installed_instances[index].get("name", ""))
	instance_notice = "Selected %s" % selected_name
	if is_instance_valid(instance_status):
		instance_status.text = instance_notice
	_update_instance_editor_state()
	_refresh_instances()

func _refresh_instances_page() -> void:
	_refresh_instances()
	instance_notice = "Instance list refreshed."
	if is_instance_valid(instance_status):
		instance_status.text = instance_notice

func _rename_selected_instance() -> void:
	if selected_name.is_empty() or not is_instance_valid(instance_rename):
		return
	var new_name := instance_rename.text.strip_edges()
	if new_name.is_empty():
		instance_notice = "Enter a new instance name."
		instance_status.text = instance_notice
		return
	if new_name == selected_name:
		instance_notice = "That is already the instance name."
		instance_status.text = instance_notice
		return
	var old_name := selected_name
	if runtime.rename_instance(old_name, new_name):
		selected_name = new_name
		instance_notice = "Renamed %s to %s." % [old_name, new_name]
	else:
		instance_notice = "Could not rename the instance. Check the name and try again."
	_refresh_instances()
	if current_section == "Instances":
		_render_instances_page()

func _request_remove_selected() -> void:
	if selected_name.is_empty():
		return
	pending_remove_name = selected_name
	remove_confirm.dialog_text = "Remove '%s' from VoxyQuest and delete its instance folder?" % pending_remove_name
	remove_confirm.popup_centered()

func _confirm_remove_instance() -> void:
	if pending_remove_name.is_empty():
		return
	var removing := pending_remove_name
	pending_remove_name = ""
	if runtime.remove_instance(removing):
		if selected_name == removing:
			selected_name = ""
		instance_notice = "Removed %s." % removing
	else:
		instance_notice = "Could not remove %s. Make sure Minecraft is not running." % removing
	_refresh_instances()
	if current_section == "Instances":
		_render_instances_page()

func _start_install() -> void:
	if not is_instance_valid(install_name) or not is_instance_valid(install_version):
		return
	var new_name := install_name.text.strip_edges()
	if new_name.is_empty():
		install_status.text = "Enter a name for this instance."
		return
	if install_version.item_count == 0:
		install_status.text = "No supported runtime versions are available."
		return
	handled_install_name = ""
	if runtime.install_instance(new_name, install_version.get_item_text(install_version.selected)):
		install_status.text = "Preparing installation…"
		install_timer.start()
	else:
		install_status.text = "Could not start the installation."
	_poll_install()

func _poll_install() -> void:
	var snapshot: Dictionary = runtime.get_install_snapshot()
	var state := str(snapshot.get("state", ""))
	var busy := state == "installing"
	install_busy = busy
	if is_instance_valid(install_submit):
		install_submit.disabled = busy or not is_instance_valid(install_version) or install_version.item_count == 0
	if is_instance_valid(install_name):
		install_name.editable = not busy
	if is_instance_valid(install_version):
		install_version.disabled = busy
	if is_instance_valid(install_status):
		install_status.text = str(snapshot.get("message", ""))
	if busy:
		_update_play()
		if install_timer.is_stopped():
			install_timer.start()
	else:
		install_timer.stop()
		if state == "installed":
			var installed_name := str(snapshot.get("installed_name", ""))
			if not installed_name.is_empty() and installed_name != handled_install_name:
				handled_install_name = installed_name
				selected_name = installed_name
				instance_notice = "Installed %s." % installed_name
				_refresh_instances()
				if current_section == "Instances":
					_render_instances_page()
	_update_instance_editor_state()

func _render_mods_page() -> void:
	_clear_workspace()
	workspace_title.text = "Mods"
	workspace_subtitle.text = "See the mod JARs in the selected instance. Core VR files stay under VoxyQuest runtime management."
	if selected_name.is_empty():
		workspace_body.add_child(_make_label("No instance selected.", 22))
		workspace_body.add_child(_make_label("Choose an instance first, then come back here to inspect its mods.", 16, true))
		workspace_body.add_child(_make_button("Choose instance", _open_section.bind("Instances"), true))
		return

	workspace_body.add_child(_make_label(selected_name, 23))
	var selected := _selected_instance()
	workspace_body.add_child(_make_label("Minecraft %s · Fabric · Vivecraft" % str(selected.get("version", "")), 16, true))
	mods_list = ItemList.new()
	mods_list.custom_minimum_size = Vector2(0, 350)
	mods_list.size_flags_vertical = Control.SIZE_EXPAND_FILL
	mods_list.add_theme_font_size_override("font_size", 18)
	mods_list.add_theme_stylebox_override("panel", style_box(Color(0.015, 0.025, 0.018, 0.7), Color(0.35, 0.43, 0.34, 0.48)))
	workspace_body.add_child(mods_list)
	mods_status = _make_label("", 15, true)
	workspace_body.add_child(mods_status)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	row.add_child(_make_button("Refresh mods", _refresh_mods_page, true))
	row.add_child(_make_button("Edit instance", _open_section.bind("Instances")))
	workspace_body.add_child(row)
	_refresh_mods_page()

func _refresh_mods_page() -> void:
	if not is_instance_valid(mods_list):
		return
	mods_list.clear()
	var snapshot: Dictionary = runtime.get_instance_mods(selected_name)
	var error := str(snapshot.get("error", ""))
	var mods: Array = snapshot.get("mods", [])
	if not error.is_empty():
		mods_status.text = error
		return
	for mod_name in mods:
		mods_list.add_item(str(mod_name))
	mods_status.text = "%d mod file(s) found." % mods.size() if not mods.is_empty() else "No mod JARs found in this instance."

func _render_accounts_page() -> void:
	_clear_workspace()
	workspace_title.text = "Accounts"
	workspace_subtitle.text = "Microsoft device-code sign-in stays in the launcher while authentication completes in your browser."
	account_page_title = _make_label("Microsoft account", 23)
	workspace_body.add_child(account_page_title)
	account_page_status = _make_label("", 17, true)
	account_page_status.custom_minimum_size.y = 56
	workspace_body.add_child(account_page_status)
	account_page_code = LineEdit.new()
	account_page_code.editable = false
	account_page_code.placeholder_text = "Microsoft code appears here"
	account_page_code.custom_minimum_size = Vector2(520, 52)
	account_page_code.add_theme_font_size_override("font_size", 24)
	workspace_body.add_child(account_page_code)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	account_page_action = _make_button("Sign in with Microsoft", _on_accounts_action, true)
	account_page_action.custom_minimum_size.x = 250
	row.add_child(account_page_action)
	account_page_copy = _make_button("Copy code", _copy_account_code)
	row.add_child(account_page_copy)
	account_page_cancel = _make_button("Cancel", _cancel_account_login)
	row.add_child(account_page_cancel)
	workspace_body.add_child(row)
	workspace_body.add_child(_make_label("VoxyQuest never displays or stores your Microsoft access token in Godot UI.", 15, true))
	_refresh_account_page_fields(runtime.get_microsoft_login_snapshot())

func _refresh_account_page_fields(auth: Dictionary) -> void:
	if current_section != "Accounts" or not is_instance_valid(account_page_status):
		return
	var configured := bool(auth.get("configured", false))
	var state := str(auth.get("state", "unavailable"))
	var code := str(auth.get("device_code", ""))
	var profile := str(auth.get("profile_name", ""))
	var error := str(auth.get("error", ""))
	var is_signed_in := bool(auth.get("signed_in", false))

	account_page_title.text = "Signed in as %s" % profile if is_signed_in else "Microsoft account"
	account_page_code.text = code if state == "waiting_for_user" else ""
	account_page_copy.disabled = code.is_empty()
	account_page_cancel.disabled = not (state in ACTIVE_AUTH_STATES)

	if is_signed_in:
		account_page_status.text = "Minecraft account connected."
		account_page_action.text = "Signed in"
		account_page_action.disabled = true
	elif not runtime.is_available():
		account_page_status.text = "Microsoft sign-in requires the Android build."
		account_page_action.disabled = true
	elif not configured:
		account_page_status.text = "This build does not have a VoxyQuest Microsoft application client ID configured."
		account_page_action.disabled = true
	elif state == "waiting_for_user" and not code.is_empty():
		account_page_status.text = "Enter the code below in the Microsoft page. The browser should already be open."
		account_page_action.text = "Open Microsoft"
		account_page_action.disabled = false
	elif state == "starting":
		account_page_status.text = "Requesting a Microsoft device code…"
		account_page_action.text = "Getting code…"
		account_page_action.disabled = true
	elif state == "exchanging":
		account_page_status.text = "Microsoft verified. Connecting to Xbox Live and Minecraft Services…"
		account_page_action.text = "Finishing sign-in…"
		account_page_action.disabled = true
	elif state == "error":
		account_page_status.text = error if not error.is_empty() else "Microsoft sign-in failed."
		account_page_action.text = "Try again"
		account_page_action.disabled = false
	else:
		account_page_status.text = "Sign in with the Microsoft account that owns Minecraft: Java Edition."
		account_page_action.text = "Sign in with Microsoft"
		account_page_action.disabled = false

func _on_accounts_action() -> void:
	var auth: Dictionary = runtime.get_microsoft_login_snapshot()
	var state := str(auth.get("state", "idle"))
	var code := str(auth.get("device_code", ""))
	if state == "waiting_for_user" and not code.is_empty():
		if not runtime.open_microsoft_login_page():
			account_page_status.text = "Could not open the browser. Tap Open Microsoft to retry."
		return
	_on_account_pressed()

func _copy_account_code() -> void:
	var code := str(runtime.get_microsoft_login_snapshot().get("device_code", ""))
	if not code.is_empty():
		DisplayServer.clipboard_set(code)
		if is_instance_valid(account_page_status):
			account_page_status.text = "Microsoft code copied."

func _cancel_account_login() -> void:
	_open_browser_when_ready = false
	runtime.cancel_microsoft_login()
	_refresh_auth_ui()

func _render_settings_page() -> void:
	_clear_workspace()
	workspace_title.text = "Settings"
	workspace_subtitle.text = "Launcher/runtime status and safe maintenance controls. Minecraft VR owns OpenXR only after launch."
	var info: Dictionary = runtime.get_info()
	var info_panel := VBoxContainer.new()
	info_panel.add_theme_constant_override("separation", 6)
	info_panel.add_child(_make_label("Runtime", 22))
	info_panel.add_child(_make_label("Host engine: %s" % str(info.get("engine", "Godot")), 17, true))
	info_panel.add_child(_make_label("Android bridge: %s" % str(info.get("bridge_version", "none")), 17, true))
	info_panel.add_child(_make_label("Pojlib: %s" % str(info.get("pojlib", "not_loaded")), 17, true))
	info_panel.add_child(_make_label("Selected instance: %s" % (selected_name if not selected_name.is_empty() else "None"), 17, true))
	workspace_body.add_child(info_panel)
	workspace_body.add_child(HSeparator.new())
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	row.add_child(_make_button("Refresh launcher data", _refresh_launcher_data, true))
	var clear_button := _make_button("Clear instance selection", _clear_instance_selection)
	clear_button.disabled = selected_name.is_empty()
	row.add_child(clear_button)
	row.add_child(_make_button("Back to Home", _open_section.bind("Home")))
	workspace_body.add_child(row)
	settings_status = _make_label("", 16, true)
	workspace_body.add_child(settings_status)

func _refresh_launcher_data() -> void:
	if runtime.is_available():
		runtime.initialize()
	_refresh_instances()
	_refresh_auth_ui()
	if current_section == "Settings":
		_render_settings_page()
		settings_status.text = "Launcher data refreshed."

func _clear_instance_selection() -> void:
	selected_name = ""
	_refresh_instances()
	if current_section == "Settings":
		_render_settings_page()
		settings_status.text = "Instance selection cleared."
