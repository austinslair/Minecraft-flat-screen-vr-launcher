extends SceneTree
## Capture real Godot frames for design review. Uses only test runtime data.
func _initialize() -> void:
	call_deferred("capture")

func capture() -> void:
	var output := OS.get_environment("VOXYQUEST_PREVIEW_DIR")
	if output.is_empty():
		output = "user://ui-previews"
	DirAccess.make_dir_recursive_absolute(output)
	var ui = load("res://scenes/main.tscn").instantiate()
	root.add_child(ui)
	await process_frame
	var fake = load("res://tests/home_smoke.gd").FakeRuntime.new()
	ui.runtime = fake
	ui._refresh_instances()
	ui._refresh_auth_ui()
	for section in ["Home", "Instances", "Mods", "Accounts", "Settings"]:
		ui._open_section(section)
		await process_frame
		await process_frame
		await RenderingServer.frame_post_draw
		root.get_texture().get_image().save_png(output.path_join(section.to_lower() + ".png"))
	fake.snapshot.instances = [{"name": "Minecraft 1.20.1", "version": "1.20.1", "installed": true}]
	ui._open_section("Instances")
	ui._on_instance_selected(0)
	await process_frame
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(output.path_join("library.png"))
	ui._open_section("Mods")
	await process_frame
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(output.path_join("mod_browser.png"))
	quit()
