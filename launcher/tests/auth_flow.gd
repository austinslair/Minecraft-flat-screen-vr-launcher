extends SceneTree

class FakeAuth extends RefCounted:
	var opened := 0
	var open_ok := true
	var snapshot := {"configured": true, "state": "idle", "signed_in": false}

	func is_available() -> bool:
		return true

	func initialize() -> bool:
		return true

	func get_microsoft_login_snapshot() -> Dictionary:
		return snapshot

	func start_microsoft_login() -> bool:
		snapshot.state = "starting"
		return true

	func open_microsoft_login_page() -> bool:
		opened += 1
		return open_ok

	func get_instance_snapshot() -> Dictionary:
		return {"available": true, "instances": [], "error": ""}

func _initialize() -> void:
	call_deferred("run_checks")

func run_checks() -> void:
	create_timer(10).timeout.connect(func(): quit(1))
	var fake := FakeAuth.new()
	var ui = load("res://scenes/main.tscn").instantiate()
	ui.runtime = fake
	root.add_child(ui)
	await process_frame

	assert(not ui.has_node("AccountWindow"))
	assert(not ui.account_code.visible)

	ui._on_account_pressed()
	assert(fake.opened == 0)
	assert(ui.is_processing())

	fake.snapshot.merge({
		"state": "waiting_for_user",
		"device_code": "TEST-CODE",
		"verification_url": "https://microsoft.com/devicelogin",
		"expires_in": 120
	}, true)
	ui._refresh_auth_ui()
	assert(fake.opened == 1)
	assert(ui.account_code.visible)
	assert(ui.account_code.text == "Code: TEST-CODE")
	assert(ui.get_node("AccountSubtitle").text == "Sign in with Microsoft")

	ui._refresh_auth_ui()
	assert(fake.opened == 1)

	fake.open_ok = false
	ui._on_account_pressed()
	assert(fake.opened == 2)
	assert(ui.get_node("AccountSubtitle").text.contains("retry browser"))

	fake.open_ok = true
	ui._on_account_pressed()
	assert(fake.opened == 3)

	fake.snapshot.merge({
		"state": "signed_in",
		"signed_in": true,
		"profile_name": "Test"
	}, true)
	ui._refresh_auth_ui()
	assert(not ui.account_code.visible)
	assert(not ui.is_processing())
	assert(ui.get_node("AccountTitle").text == "Test")
	print("PASS: inline code appears, browser opens once, retries from account button, and separate login window is removed")
	quit()
