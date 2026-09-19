extends SceneTree

class FakeRuntime extends RefCounted:
	var snapshot := {"available": true, "instances": [], "error": ""}
	func get_instance_snapshot() -> Dictionary:
		return snapshot

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
	var fake := FakeRuntime.new()
	ui.runtime = fake
	ui._refresh_instances()
	assert(ui.get_node("InstanceEmpty").text == "No instances installed")
	fake.snapshot.instances = [{"name": "My saved world", "version": "test"}]
	ui._refresh_instances()
	assert(ui.instance_list.get_item_text(0) == "My saved world")
	assert(ui.get_node("QuickEmpty").text == "No version selected")
	assert(ui.get_node("Play").disabled)
	fake.snapshot.error = "Read failed"
	ui._refresh_instances()
	assert(ui.get_node("InstanceEmpty").text == "Could not load instances")
	ui._sync_account({"signed_in": true, "profile_name": "Test player"})
	assert(ui.get_node("AccountTitle").text == "Test player")
	ui.selected_name = "My saved world"
	fake.snapshot.error = ""
	fake.snapshot.instances[0]["installed"] = true
	ui._refresh_instances()
	assert(not ui.get_node("Play").disabled)
	ui._sync_account({"signed_in": false})
	assert(ui.get_node("Play").disabled)
	assert(ui.get_node("AccountTitle").text == "Not Signed In")
	print("PASS: empty states, real profile data, registry errors, Play gating, neutral Home")
	quit()
