extends SceneTree

class FakeRuntime extends RefCounted:
	var snapshot := {"available": true, "instances": [], "error": ""}
	var auth := {"configured": true, "state": "idle", "signed_in": false, "device_code": "", "profile_name": ""}
	var renamed := false
	var removed := false
	func is_available() -> bool:
		return true
	func initialize() -> bool:
		return true
	func get_info() -> Dictionary:
		return {"available": true, "engine": "Godot", "bridge_version": "test", "pojlib": "godot_host_ready"}
	func get_instance_snapshot() -> Dictionary:
		return snapshot
	func get_install_versions() -> Array:
		return ["test"]
	func get_install_snapshot() -> Dictionary:
		return {"state": "idle", "message": "", "installed_name": ""}
	func install_instance(_name: String, _version: String) -> bool:
		return true
	func rename_instance(old_name: String, new_name: String) -> bool:
		for item in snapshot.instances:
			if item.name == old_name:
				item.name = new_name
				renamed = true
				return true
		return false
	func remove_instance(name: String) -> bool:
		for index in range(snapshot.instances.size() - 1, -1, -1):
			if snapshot.instances[index].name == name:
				snapshot.instances.remove_at(index)
				removed = true
				return true
		return false
	func get_instance_mods(_name: String) -> Dictionary:
		return {"available": true, "mods": ["Vivecraft.jar", "example.jar"], "error": ""}
	func get_microsoft_login_snapshot() -> Dictionary:
		return auth
	func start_microsoft_login() -> bool:
		auth.state = "starting"
		return true
	func open_microsoft_login_page() -> bool:
		return true
	func cancel_microsoft_login() -> void:
		auth.state = "cancelled"
	func launch_minecraft_vr(_name: String) -> bool:
		return true

func _initialize() -> void:
	call_deferred("run_checks")

func run_checks() -> void:
	create_timer(10).timeout.connect(func(): quit(1))
	var ui = load("res://scenes/main.tscn").instantiate()
	root.add_child(ui)
	await process_frame
	assert(ui.get_node("Play").disabled)
	assert(ui.get_node("AccountTitle").text == "Not Signed In")
	assert(ui.get_node_or_null("Avatar") == null)
	assert(ui.get_node_or_null("NewsCard0") == null)
	assert(ui.get_node_or_null("Version") == null)
	assert(ui.get_node("Home").get_theme_stylebox("normal").bg_color.a == 0)
	assert(ui.get_node("AccountTitle").clip_text)

	var fake := FakeRuntime.new()
	ui.runtime = fake
	ui._refresh_instances()
	assert(ui.get_node("InstanceEmpty").text == "No instances installed")

	fake.snapshot.instances = [{"name": "My saved world", "version": "test", "installed": true}]
	ui._refresh_instances()
	assert(ui.get_node("QuickEmpty").text == "No version selected")
	assert(ui.get_node("Play").disabled)

	ui._navigate(ui.get_node("Instances"))
	assert(ui.current_section == "Instances")
	assert(ui.workspace.visible)
	assert(ui.instance_list.get_item_count() == 1)
	await process_frame
	await process_frame
	assert(ui.workspace_body.get_parent() is ScrollContainer)
	assert(ui.workspace_body.size.x <= ui.workspace.size.x)
	assert(ui.install_submit.get_global_rect().end.x <= ui.workspace.get_global_rect().end.x)
	assert(ui.instance_list.get_item_text(0).contains("My saved world"))
	ui._on_instance_selected(0)
	assert(ui.selected_name == "My saved world")
	assert(ui.instance_rename.text == "My saved world")

	ui._sync_account({"signed_in": true, "profile_name": "Test player"})
	assert(ui.get_node("AccountTitle").text == "Test player")
	assert(not ui.get_node("Play").disabled)

	ui._navigate(ui.get_node("Mods"))
	assert(ui.current_section == "Mods")
	assert(ui.mods_list.get_item_count() == 2)

	ui._navigate(ui.get_node("Accounts"))
	assert(ui.current_section == "Accounts")
	assert(ui.account_page_action != null)

	ui._navigate(ui.get_node("Settings"))
	assert(ui.current_section == "Settings")
	assert(ui.workspace_title.text == "Settings")

	ui._navigate(ui.get_node("Home"))
	assert(ui.current_section == "Home")
	assert(not ui.workspace.visible)

	fake.snapshot.error = "Read failed"
	ui._refresh_instances()
	assert(ui.get_node("InstanceEmpty").text == "Could not load instances")
	ui._sync_account({"signed_in": false})
	assert(ui.get_node("Play").disabled)
	assert(ui.get_node("AccountTitle").text == "Not Signed In")
	print("PASS: interactive tabs, instance selection, mods/account/settings pages, Play gating")
	quit()
