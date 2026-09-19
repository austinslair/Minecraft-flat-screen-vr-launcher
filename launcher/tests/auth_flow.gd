extends SceneTree

class FakeAuth extends RefCounted:
	var opened := 0
	var open_ok := true
	var snapshot := {"configured": true, "state": "idle", "signed_in": false}
	func is_available() -> bool:
		return true
	func get_microsoft_login_snapshot() -> Dictionary:
		return snapshot
	func start_microsoft_login() -> bool:
		snapshot.state = "starting"
		return true
	func open_microsoft_login_page() -> bool:
		opened += 1
		return open_ok
	func cancel_microsoft_login() -> void:
		snapshot.state = "cancelled"

func _initialize() -> void:
	call_deferred("run_checks")

func run_checks() -> void:
	create_timer(10).timeout.connect(func(): quit(1))
	var ui = load("res://scenes/account.tscn").instantiate()
	root.add_child(ui)
	await process_frame
	var fake := FakeAuth.new()
	ui.runtime = fake
	ui._on_sign_in_pressed()
	assert(fake.opened == 0)
	fake.snapshot.merge({"state": "waiting_for_user", "device_code": "TEST-CODE", "verification_url": "https://microsoft.com/devicelogin", "expires_in": 120}, true)
	ui._refresh_auth_ui()
	assert(fake.opened == 1)
	assert(ui.code_panel.visible)
	ui._refresh_auth_ui()
	assert(fake.opened == 1)
	ui._on_cancel_pressed()
	assert(not ui.is_processing())
	ui._on_sign_in_pressed()
	fake.open_ok = false
	fake.snapshot.state = "waiting_for_user"
	ui._refresh_auth_ui()
	assert(ui.status_label.text.contains("Browser could not open"))
	fake.open_ok = true
	ui._on_open_pressed()
	fake.snapshot.merge({"state": "signed_in", "signed_in": true, "profile_name": "Test"}, true)
	ui._refresh_auth_ui()
	assert(ui.sign_in_button.disabled)
	assert(not ui.is_processing())
	print("PASS: waits for device code, opens browser once, retries failures, cancels polling, completes sign-in")
	quit()
