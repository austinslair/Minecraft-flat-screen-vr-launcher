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
	"Hero", "HeroShade", "HeroEyebrow", "HeroTitle", "LibraryEyebrow",
	"InstancePanel", "InstanceEmpty", "InstanceDescription", "InstanceBadge", "ChangeInstance",
	"HomeHelpTitle", "HomeHelpBody", "HomeActions"
]

var nav_buttons: Array[Button] = []
var runtime: RefCounted = VoxyQuestRuntimeBridge.new()
var selected_name := ""
var play_mode := "vr"
var installed_instances: Array = []
var instance_read_error := ""
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

var instance_empty_hint: Label
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
var install_feedback := ""

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
	theme = preload("res://scripts/ui_theme.gd").create()
	for caption in [$AccountTitle, $AccountSubtitle]:
		caption.clip_text = true
		caption.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	get_viewport().gui_embed_subwindows = true
	set_process(false)
	for section in ["Home", "Instances", "Mods", "Accounts", "Settings"]:
		var button := get_node(section) as Button
		nav_buttons.append(button)
		button.pressed.connect(_navigate.bind(button))

	_build_play_mode()
	_build_workspace()
	_build_instance_badge()
	_build_inline_account_status()
	_build_remove_confirmation()
	_build_install_timer()

	for item in find_children("*", "Button", true, false):
		style_button(item)
	_build_home_actions()

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
	if button is OptionButton:
		return
	button.add_theme_stylebox_override("normal", style_box(Color(0, 0, 0, 0), Color(0, 0, 0, 0)))
	button.add_theme_stylebox_override("hover", style_box(Color("2b3b2d"), Color("506c48"), 10))
	button.add_theme_stylebox_override("pressed", style_box(Color("314b32"), Color("86ad69"), 10))
	button.add_theme_stylebox_override("focus", style_box(Color.TRANSPARENT, Color("aed580"), 10))
	button.add_theme_color_override("font_color", Color("f3f7ef"))
	if button == $ChangeInstance:
		button.add_theme_stylebox_override("normal", preload("res://scripts/ui_theme.gd").surface(Color("2b3b2d"), Color("608054")))
	if button == $Play:
		_style_primary_button(button)

func _style_primary_button(button: Button) -> void:
	button.add_theme_stylebox_override("normal", style_box(Color("8dc96a"), Color("b8e593"), 12))
	button.add_theme_stylebox_override("hover", style_box(Color("a8de82"), Color("d4f3b2"), 12))
	button.add_theme_stylebox_override("pressed", style_box(Color("6eaa50"), Color("a3d780"), 12))
	button.add_theme_stylebox_override("disabled", style_box(Color("344536"), Color("455a46"), 12))
	button.add_theme_color_override("font_color", Color("142417"))
	button.add_theme_color_override("font_hover_color", Color("142417"))
	button.add_theme_color_override("font_pressed_color", Color("142417"))
	button.add_theme_color_override("font_disabled_color", Color("a9bda2"))
	for state in ["normal", "hover", "pressed"]:
		var box := button.get_theme_stylebox(state)
		box.content_margin_left = 16
		box.content_margin_right = 16

func _style_danger_button(button: Button) -> void:
	button.add_theme_stylebox_override("normal", style_box(Color(0.24, 0.07, 0.07, 0.72), Color(0.72, 0.31, 0.31, 0.75)))
	button.add_theme_stylebox_override("hover", style_box(Color(0.32, 0.09, 0.09, 0.9), Color(0.88, 0.4, 0.4, 0.95)))
	button.add_theme_color_override("font_color", Color(1.0, 0.83, 0.83, 1))

func _make_button(text: String, callback: Callable, primary := false, danger := false) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size = Vector2(150, 48)
	button.add_theme_font_size_override("font_size", 17)
	style_button(button)
	button.add_theme_stylebox_override("normal", preload("res://scripts/ui_theme.gd").surface(Color("263329"), Color("405543")))
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
		Color("a9baa8") if muted else Color("f1f6ed")
	)
	return label

func _build_play_mode() -> void:
	var mode := OptionButton.new()
	mode.name = "PlayMode"
	mode.position = Vector2(866, 934)
	mode.size = Vector2(334, 54)
	mode.add_item("Virtual reality")
	mode.add_item("Flatscreen")
	mode.tooltip_text = "Flatscreen currently uses a keyboard and mouse."
	mode.item_selected.connect(func(index: int):
		play_mode = "flat" if index == 1 else "vr"
		_update_play()
	)
	add_child(mode)

func _build_home_actions() -> void:
	var actions := Panel.new()
	actions.name = "HomeActions"
	actions.position = Vector2(280, 745)
	actions.size = Vector2(1228, 132)
	actions.add_theme_stylebox_override("panel", preload("res://scripts/ui_theme.gd").surface(Color("1e2b21"), Color("3c543d")))
	add_child(actions)
	move_child(actions, $HomeHelpTitle.get_index())
	var create := _make_button("+  New instance", _open_section.bind("Instances"), true)
	create.position = Vector2(746, 42)
	create.size = Vector2(208, 54)
	create.custom_minimum_size = Vector2.ZERO
	actions.add_child(create)
	var mods := _make_button("Manage mods  →", _open_section.bind("Mods"))
	mods.position = Vector2(970, 42)
	mods.size = Vector2(216, 54)
	mods.custom_minimum_size = Vector2.ZERO
	actions.add_child(mods)

func _build_instance_badge() -> void:
	var badge := _make_label("NO PROFILE", 15)
	badge.name = "InstanceBadge"
	badge.position = Vector2(1235, 584)
	badge.size = Vector2(238, 34)
	badge.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	badge.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
	badge.add_theme_color_override("font_color", Color("b9dfa4"))
	add_child(badge)

func _build_workspace() -> void:
	workspace = Panel.new()
	workspace.name = "Workspace"
	workspace.position = Vector2(264, 112)
	workspace.size = Vector2(1244, 774)
	workspace.visible = false
	workspace.clip_contents = true
	workspace.add_theme_stylebox_override("panel", style_box(Color.TRANSPARENT, Color.TRANSPARENT, 0))
	add_child(workspace)

	var margin := MarginContainer.new()
	for side in ["left", "top", "right", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 20)
	workspace.add_child(margin)
	margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

	var content := VBoxContainer.new()
	content.add_theme_constant_override("separation", 10)
	margin.add_child(content)

	workspace_title = _make_label("", 26)
	workspace_title.visible = true
	workspace_title.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
	workspace_title.add_theme_color_override("font_color", Color("ecf5e8"))
	content.add_child(workspace_title)
	workspace_subtitle = _make_label("", 14, true)
	workspace_subtitle.custom_minimum_size.y = 20
	content.add_child(workspace_subtitle)

	var scroll := ScrollContainer.new()
	scroll.name = "PageScroll"
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_AUTO
	scroll.follow_focus = true
	scroll.clip_contents = true
	content.add_child(scroll)

	workspace_body = VBoxContainer.new()
	workspace_body.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	workspace_body.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	workspace_body.add_theme_constant_override("separation", 18)
	scroll.add_child(workspace_body)

func _build_inline_account_status() -> void:
	account_code = Label.new()
	account_code.name = "AccountCode"
	account_code.visible = false
	account_code.position = Vector2(780, 33)
	account_code.size = Vector2(310, 40)
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
	(workspace_body.get_parent() as ScrollContainer).scroll_vertical = 0
	for child in workspace_body.get_children():
		workspace_body.remove_child(child)
		child.queue_free()
	instance_empty_hint = null
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
		var selected := item == button
		item.add_theme_stylebox_override("normal", style_box(
			Color("2c412c") if selected else Color.TRANSPARENT,
			Color("557c4c") if selected else Color.TRANSPARENT, 10
		))
		var caption := get_node(str(item.name) + "Text") as Label
		caption.modulate = Color("c8efa9") if selected else Color("c0d0be")

func _navigate(button: Button) -> void:
	_open_section(str(button.name))

func _open_section(section: String) -> void:
	var nav_button := get_node_or_null(section)
	if nav_button is Button and nav_button in nav_buttons:
		_select_nav(nav_button)
	current_section = section
	$PageTitle.text = "Home" if section == "Home" else section
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
	var launched: bool = runtime.launch_minecraft_flat(selected_name) if play_mode == "flat" else runtime.launch_minecraft_vr(selected_name)
	if not launched:
		_show_message("Could not start Minecraft", "Check your sign-in and installed instance, then try again.")

func _set_account_code(code: String) -> void:
	var show_code := not code.is_empty()
	account_code.visible = show_code
	account_code.text = "Code: %s" % code if show_code else ""


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
	instance_read_error = str(snapshot.get("error", ""))
	if not bool(snapshot.get("available", false)):
		instance_read_error = "Instances need the Android runtime. Open an APK with the VoxyQuest bridge included."
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

	var chosen := _selected_instance()
	$InstanceBadge.text = "READY TO PLAY" if bool(chosen.get("installed", false)) else ("NEEDS REPAIR" if not chosen.is_empty() else "NO PROFILE")
	$InstanceBadge.add_theme_color_override("font_color", Color("b9dfa4") if bool(chosen.get("installed", false)) else Color("e8c88a"))
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
	$Play.modulate = Color.WHITE
	$Play.tooltip_text = "Sign in and select a fully installed instance to play." if $Play.disabled else "Play Minecraft (%s)" % ("Flatscreen" if play_mode == "flat" else "VR")
	$PlaybarCaption.text = "INSTALLING" if install_busy else ("SIGN IN TO PLAY" if not signed_in else ("CHOOSE AN INSTANCE" if selected.is_empty() else ("REPAIR REQUIRED" if not bool(selected.get("installed", false)) else "READY TO PLAY")))
	$QuickEmpty.text = "No version selected" if selected.is_empty() else "Minecraft %s · %s" % [str(selected.get("version", "")), "Flatscreen" if play_mode == "flat" else "VR"]

func _page_card(parent: Control, heading: String, description := "") -> VBoxContainer:
	var panel := PanelContainer.new()
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	panel.add_theme_stylebox_override("panel", preload("res://scripts/ui_theme.gd").surface(Color("1e2921"), Color("3b4d3d")))
	parent.add_child(panel)
	var body := VBoxContainer.new()
	body.add_theme_constant_override("separation", 14)
	panel.add_child(body)
	var title := _make_label(heading, 21)
	title.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
	body.add_child(title)
	if not description.is_empty():
		body.add_child(_make_label(description, 16, true))
	return body

func _field(parent: Control, caption: String, control: Control) -> void:
	var field := VBoxContainer.new()
	field.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	field.add_theme_constant_override("separation", 8)
	parent.add_child(field)
	field.add_child(_make_label(caption, 15, true))
	control.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	field.add_child(control)

func _detail_row(parent: Control, caption: String, value: String) -> void:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 24)
	parent.add_child(row)
	var label := _make_label(caption, 17, true)
	label.custom_minimum_size.x = 260
	row.add_child(label)
	var detail := _make_label(value, 17)
	detail.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(detail)

func _render_instances_page() -> void:
	_clear_workspace()
	workspace_title.text = "Your instances"
	workspace_subtitle.text = "All your worlds in one place. Pick a version and make it yours."
	_refresh_instances()


	var columns := HBoxContainer.new()
	columns.add_theme_constant_override("separation", 24)
	workspace_body.add_child(columns)
	var library := _page_card(columns, "Installed instances", "Select a profile to launch or edit.")
	library.custom_minimum_size.x = 540
	var editor := _page_card(columns, "Manage selected", "Rename, repair, or remove the selected profile.")
	instance_empty_hint = _make_label("No instances yet. Install one below.", 16, true)
	library.add_child(instance_empty_hint)

	instance_list = ItemList.new()
	instance_list.custom_minimum_size = Vector2(0, 275)
	instance_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	instance_list.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	instance_list.mouse_filter = Control.MOUSE_FILTER_STOP
	instance_list.focus_mode = Control.FOCUS_ALL
	instance_list.add_theme_font_size_override("font_size", 17)
	instance_list.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	instance_list.add_theme_stylebox_override("panel", style_box(Color("17221b"), Color("415442"), 12))
	instance_list.item_selected.connect(_on_instance_selected)
	library.add_child(instance_list)

	var edit_row := HBoxContainer.new()
	edit_row.add_theme_constant_override("separation", 8)
	editor.add_child(edit_row)
	instance_rename = LineEdit.new()
	instance_rename.placeholder_text = "Select an instance to rename"
	instance_rename.max_length = 48
	instance_rename.custom_minimum_size = Vector2(300, 46)
	instance_rename.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	instance_rename.add_theme_font_size_override("font_size", 17)
	_field(editor, "Instance name", instance_rename)
	editor.move_child(edit_row, editor.get_child_count() - 1)
	instance_rename_button = _make_button("Rename", _rename_selected_instance, true)
	instance_rename_button.custom_minimum_size.x = 125
	instance_rename_button.size_flags_vertical = Control.SIZE_SHRINK_END
	edit_row.add_child(instance_rename_button)
	instance_remove_button = _make_button("Remove", _request_remove_selected, false, true)
	instance_remove_button.custom_minimum_size.x = 125
	instance_remove_button.size_flags_vertical = Control.SIZE_SHRINK_END
	edit_row.add_child(instance_remove_button)
	var refresh := _make_button("Refresh", _refresh_instances_page)
	refresh.custom_minimum_size.x = 125
	refresh.size_flags_vertical = Control.SIZE_SHRINK_END
	edit_row.add_child(refresh)

	var action_row := VBoxContainer.new()
	action_row.add_theme_constant_override("separation", 8)
	editor.add_child(action_row)
	var repair := _make_button("Repair / resume selected", _repair_selected_instance)
	repair.custom_minimum_size.x = 260
	action_row.add_child(repair)
	instance_status = _make_label(instance_notice, 15, true)
	instance_status.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	instance_status.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	action_row.add_child(instance_status)

	var installer := _page_card(workspace_body, "+  Create a new instance", "Choose a Minecraft version. Fabric and Vivecraft are added during installation.")

	var install_row := HBoxContainer.new()
	install_row.add_theme_constant_override("separation", 8)
	installer.add_child(install_row)
	install_name = LineEdit.new()
	install_name.placeholder_text = "Optional — automatic name"
	install_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	install_name.max_length = 48
	install_name.custom_minimum_size = Vector2(340, 46)
	install_name.add_theme_font_size_override("font_size", 17)
	_field(install_row, "New instance name", install_name)
	install_version = OptionButton.new()
	install_version.custom_minimum_size = Vector2(250, 46)
	install_version.add_theme_font_size_override("font_size", 17)
	for version in runtime.get_install_versions():
		install_version.add_item(str(version))
	_field(install_row, "Minecraft version", install_version)
	install_submit = _make_button("Install", _start_install, true)
	install_submit.action_mode = BaseButton.ACTION_MODE_BUTTON_PRESS
	install_submit.custom_minimum_size = Vector2(170, 46)
	install_submit.size_flags_vertical = Control.SIZE_SHRINK_END
	install_row.add_child(install_submit)

	install_status = _make_label("", 15, true)
	install_status.custom_minimum_size.y = 22
	installer.add_child(install_status)
	_populate_instance_list()
	_poll_install()

func _populate_instance_list() -> void:
	if not is_instance_valid(instance_list):
		return
	instance_list.clear()
	if is_instance_valid(instance_empty_hint):
		instance_empty_hint.visible = installed_instances.is_empty() or not instance_read_error.is_empty()
		instance_empty_hint.text = instance_read_error if not instance_read_error.is_empty() else "No instances installed yet. Choose a Minecraft version below and install one."
	instance_list.visible = true
	var selected_index := -1
	for index in range(installed_instances.size()):
		var instance: Dictionary = installed_instances[index]
		var name := str(instance.get("name", "Unnamed instance"))
		var version := str(instance.get("version", "Unknown"))
		var readiness := "Ready" if bool(instance.get("installed", false)) else "Needs repair"
		instance_list.add_item("%s    /    Minecraft %s    /    %s" % [name, version, readiness])
		instance_list.set_item_tooltip(index, "%s\nMinecraft %s — %s" % [name, version, readiness])
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

func _repair_selected_instance() -> void:
	var selected := _selected_instance()
	if selected.is_empty() or install_busy:
		if is_instance_valid(instance_status):
			instance_status.text = "Select an instance first."
		return
	handled_install_name = ""
	if runtime.install_instance(selected_name, str(selected.get("version", ""))):
		install_timer.start()
	_poll_install()

func _start_install() -> void:
	if not is_instance_valid(install_name) or not is_instance_valid(install_version):
		return
	if install_busy:
		return
	if install_version.item_count == 0:
		install_feedback = "No supported runtime versions are available."
		install_status.text = install_feedback
		_show_message("Installation unavailable", install_feedback)
		return
	if install_version.selected < 0:
		install_version.select(0)
	var version := install_version.get_item_text(install_version.selected)
	var new_name := install_name.text.strip_edges()
	if new_name.is_empty():
		new_name = "Minecraft %s" % version.replace(".", "-")
		install_name.text = new_name

	handled_install_name = ""
	if runtime.install_instance(new_name, version):
		install_feedback = "Preparing installation…"
		install_status.text = install_feedback
		install_timer.start()
	else:
		var snapshot: Dictionary = runtime.get_install_snapshot()
		install_feedback = str(snapshot.get("message", "")).strip_edges()
		if install_feedback.is_empty():
			install_feedback = "The Android installer rejected the request. Stop Minecraft, check the instance name, and try again."
		install_status.text = install_feedback
		_show_message("Installation did not start", install_feedback)
	_poll_install()

func _poll_install() -> void:
	var snapshot: Dictionary = runtime.get_install_snapshot()
	var state := str(snapshot.get("state", ""))
	var busy := state == "installing"
	install_busy = busy
	if is_instance_valid(install_submit):
		install_submit.text = "Installing…" if busy else ("Retry install" if state == "error" else "Install")
		install_submit.disabled = busy or not runtime.is_available() or state == "unavailable" or not is_instance_valid(install_version) or install_version.item_count == 0
	if is_instance_valid(install_name):
		install_name.editable = not busy
	if is_instance_valid(install_version):
		install_version.disabled = busy
	var message := str(snapshot.get("message", "")).strip_edges()
	if not message.is_empty():
		install_feedback = message
	elif busy:
		install_feedback = "Installing Minecraft…"
	if is_instance_valid(install_status):
		install_status.text = install_feedback
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
	_update_play()

func _render_mods_page() -> void:
	_clear_workspace()
	workspace_title.text = "Manage mods"
	workspace_subtitle.text = "Keep your Fabric mods organized by instance."
	if selected_name.is_empty():
		var empty := _page_card(workspace_body, "Choose an instance first", "Select an instance before adding or viewing mods.")
		var choose := _make_button("Choose instance", _open_section.bind("Instances"), true)
		choose.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		empty.add_child(choose)
		return

	var collection := _page_card(workspace_body, selected_name)
	var selected := _selected_instance()
	collection.add_child(_make_label("Minecraft %s · Fabric · Vivecraft" % str(selected.get("version", "")), 16, true))
	mods_list = ItemList.new()
	mods_list.custom_minimum_size = Vector2(0, 300)
	mods_list.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	mods_list.add_theme_font_size_override("font_size", 18)
	mods_list.add_theme_stylebox_override("panel", style_box(Color("17221b"), Color("415442"), 12))
	collection.add_child(mods_list)
	mods_status = _make_label("", 15, true)
	collection.add_child(mods_status)
	var actions := HBoxContainer.new()
	actions.add_theme_constant_override("separation", 12)
	collection.add_child(actions)
	actions.add_child(_make_button("Add mod JAR", _add_mod, true))
	actions.add_child(_make_button("Refresh mods", _refresh_mods_page))
	collection.add_child(_make_label("Use Fabric mods made for this Minecraft version and install required dependencies.", 16, true))
	_refresh_mods_page()

func _add_mod() -> void:
	if selected_name.is_empty() or install_busy:
		return
	if runtime.add_instance_mod(selected_name):
		mods_status.text = "Choose a mod in the file picker, then refresh this list when you return."
	else:
		mods_status.text = "Could not open mod import. Stop Minecraft and try again."

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
	workspace_subtitle.text = "Connect your Minecraft: Java Edition account to play."
	var account := _page_card(workspace_body, "Your account")
	account_page_title = _make_label("Microsoft account", 23)
	account.add_child(account_page_title)
	account_page_status = _make_label("", 17, true)
	account_page_status.custom_minimum_size.y = 56
	account.add_child(account_page_status)
	account_page_code = LineEdit.new()
	account_page_code.editable = false
	account_page_code.placeholder_text = "Microsoft code appears here"
	account_page_code.custom_minimum_size = Vector2(320, 64)
	account_page_code.add_theme_font_size_override("font_size", 24)
	_field(account, "Microsoft sign-in code", account_page_code)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	account_page_action = _make_button("Sign in with Microsoft", _on_accounts_action, true)
	account_page_action.custom_minimum_size.x = 250
	row.add_child(account_page_action)
	account_page_copy = _make_button("Copy code", _copy_account_code)
	row.add_child(account_page_copy)
	account_page_cancel = _make_button("Cancel", _cancel_account_login)
	row.add_child(account_page_cancel)
	account.add_child(row)
	var guide := _page_card(workspace_body, "Sign in from your headset")
	_detail_row(guide, "01   Open Microsoft", "Start sign-in to open the browser on your headset.")
	_detail_row(guide, "02   Enter your code", "Copy the code above, then paste or type it into the Microsoft page.")
	_detail_row(guide, "03   Return to VoxyQuest", "Finish in the browser. Your account status updates automatically.")
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
	account_page_code.get_parent().visible = state == "waiting_for_user" and not code.is_empty()
	account_page_copy.visible = state == "waiting_for_user" and not code.is_empty()
	account_page_cancel.visible = state in ACTIVE_AUTH_STATES
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
		account_page_status.text = "This build does not have a Microsoft application client ID configured."
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
	workspace_subtitle.text = "Your selected profile and launcher diagnostics."
	var selection := _page_card(workspace_body, "Game selection", "Choose which installed instance the Play button opens.")
	_detail_row(selection, "Selected instance", selected_name if not selected_name.is_empty() else "None selected")
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 12)
	selection.add_child(row)
	row.add_child(_make_button("Choose instance", _open_section.bind("Instances"), true))
	var clear_button := _make_button("Clear selection", _clear_instance_selection)
	clear_button.disabled = selected_name.is_empty()
	row.add_child(clear_button)
	var info: Dictionary = runtime.get_info()
	var diagnostics := _page_card(workspace_body, "Launcher status")
	_detail_row(diagnostics, "Host engine", str(info.get("engine", "Godot")))
	_detail_row(diagnostics, "Android bridge", str(info.get("bridge_version", "Unavailable")))
	_detail_row(diagnostics, "Pojlib runtime", str(info.get("pojlib", "Not loaded")))
	var refresh := _make_button("Refresh launcher data", _refresh_launcher_data)
	refresh.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	diagnostics.add_child(refresh)
	settings_status = _make_label("", 16, true)
	diagnostics.add_child(settings_status)

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
