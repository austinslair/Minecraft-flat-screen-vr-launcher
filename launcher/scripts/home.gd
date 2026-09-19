extends Control
## Home screen backed by the existing Android/Pojlib account and instance bridge.
signal navigation_requested(section: String)
signal launch_requested
signal sign_in_requested
signal install_requested
signal change_instance_requested

var nav_buttons: Array[Button] = []
var runtime: RefCounted = VoxyQuestRuntimeBridge.new()
var selected_name := ""
var installed_instances: Array = []
var signed_in := false
var install_busy := false
var install_window: Window
var install_name: LineEdit
var install_version: OptionButton
var install_submit: Button
var instance_list: ItemList
var install_status: Label
var install_timer: Timer

func _ready() -> void:
	for section in ["Home", "Instances", "Mods", "Accounts", "Settings"]:
		var button := get_node(section) as Button
		nav_buttons.append(button)
		button.pressed.connect(_navigate.bind(button))
	for item in find_children("*", "Button", true, false):
		style_button(item)
	_build_instance_window()
	_select_nav($Home)
	$AccountWindow.close_requested.connect($AccountWindow.hide)
	$AccountWindow/AccountUI.auth_changed.connect(_sync_account)
	_sync_account(runtime.get_microsoft_login_snapshot())
	_refresh_instances()
	if OS.get_name() == "Android":
		$Minimize.hide()
		$Close.hide()
	$ChangeInstance.pressed.connect(_on_install_pressed)
	$Account.pressed.connect(_on_account_pressed)
	$Play.pressed.connect(_on_play_pressed)
	$Close.pressed.connect(func(): get_tree().quit())
	$Minimize.pressed.connect(func(): DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_MINIMIZED))

func style_box(fill: Color, border: Color) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = fill
	box.border_color = border
	box.set_border_width_all(1)
	box.set_corner_radius_all(6)
	return box

func style_button(button: Button) -> void:
	button.add_theme_stylebox_override("normal", style_box(Color(0, 0, 0, 0), Color(0, 0, 0, 0)))
	button.add_theme_stylebox_override("hover", style_box(Color(0.85, 0.88, 0.86, 0.07), Color(0.8, 0.85, 0.81, 0.22)))
	button.add_theme_stylebox_override("pressed", style_box(Color(0.8, 0.85, 0.82, 0.12), Color(0.8, 0.85, 0.81, 0.35)))
	button.add_theme_stylebox_override("focus", style_box(Color(0, 0, 0, 0), Color(0.85, 0.9, 0.87, 0.8)))
	if button == $ChangeInstance:
		button.add_theme_stylebox_override("normal", style_box(Color(0.025, 0.08, 0.025, 0.85), Color(0.56, 0.86, 0.36)))
		button.add_theme_color_override("font_color", Color(0.68, 0.93, 0.5))

func _select_nav(button: Button) -> void:
	for item in nav_buttons:
		# Home never receives a filled selection background.
		var selected := item == button and item != $Home
		item.add_theme_stylebox_override("normal", style_box(
			Color(0.85, 0.88, 0.86, 0.06) if selected else Color.TRANSPARENT,
			Color(0.8, 0.85, 0.81, 0.15) if selected else Color.TRANSPARENT))
		var caption := get_node(str(item.name) + "Text") as Label
		caption.modulate = Color.WHITE if item == button else Color(0.78, 0.82, 0.79)

func _navigate(button: Button) -> void:
	_select_nav(button)
	navigation_requested.emit(str(button.name))
	if button == $Accounts:
		_on_account_pressed()
	elif button == $Instances:
		_on_install_pressed()
	elif button == $Mods:
		_show_message("Mods", "Install and select an instance before managing mods.")
	elif button == $Settings:
		_show_message("Settings", "Game settings will be available when the game runtime is ready.")

func _show_message(heading: String, message: String) -> void:
	$ActionDialog.title = heading
	$ActionDialog.dialog_text = message
	$ActionDialog.popup_centered()

func _on_play_pressed() -> void:
	if $Play.disabled:
		return
	if not runtime.launch_minecraft_vr(selected_name):
		_show_message("Could not start Minecraft", "Check your sign-in and installed instance, then try again.")

func _on_account_pressed() -> void:
	$AccountWindow/AccountUI._refresh_auth_ui()
	$AccountWindow.popup_centered()
	var auth: Dictionary = runtime.get_microsoft_login_snapshot()
	if auth.get("configured", false) and not auth.get("signed_in", false) and not auth.get("state", "") in ["starting", "waiting_for_user", "exchanging"]:
		$AccountWindow/AccountUI._on_sign_in_pressed()

func _sync_account(auth: Dictionary) -> void:
	signed_in = bool(auth.get("signed_in", false))
	_update_play()
	$AccountTitle.text = str(auth.get("profile_name", "")) if auth.get("signed_in", false) else "Not Signed In"
	$AccountSubtitle.text = "Microsoft account" if auth.get("signed_in", false) else "Sign in with Microsoft"

func _on_install_pressed() -> void:
	_refresh_instances()
	install_version.clear()
	for version in runtime.get_install_versions():
		install_version.add_item(str(version))
	_poll_install()
	install_window.popup_centered()

func _refresh_instances() -> void:
	var snapshot: Dictionary = runtime.get_instance_snapshot()
	installed_instances = snapshot.get("instances", [])
	instance_list.clear()
	for instance in installed_instances:
		instance_list.add_item(str(instance.get("name", "Unnamed instance")))
	if not snapshot.get("available", false):
		$InstanceEmpty.text = "No instances available"
		$InstanceDescription.text = "Install Minecraft in the Android launcher to get started."
	elif not str(snapshot.get("error", "")).is_empty():
		$InstanceEmpty.text = "Could not load instances"
		$InstanceDescription.text = "Your saved profiles could not be read. Try refreshing."
	elif installed_instances.is_empty():
		$InstanceEmpty.text = "No instances installed"
		$InstanceDescription.text = "Install Minecraft with Fabric and Vivecraft to get started."
	else:
		$InstanceEmpty.text = "%d saved instance(s)" % installed_instances.size()
		$InstanceDescription.text = "Choose an instance to play, or install another version."
	_update_play()

func _update_play() -> void:
	var selected: Dictionary = {}
	for instance in installed_instances:
		if instance.get("name", "") == selected_name:
			selected = instance
	$Play.disabled = not (signed_in and not install_busy and selected.get("installed", false))
	$Play.modulate = Color(0.42, 0.46, 0.41) if $Play.disabled else Color.WHITE
	$Play.tooltip_text = "Sign in and select a fully installed instance to play Minecraft VR." if $Play.disabled else "Play Minecraft VR"
	$QuickEmpty.text = "No version selected"
	if not selected.is_empty():
		$InstanceEmpty.text = selected_name
		$InstanceDescription.text = "Minecraft %s · Fabric · Vivecraft" % selected.get("version", "")
		$QuickEmpty.text = "Minecraft %s · VR" % selected.get("version", "")

func _build_instance_window() -> void:
	install_window = Window.new()
	install_window.title = "Minecraft instances"
	install_window.size = Vector2i(700, 560)
	install_window.visible = false
	install_window.exclusive = true
	add_child(install_window)
	install_window.close_requested.connect(install_window.hide)
	var margin := MarginContainer.new()
	install_window.add_child(margin)
	margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	for side in ["left", "right", "top", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 24)
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 14)
	margin.add_child(box)
	var heading := Label.new()
	heading.text = "Installed instances — select one to play"
	box.add_child(heading)
	instance_list = ItemList.new()
	instance_list.custom_minimum_size.y = 130
	box.add_child(instance_list)
	instance_list.item_selected.connect(func(index: int):
		selected_name = str(installed_instances[index].get("name", ""))
		_update_play())
	install_name = LineEdit.new()
	install_name.placeholder_text = "New instance name"
	install_name.max_length = 48
	box.add_child(install_name)
	install_version = OptionButton.new()
	box.add_child(install_version)
	install_submit = Button.new()
	install_submit.text = "Install Minecraft VR"
	install_submit.custom_minimum_size.y = 48
	box.add_child(install_submit)
	install_submit.pressed.connect(_start_install)
	install_status = Label.new()
	install_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	box.add_child(install_status)
	install_timer = Timer.new()
	install_timer.wait_time = 0.5
	add_child(install_timer)
	install_timer.timeout.connect(_poll_install)

func _start_install() -> void:
	if install_name.text.strip_edges().is_empty():
		install_status.text = "Enter a name for this instance."
		return
	if install_version.item_count == 0:
		return
	if runtime.install_instance(install_name.text.strip_edges(), install_version.get_item_text(install_version.selected)):
		install_timer.start()
	_poll_install()

func _poll_install() -> void:
	var snapshot: Dictionary = runtime.get_install_snapshot()
	var busy: bool = snapshot.get("state", "") == "installing"
	install_busy = busy
	install_submit.disabled = busy or install_version.item_count == 0
	install_name.editable = not busy
	install_version.disabled = busy
	install_status.text = str(snapshot.get("message", ""))
	if busy:
		_update_play()
		install_timer.start()
	else:
		install_timer.stop()
		if snapshot.get("state", "") == "installed":
			selected_name = str(snapshot.get("installed_name", ""))
		_refresh_instances()
