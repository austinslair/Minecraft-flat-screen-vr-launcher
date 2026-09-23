extends SceneTree

class FakeRuntime extends RefCounted:
	var snapshot := {"available": true, "instances": [], "error": ""}
	var auth := {"configured": true, "state": "idle", "signed_in": false, "device_code": "", "profile_name": ""}
	var renamed := false
	var removed := false
	var last_mode := ""
	var imported := ""
	var install_args: Array = []
	var install_state := "idle"
	var accept_install := true
	var install_calls := 0
	var searched := ""
	var last_sort := ""
	var last_category := ""
	var modrinth_installed := ""
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
		return {"state": install_state, "message": "", "installed_name": ""}
	func install_instance(_name: String, _version: String) -> bool:
		install_calls += 1
		install_args = [_name, _version]
		return accept_install
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
	func get_modrinth_snapshot() -> Dictionary:
		return {"search_state": "ready", "search_instance": "My saved world", "search_message": "", "results": [{"id": "AANobbMI", "title": "Sodium", "description": "Rendering optimization"}], "install_state": "idle", "install_message": ""}
	func search_modrinth_mods(_name: String, query: String, sort := "relevance", category := "all") -> bool:
		searched = query
		last_sort = sort
		last_category = category
		return true
	func install_modrinth_mod(_name: String, project: String) -> bool:
		modrinth_installed = project
		return true
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
		last_mode = "vr"
		return true
	func launch_minecraft_flat(_name: String) -> bool:
		last_mode = "flat"
		return true
	func add_instance_mod(name: String) -> bool:
		imported = name
		return true

class CatalogPlugin extends RefCounted:
	var response := "[]"
	func getInstallVersionsJson() -> String:
		return response

func _initialize() -> void:
	call_deferred("run_checks")

func run_checks() -> void:
	create_timer(10).timeout.connect(func(): quit(1))
	var bridge := VoxyQuestRuntimeBridge.new()
	bridge._plugin = null
	assert(not bridge.get_install_versions().is_empty())
	bridge._plugin = RefCounted.new()
	assert(not bridge.get_install_versions().is_empty())
	var catalog := CatalogPlugin.new()
	bridge._plugin = catalog
	assert(not bridge.get_install_versions().is_empty())
	catalog.response = "invalid json"
	assert(not bridge.get_install_versions().is_empty())
	catalog.response = '["1.20.1"]'
	assert(bridge.get_install_versions() == ["1.20.1"])
	var ui = load("res://scenes/main.tscn").instantiate()
	root.add_child(ui)
	await process_frame
	assert(ui.get_node("Play").disabled)
	assert(ui.get_node("AccountTitle").text == "Not Signed In")
	assert(ui.get_node_or_null("Avatar") == null)
	assert(ui.get_node_or_null("NewsCard0") == null)
	assert(ui.get_node_or_null("Version") == null)
	assert(ui.get_node("Home").get_theme_stylebox("normal").bg_color.a > 0)
	assert(ui.get_node("Home").position.y < 170)
	assert(ui.get_node("Instances").position.x > ui.get_node("Home").position.x)
	assert(ui.get_node("LibraryHome").visible)
	assert(ui.get_node("AccountTitle").clip_text)
	ui._open_section("Instances")
	assert(ui.install_version.item_count > 0)
	assert(ui.install_submit.disabled)
	assert(ui.instance_empty_hint.text.contains("Android runtime"))
	assert(ui.instance_list.visible)
	ui._open_section("Home")
	assert(ui.get_node("LibraryHome").visible)

	var fake := FakeRuntime.new()
	ui.runtime = fake
	ui._open_section("Mods")
	assert(ui.workspace_body.find_children("*", "Button", true, false).size() == 1)
	ui._open_section("Instances")
	assert(ui.instance_empty_hint.visible)
	fake.snapshot.error = "Could not read saved instances"
	ui._refresh_instances()
	assert(ui.instance_empty_hint.text == "Could not read saved instances")
	fake.snapshot.error = ""
	ui._refresh_instances()
	ui._open_section("Home")
	ui._refresh_instances()
	assert(ui.get_node("InstanceEmpty").text == "No instances installed")

	fake.snapshot.instances = [{"name": "My saved world", "version": "test", "installed": true}]
	ui._refresh_instances()
	assert(ui.library_grid.find_children("*", "Button", true, false).size() >= 1)
	await process_frame
	assert(ui.library_grid.get_child(0).get_child(0).size.y < 50)
	ui._select_library_instance("My saved world")
	assert(ui.library_launch_button.disabled)
	ui.selected_name = ""
	ui._refresh_instances()
	assert(ui.get_node("QuickEmpty").text == "No version selected")
	assert(ui.get_node("Play").disabled)

	ui._navigate(ui.get_node("Instances"))
	assert(ui.current_section == "Instances")
	assert(ui.workspace.visible)
	assert(ui.instance_list.get_item_count() == 1)
	fake.accept_install = false
	ui.install_name.text = "Rejected instance"
	ui.install_submit.pressed.emit()
	assert(ui.install_status.text.contains("rejected"))
	ui._poll_install()
	assert(ui.install_status.text.contains("rejected"))
	assert(ui.get_node("ActionDialog").visible)
	ui.get_node("ActionDialog").hide()
	fake.accept_install = true
	ui.install_submit.pressed.emit()
	assert(fake.install_args == ["Rejected instance", "test"])
	assert(ui.install_status.text == "Preparing installation…")
	ui.install_name.text = ""
	var previous_calls := fake.install_calls
	ui.install_submit.pressed.emit()
	assert(fake.install_calls == previous_calls + 1)
	assert(fake.install_args == ["Minecraft test", "test"])
	assert(ui.install_submit.action_mode == BaseButton.ACTION_MODE_BUTTON_PRESS)


	await process_frame
	await process_frame
	assert(ui.workspace_body.get_parent() is ScrollContainer)
	assert(ui.workspace_body.size.x <= ui.workspace.size.x)
	assert(ui.install_submit.get_global_rect().end.x <= ui.workspace.get_global_rect().end.x)
	assert(ui.instance_list.get_item_text(0).contains("My saved world"))
	ui._on_instance_selected(0)
	assert(ui.selected_name == "My saved world")
	assert(ui.instance_rename.text == "My saved world")
	(ui.workspace_body.get_parent() as ScrollContainer).scroll_vertical = 250
	ui._open_section("Home")
	ui._open_section("Instances")
	await process_frame
	await process_frame
	assert((ui.workspace_body.get_parent() as ScrollContainer).scroll_vertical == 0)
	assert(ui.instance_list.is_visible_in_tree())
	assert(ui.instance_list.get_selected_items()[0] == 0)
	assert(ui.instance_list.get_global_rect().intersection((ui.workspace_body.get_parent() as ScrollContainer).get_global_rect()).size.y > 100)


	ui._sync_account({"signed_in": true, "profile_name": "Test player"})
	assert(ui.get_node("AccountTitle").text == "Test player")
	assert(not ui.get_node("Play").disabled)
	ui.play_mode = "flat"
	ui._on_play_pressed()
	assert(fake.last_mode == "flat")
	ui.play_mode = "vr"
	ui._on_play_pressed()
	assert(fake.last_mode == "vr")
	fake.snapshot.instances[0].installed = false
	ui._refresh_instances()
	assert(ui.get_node("PlaybarCaption").text == "REPAIR REQUIRED")
	fake.snapshot.instances[0].installed = true
	fake.install_state = "installing"
	ui._poll_install()
	assert(ui.get_node("Play").disabled)
	fake.install_state = "error"
	ui._poll_install()
	assert(not ui.get_node("Play").disabled)
	fake.install_state = "idle"
	ui._repair_selected_instance()
	assert(fake.install_args == [ui.selected_name, "test"])

	ui._navigate(ui.get_node("Mods"))
	assert(ui.current_section == "Mods")
	await process_frame
	assert(ui.modrinth_sort.size.y < 65)
	assert(ui.modrinth_category.size.y < 65)
	assert(not ui.get_node("HomeTools").visible)
	assert(ui.mods_list.get_item_count() == 2)
	ui._add_mod()
	assert(fake.imported == ui.selected_name)
	ui.modrinth_search.text = "Sodium"
	ui.modrinth_sort.select(1)
	ui.modrinth_category.select(1)
	ui._search_modrinth()
	assert(fake.searched == "Sodium")
	assert(fake.last_sort == "downloads" and fake.last_category == "optimization")
	ui._poll_modrinth()
	assert(ui.modrinth_results.get_child_count() == 1)
	ui._select_modrinth_result(0)
	ui._update_modrinth_install_button()
	ui._install_modrinth()
	assert(fake.modrinth_installed == "AANobbMI")

	for section in ["Home", "Instances", "Mods", "Accounts", "Settings"]:
		ui._open_section(section)
		await process_frame
		await process_frame
		assert(ui.get_node("Play").is_visible_in_tree())
		assert(ui.get_node("PlayMode").is_visible_in_tree())
		assert(ui.get_node("Play").get_global_rect().position.y >= ui.workspace.get_global_rect().end.y)
		assert(ui.workspace_body.size.x <= ui.workspace.size.x)
		assert(ui.get_node("PageTitle").text == ("Overview" if section == "Home" else section))
	ui.get_node("PlayMode").item_selected.emit(1)
	assert(ui.play_mode == "flat")
	ui.get_node("PlayMode").item_selected.emit(0)
	assert(ui.play_mode == "vr")
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
