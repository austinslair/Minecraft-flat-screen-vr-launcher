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
	"HeroEyebrow", "HeroTitle", "LibraryEyebrow",
	"InstancePanel", "InstanceEmpty", "InstanceDescription", "ChangeInstance",
	"HomeHelpTitle", "HomeHelpBody", "HomeTools", "HomeToolsInstances", "HomeToolsMods", "HomeBadge"
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
var library_home: Control
var library_grid: VBoxContainer
var library_inspector: VBoxContainer
var library_launch_button: Button

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
var modrinth_search: LineEdit
var modrinth_search_button: Button
var modrinth_results: VBoxContainer
var modrinth_selected := -1
var modrinth_install_button: Button
var modrinth_status: Label
var modrinth_hits: Array = []
var modrinth_timer: Timer
var last_modrinth_results := ""
var last_modrinth_install_state := ""

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
	_build_inline_account_status()
	_build_remove_confirmation()
	_build_install_timer()
	modrinth_timer = Timer.new()
	modrinth_timer.wait_time = 0.5
	modrinth_timer.timeout.connect(_poll_modrinth)
	add_child(modrinth_timer)

	for item in find_children("*", "Button", true, false):
		style_button(item)
	_build_home_tools()
	_build_library_home()
	_apply_library_shell()

	_select_nav($Home)
	_set_home_content_visible(false)
	library_home.visible = true
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
	button.add_theme_stylebox_override("hover", style_box(Color(0.85, 0.88, 0.86, 0.07), Color(0.8, 0.85, 0.81, 0.22)))
	button.add_theme_stylebox_override("pressed", style_box(Color(0.8, 0.85, 0.82, 0.12), Color(0.8, 0.85, 0.81, 0.35)))
	button.add_theme_stylebox_override("focus", style_box(Color(0, 0, 0, 0), Color(0.85, 0.9, 0.87, 0.8)))
	button.add_theme_color_override("font_color", Color("f1f3eb"))
	if button == $ChangeInstance:
		button.add_theme_stylebox_override("normal", preload("res://scripts/ui_theme.gd").surface(Color("dce8cb"), Color.TRANSPARENT))
		button.add_theme_color_override("font_color", Color("233b2a"))
	if button == $Play:
		_style_primary_button(button)

func _style_primary_button(button: Button) -> void:
	button.add_theme_stylebox_override("normal", style_box(Color("3999bd"), Color.TRANSPARENT))
	button.add_theme_stylebox_override("hover", style_box(Color("55adcc"), Color.TRANSPARENT))
	button.add_theme_stylebox_override("pressed", style_box(Color("247d9d"), Color.TRANSPARENT))
	button.add_theme_color_override("font_color", Color.WHITE)
	button.add_theme_color_override("font_hover_color", Color.WHITE)
	button.add_theme_color_override("font_pressed_color", Color.WHITE)
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
	button.add_theme_stylebox_override("normal", preload("res://scripts/ui_theme.gd").surface(Color("f6f8f2"), Color("c8d2c4")))
	button.add_theme_stylebox_override("hover", preload("res://scripts/ui_theme.gd").surface(Color("e7f1f5"), Color("9cbdca")))
	button.add_theme_stylebox_override("pressed", preload("res://scripts/ui_theme.gd").surface(Color("d9eaf0"), Color("80adbd")))
	button.add_theme_color_override("font_color", Color("26382b"))
	button.add_theme_color_override("font_hover_color", Color("1b4253"))
	button.add_theme_color_override("font_pressed_color", Color("1b4253"))
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
		Color("657469") if muted else Color("1f3026")
	)
	return label

func _build_play_mode() -> void:
	var mode := OptionButton.new()
	mode.name = "PlayMode"
	mode.position = Vector2(867, 929)
	mode.size = Vector2(310, 54)
	mode.add_item("Virtual reality")
	mode.add_item("Flatscreen")
	mode.tooltip_text = "Choose VR or flatscreen for the selected instance."
	mode.item_selected.connect(func(index: int):
		play_mode = "flat" if index == 1 else "vr"
		_update_play()
	)
	add_child(mode)

func _build_workspace() -> void:
	workspace = Panel.new()
	workspace.name = "Workspace"
	workspace.position = Vector2(24, 193)
	workspace.size = Vector2(1488, 684)
	workspace.visible = false
	workspace.clip_contents = true
	workspace.add_theme_stylebox_override("panel", style_box(Color.TRANSPARENT, Color.TRANSPARENT, 0))
	add_child(workspace)

	var margin := MarginContainer.new()
	for side in ["left", "top", "right", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 24)
	workspace.add_child(margin)
	margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

	var content := VBoxContainer.new()
	content.add_theme_constant_override("separation", 10)
	margin.add_child(content)

	workspace_title = _make_label("", 32)
	workspace_title.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
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
	workspace_body.add_theme_constant_override("separation", 16)
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

func _build_home_tools() -> void:
	var panel := Panel.new()
	panel.name = "HomeTools"
	panel.position = Vector2(974, 366)
	panel.size = Vector2(514, 447)
	panel.add_theme_stylebox_override("panel", style_box(Color("ffffff"), Color("d1dacb"), 16))
	add_child(panel)
	move_child(panel, $HomeHelpTitle.get_index())
	var badge := _make_label("FABRIC  /  VIVECRAFT", 13)
	badge.name = "HomeBadge"
	badge.position = Vector2(80, 399)
	badge.size = Vector2(420, 28)
	badge.add_theme_color_override("font_color", Color("b8d7ab"))
	badge.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
	add_child(badge)
	var browse := _make_button("Browse instances     →", _open_section.bind("Instances"))
	browse.name = "HomeToolsInstances"
	browse.position = Vector2(1006, 604)
	browse.size = Vector2(450, 60)
	browse.custom_minimum_size = Vector2.ZERO
	add_child(browse)
	var mods := _make_button("Discover Fabric mods     →", _open_section.bind("Mods"))
	mods.name = "HomeToolsMods"
	mods.position = Vector2(1006, 680)
	mods.size = Vector2(450, 60)
	mods.custom_minimum_size = Vector2.ZERO
	add_child(mods)

func _apply_library_shell() -> void:
	$Canvas.color = Color("ffffff")
	$Sidebar.color = Color("edf1f5")
	$SidebarRule.color = Color("edf1f5")
	$HeaderRule.color = Color("cfd7dd")
	$Brand.text = "VOXYQUEST"
	$Brand.add_theme_color_override("font_color", Color("25353d"))
	$BrandCaption.text = "MINECRAFT LAUNCHER  ·  QUEST"
	$BrandCaption.add_theme_color_override("font_color", Color("687984"))
	$AccountPanel.add_theme_stylebox_override("panel", style_box(Color("e2e8ed"), Color("ced7de")))
	$AccountTitle.add_theme_color_override("font_color", Color("25353d"))
	$AccountSubtitle.add_theme_color_override("font_color", Color("637580"))
	$Playbar.color = Color("edf1f5")
	$PlaybarRule.color = Color("cfd7dd")
	$PlaybarCaption.add_theme_color_override("font_color", Color("687984"))
	$QuickEmpty.add_theme_color_override("font_color", Color("25353d"))
	$HomeText.text = "Library"
	$InstancesText.text = "Add instance"
	for button in nav_buttons:
		var caption := get_node(str(button.name) + "Text") as Label
		caption.add_theme_color_override("font_color", Color("25353d"))
		(get_node(str(button.name) + "Icon") as TextureRect).modulate = Color("51717b")
	$Play.text = "Launch  →"

func _build_library_home() -> void:
	library_home = HBoxContainer.new()
	library_home.name = "LibraryHome"
	library_home.position = Vector2(0, 169)
	library_home.size = Vector2(1536, 721)
	library_home.add_theme_constant_override("separation", 0)
	add_child(library_home)
	var area := PanelContainer.new()
	area.custom_minimum_size.x = 1170
	area.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	area.add_theme_stylebox_override("panel", style_box(Color.WHITE, Color.TRANSPARENT, 0))
	library_home.add_child(area)
	var main_margin := MarginContainer.new()
	main_margin.add_theme_constant_override("margin_left", 34)
	main_margin.add_theme_constant_override("margin_top", 28)
	main_margin.add_theme_constant_override("margin_right", 34)
	main_margin.add_theme_constant_override("margin_bottom", 18)
	area.add_child(main_margin)
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	main_margin.add_child(scroll)
	library_grid = VBoxContainer.new()
	library_grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	library_grid.add_theme_constant_override("separation", 26)
	scroll.add_child(library_grid)
	var sidebar := PanelContainer.new()
	sidebar.custom_minimum_size.x = 348
	sidebar.add_theme_stylebox_override("panel", style_box(Color("f5f7f9"), Color("d7dfe5"), 0))
	library_home.add_child(sidebar)
	var side_margin := MarginContainer.new()
	for side in ["left", "right"]:
		side_margin.add_theme_constant_override("margin_" + side, 25)
	side_margin.add_theme_constant_override("margin_top", 28)
	side_margin.add_theme_constant_override("margin_bottom", 20)
	sidebar.add_child(side_margin)
	library_inspector = VBoxContainer.new()
	library_inspector.add_theme_constant_override("separation", 12)
	side_margin.add_child(library_inspector)
	library_home.visible = false

func _library_icon(title: String, version: String, large := false) -> PanelContainer:
	var swatches: Array[Color] = [Color("b4e7e8"), Color("f1d6ab"), Color("dad5f1"), Color("cde4ba"), Color("e9d0d3")]
	var accent: Color = swatches[int(abs(title.hash())) % swatches.size()]
	var tile := PanelContainer.new()
	tile.custom_minimum_size = Vector2(112, 112) if large else Vector2(88, 88)
	var surface := style_box(accent, accent.darkened(0.12), 13)
	surface.content_margin_left = 5
	surface.content_margin_right = 5
	surface.content_margin_top = 5
	surface.content_margin_bottom = 5
	tile.add_theme_stylebox_override("panel", surface)
	var mark := Label.new()
	mark.text = "F" if not version.is_empty() else "M"
	mark.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	mark.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	mark.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
	mark.add_theme_font_size_override("font_size", 46 if large else 38)
	mark.add_theme_color_override("font_color", Color("2b555d"))
	mark.mouse_filter = Control.MOUSE_FILTER_IGNORE
	tile.add_child(mark)
	return tile

func _library_section(heading: String, profiles: Array) -> void:
	if profiles.is_empty():
		return
	var line := HBoxContainer.new()
	line.add_theme_constant_override("separation", 16)
	library_grid.add_child(line)
	var caption := _make_label("⌄  " + heading, 18)
	caption.custom_minimum_size.x = 210
	caption.autowrap_mode = TextServer.AUTOWRAP_OFF
	caption.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
	line.add_child(caption)
	var separator := HSeparator.new()
	separator.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	line.add_child(separator)
	var cards := GridContainer.new()
	cards.columns = 5
	cards.add_theme_constant_override("h_separation", 14)
	cards.add_theme_constant_override("v_separation", 22)
	library_grid.add_child(cards)
	for instance in profiles:
		var name := str(instance.get("name", "Unnamed instance"))
		var version := str(instance.get("version", ""))
		var card := VBoxContainer.new()
		card.custom_minimum_size.x = 195
		card.add_theme_constant_override("separation", 7)
		cards.add_child(card)
		var icon_row := CenterContainer.new()
		icon_row.custom_minimum_size.y = 102
		var icon := _library_icon(name, version)
		icon.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		icon.gui_input.connect(func(event: InputEvent):
			if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
				_select_library_instance(name)
		)
		icon_row.add_child(icon)
		card.add_child(icon_row)
		var select := _make_button(name, _select_library_instance.bind(name))
		select.custom_minimum_size = Vector2(190, 48)
		select.tooltip_text = "%s\nMinecraft %s" % [name, version]
		select.clip_text = true
		select.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		if name == selected_name:
			_style_primary_button(select)
		card.add_child(select)
		var version_label := _make_label("Minecraft " + version, 13, true)
		version_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		card.add_child(version_label)

func _select_library_instance(name: String) -> void:
	selected_name = name
	_refresh_instances()

func _refresh_library_home() -> void:
	if not is_instance_valid(library_grid):
		return
	for child in library_grid.get_children():
		library_grid.remove_child(child)
		child.queue_free()
	for child in library_inspector.get_children():
		library_inspector.remove_child(child)
		child.queue_free()
	var ready: Array = []
	var repair: Array = []
	for instance in installed_instances:
		if bool(instance.get("installed", false)):
			ready.append(instance)
		else:
			repair.append(instance)
	_library_section("Your instances", ready)
	_library_section("Needs repair", repair)
	if installed_instances.is_empty():
		library_grid.add_child(_make_label("No instances yet", 26))
		library_grid.add_child(_make_label("Add a Minecraft version to start building your library.", 17, true))
		var add_instance := _make_button("+  Add instance", _open_section.bind("Instances"), true)
		add_instance.custom_minimum_size.x = 220
		add_instance.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		library_grid.add_child(add_instance)
	var selected := _selected_instance()
	var preview := CenterContainer.new()
	preview.custom_minimum_size.y = 125
	library_inspector.add_child(preview)
	preview.add_child(_library_icon(selected_name, str(selected.get("version", "")), true))
	var title := _make_label(selected_name if not selected.is_empty() else "Select an instance", 21)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_override("font", preload("res://assets/fonts/DejaVuSans-Bold.ttf"))
	library_inspector.add_child(title)
	var detail := _make_label("Minecraft %s · Fabric" % str(selected.get("version", "")) if not selected.is_empty() else "Choose a tile in your library", 14, true)
	detail.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	library_inspector.add_child(detail)
	library_inspector.add_child(HSeparator.new())
	var launch := _make_button("▷   Launch selected", _on_play_pressed, true)
	launch.disabled = $Play.disabled
	library_launch_button = launch
	library_inspector.add_child(launch)
	var mode := _make_label("Use the VR / Flatscreen selector below", 13, true)
	library_inspector.add_child(mode)
	library_inspector.add_child(HSeparator.new())
	var manage := _make_button("▤   Edit instance", _open_section.bind("Instances"))
	manage.disabled = selected.is_empty()
	library_inspector.add_child(manage)
	var mods := _make_button("◇   Browse mods", _open_section.bind("Mods"))
	mods.disabled = selected.is_empty()
	library_inspector.add_child(mods)
	var fix := _make_button("↻   Repair instance", _repair_selected_instance)
	fix.disabled = selected.is_empty() or install_busy
	library_inspector.add_child(fix)
	var remove := _make_button("✕   Remove instance", _request_remove_selected)
	remove.disabled = selected.is_empty() or install_busy
	library_inspector.add_child(remove)
	library_inspector.add_spacer(false)
	library_inspector.add_child(_make_label("%d saved instance(s)" % installed_instances.size(), 13, true))

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
	modrinth_search = null
	modrinth_search_button = null
	modrinth_results = null
	modrinth_install_button = null
	modrinth_status = null
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
			Color("dae8ef") if selected else Color.TRANSPARENT,
			Color.TRANSPARENT
		))
		var caption := get_node(str(item.name) + "Text") as Label
		caption.add_theme_color_override("font_color", Color("146e90") if selected else Color("34454e"))
		var icon := get_node(str(item.name) + "Icon") as TextureRect
		icon.modulate = Color("2084a8") if selected else Color("526f7a")

func _navigate(button: Button) -> void:
	_open_section(str(button.name))

func _open_section(section: String) -> void:
	if is_instance_valid(modrinth_timer) and section != "Mods":
		modrinth_timer.stop()
	var nav_button := get_node_or_null(section)
	if nav_button is Button and nav_button in nav_buttons:
		_select_nav(nav_button)
	current_section = section
	$PageTitle.text = "Overview" if section == "Home" else section
	$PageTitle.visible = false
	navigation_requested.emit(section)
	if section == "Home":
		workspace.visible = false
		_set_home_content_visible(false)
		library_home.visible = true
		_refresh_instances()
		return

	_set_home_content_visible(false)
	library_home.visible = false
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

	_update_play()
	_populate_instance_list()
	if current_section == "Home":
		_refresh_library_home()

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
	if is_instance_valid(library_launch_button):
		library_launch_button.disabled = $Play.disabled

func _page_card(parent: Control, heading: String, description := "") -> VBoxContainer:
	var panel := PanelContainer.new()
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	panel.add_theme_stylebox_override("panel", preload("res://scripts/ui_theme.gd").surface(Color("ffffff"), Color("d1dacb")))
	parent.add_child(panel)
	var body := VBoxContainer.new()
	body.add_theme_constant_override("separation", 12)
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
	workspace_title.text = "Instances"
	workspace_subtitle.text = "Select an instance, repair it, or install another Minecraft version."
	_refresh_instances()


	var columns := HBoxContainer.new()
	columns.add_theme_constant_override("separation", 24)
	workspace_body.add_child(columns)
	var library := _page_card(columns, "Your instances", "Choose a profile to manage or play.")
	library.custom_minimum_size.x = 455
	library.add_theme_constant_override("separation", 12)
	var tools := VBoxContainer.new()
	tools.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tools.add_theme_constant_override("separation", 16)
	columns.add_child(tools)
	var editor := _page_card(tools, "Profile settings", "Rename, repair, or remove the selected profile.")
	instance_empty_hint = _make_label("No instances yet. Install one below.", 16, true)
	library.add_child(instance_empty_hint)

	instance_list = ItemList.new()
	instance_list.custom_minimum_size = Vector2(0, 254)
	instance_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	instance_list.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	instance_list.mouse_filter = Control.MOUSE_FILTER_STOP
	instance_list.focus_mode = Control.FOCUS_ALL
	instance_list.add_theme_font_size_override("font_size", 17)
	instance_list.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	instance_list.add_theme_stylebox_override("panel", style_box(Color("f6f8f2"), Color("cbd5c8")))
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

	var installer := _page_card(tools, "Create an instance", "Install a version with Fabric and Vivecraft.")

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
		instance_list.add_item("%s   ·   %s   ·   %s" % [name, version, readiness])
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
	workspace_title.text = "Mods"
	workspace_subtitle.text = "Browse compatible Fabric mods from Modrinth or import a local JAR."
	if selected_name.is_empty():
		var empty := _page_card(workspace_body, "Choose an instance first", "Select an instance before adding or viewing mods.")
		var choose := _make_button("Choose instance", _open_section.bind("Instances"), true)
		choose.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		empty.add_child(choose)
		return

	var selected := _selected_instance()
	var columns := HBoxContainer.new()
	columns.add_theme_constant_override("separation", 20)
	workspace_body.add_child(columns)
	var browser := _page_card(columns, "Discover mods", "Modrinth · Fabric · Minecraft %s" % str(selected.get("version", "")))
	browser.custom_minimum_size.x = 850
	last_modrinth_results = ""
	var search_row := HBoxContainer.new()
	search_row.add_theme_constant_override("separation", 10)
	browser.add_child(search_row)
	modrinth_search = LineEdit.new()
	modrinth_search.placeholder_text = "Search mods by name…"
	modrinth_search.custom_minimum_size.y = 50
	modrinth_search.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	modrinth_search.text_submitted.connect(func(_text: String): _search_modrinth())
	search_row.add_child(modrinth_search)
	modrinth_search_button = _make_button("Search", _search_modrinth, true)
	search_row.add_child(modrinth_search_button)
	var results_scroll := ScrollContainer.new()
	results_scroll.custom_minimum_size.y = 360
	results_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	browser.add_child(results_scroll)
	modrinth_results = VBoxContainer.new()
	modrinth_results.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	modrinth_results.add_theme_constant_override("separation", 10)
	results_scroll.add_child(modrinth_results)
	var install_row := HBoxContainer.new()
	install_row.add_theme_constant_override("separation", 12)
	browser.add_child(install_row)
	modrinth_install_button = _make_button("Install selected", _install_modrinth, true)
	modrinth_install_button.disabled = true
	install_row.add_child(modrinth_install_button)
	modrinth_status = _make_label("Search to find mods for this instance.", 15, true)
	modrinth_status.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	modrinth_status.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	install_row.add_child(modrinth_status)
	var collection := _page_card(columns, "Installed mods", selected_name)
	mods_list = ItemList.new()
	mods_list.custom_minimum_size = Vector2(0, 280)
	mods_list.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	mods_list.add_theme_font_size_override("font_size", 18)
	mods_list.add_theme_stylebox_override("panel", style_box(Color("f6f8f2"), Color("cbd5c8")))
	collection.add_child(mods_list)
	mods_status = _make_label("", 15, true)
	collection.add_child(mods_status)
	var actions := HBoxContainer.new()
	actions.add_theme_constant_override("separation", 12)
	collection.add_child(actions)
	actions.add_child(_make_button("Import JAR", _add_mod, true))
	actions.add_child(_make_button("Refresh", _refresh_mods_page))
	collection.add_child(_make_label("Local JAR imports must match your Minecraft version. Modrinth installs include required dependencies.", 15, true))
	_refresh_mods_page()
	_poll_modrinth()

func _search_modrinth() -> void:
	if not is_instance_valid(modrinth_search) or selected_name.is_empty():
		return
	last_modrinth_results = ""
	modrinth_hits.clear()
	modrinth_selected = -1
	for child in modrinth_results.get_children():
		modrinth_results.remove_child(child)
		child.queue_free()
	_update_modrinth_install_button()
	if runtime.search_modrinth_mods(selected_name, modrinth_search.text):
		modrinth_status.text = "Searching Modrinth…"
		modrinth_timer.start()
	else:
		modrinth_status.text = "Search is unavailable. Use the Android build with the Modrinth bridge."

func _update_modrinth_install_button() -> void:
	if not is_instance_valid(modrinth_install_button):
		return
	modrinth_install_button.disabled = install_busy or modrinth_selected < 0 or modrinth_selected >= modrinth_hits.size()

func _install_modrinth() -> void:
	if modrinth_selected < 0 or modrinth_selected >= modrinth_hits.size():
		return
	var id := str(modrinth_hits[modrinth_selected].get("id", ""))
	if runtime.install_modrinth_mod(selected_name, id):
		modrinth_status.text = "Resolving dependencies and downloading…"
		modrinth_install_button.disabled = true
		modrinth_timer.start()
	else:
		modrinth_status.text = "Could not start the install. Wait for the current task to finish."

func _poll_modrinth() -> void:
	if current_section != "Mods" or not is_instance_valid(modrinth_status):
		return
	var snapshot: Dictionary = runtime.get_modrinth_snapshot()
	if str(snapshot.get("search_instance", selected_name)) != selected_name:
		return
	var search_state := str(snapshot.get("search_state", "unavailable"))
	var results: Array = snapshot.get("results", [])
	var signature := JSON.stringify(results)
	if search_state == "ready" and signature != last_modrinth_results:
		last_modrinth_results = signature
		modrinth_hits = results
		modrinth_selected = -1
		for child in modrinth_results.get_children():
			modrinth_results.remove_child(child)
			child.queue_free()
		for index in results.size():
			var hit: Dictionary = results[index]
			var card := Button.new()
			card.custom_minimum_size.y = 94
			card.toggle_mode = true
			card.alignment = HORIZONTAL_ALIGNMENT_LEFT
			card.text = "%s\n%s · %s downloads\n%s" % [
				str(hit.get("title", "Mod")), str(hit.get("author", "Modrinth")),
				str(hit.get("downloads", 0)), str(hit.get("description", "")).left(145)]
			card.tooltip_text = str(hit.get("description", ""))
			card.add_theme_font_size_override("font_size", 16)
			card.pressed.connect(_select_modrinth_result.bind(index))
			modrinth_results.add_child(card)
			_load_modrinth_icon(str(hit.get("icon_url", "")), card)
		modrinth_status.text = "%d compatible mod(s) found." % results.size() if not results.is_empty() else "No matching Fabric mods for this version."
	elif search_state == "searching":
		modrinth_status.text = str(snapshot.get("search_message", "Searching…"))
	elif search_state == "error":
		modrinth_status.text = str(snapshot.get("search_message", "Search failed."))
	var install_state := str(snapshot.get("install_state", "idle"))
	install_busy = install_state == "installing"
	_update_play()
	if install_state == "installing":
		modrinth_status.text = str(snapshot.get("install_message", "Installing…"))
		modrinth_install_button.disabled = true
	elif install_state in ["installed", "error"] and install_state != last_modrinth_install_state:
		modrinth_status.text = str(snapshot.get("install_message", ""))
		if install_state == "installed":
			_refresh_mods_page()
	last_modrinth_install_state = install_state
	if search_state != "searching" and install_state != "installing":
		modrinth_timer.stop()
		_update_modrinth_install_button()

func _select_modrinth_result(index: int) -> void:
	modrinth_selected = index
	for i in modrinth_results.get_child_count():
		var card := modrinth_results.get_child(i) as Button
		card.button_pressed = i == index
	modrinth_status.text = str(modrinth_hits[index].get("description", ""))
	_update_modrinth_install_button()

func _load_modrinth_icon(url: String, card: Button) -> void:
	if not url.begins_with("https://cdn.modrinth.com/"):
		return
	var request := HTTPRequest.new()
	card.add_child(request)
	request.request_completed.connect(func(result: int, response: int, _headers: PackedStringArray, body: PackedByteArray):
		if result == HTTPRequest.RESULT_SUCCESS and response == 200 and body.size() < 524288:
			var picture := Image.new()
			var format_error := picture.load_png_from_buffer(body)
			if format_error != OK:
				format_error = picture.load_webp_from_buffer(body)
			if format_error == OK:
				picture.resize(48, 48)
				card.icon = ImageTexture.create_from_image(picture)
		request.queue_free()
	)
	if request.request(url) != OK:
		request.queue_free()

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
	var profiles: Array = snapshot.get("profiles", [])
	for index in mods.size():
		var mod_name := str(mods[index])
		var profile: Dictionary = profiles[index] if index < profiles.size() and profiles[index] is Dictionary else {}
		var title := str(profile.get("title", mod_name))
		var version := str(profile.get("version", ""))
		var description := str(profile.get("description", ""))
		mods_list.add_item("%s  %s\n%s" % [title, version, description.left(85)] if not profile.is_empty() else mod_name)
		mods_list.set_item_tooltip(index, "%s\n%s" % [description, mod_name])
	mods_status.text = "%d mod file(s) found." % mods.size() if not mods.is_empty() else "No mod JARs found in this instance."

func _render_accounts_page() -> void:
	_clear_workspace()
	workspace_title.text = "Accounts"
	workspace_subtitle.text = "Connect the Microsoft account that owns Minecraft: Java Edition."
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
	workspace_subtitle.text = "Launcher and runtime status."
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
