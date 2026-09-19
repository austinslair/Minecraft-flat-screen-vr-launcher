extends Control

var runtime := VoxyQuestRuntimeBridge.new()
var _poll_elapsed := 0.0

@onready var account_label: Label = $Page/Content/AccountLabel
@onready var status_label: Label = $Page/Content/StatusLabel
@onready var sign_in_button: Button = $Page/Content/SignInButton
@onready var code_panel: VBoxContainer = $Page/Content/CodePanel
@onready var device_code_label: Label = $Page/Content/CodePanel/DeviceCode
@onready var verification_label: Label = $Page/Content/CodePanel/VerificationUrl
@onready var open_button: Button = $Page/Content/CodePanel/Actions/OpenMicrosoft
@onready var copy_button: Button = $Page/Content/CodePanel/Actions/CopyCode
@onready var cancel_button: Button = $Page/Content/CodePanel/Actions/Cancel

func _ready() -> void:
	sign_in_button.pressed.connect(_on_sign_in_pressed)
	open_button.pressed.connect(_on_open_pressed)
	copy_button.pressed.connect(_on_copy_pressed)
	cancel_button.pressed.connect(_on_cancel_pressed)

	if runtime.is_available():
		runtime.initialize()
	_refresh_auth_ui()

func _process(delta: float) -> void:
	_poll_elapsed += delta
	if _poll_elapsed >= 0.25:
		_poll_elapsed = 0.0
		_refresh_auth_ui()

func _on_sign_in_pressed() -> void:
	if not runtime.start_microsoft_login():
		_refresh_auth_ui()

func _on_open_pressed() -> void:
	runtime.open_microsoft_login_page()

func _on_copy_pressed() -> void:
	var auth := runtime.get_microsoft_login_snapshot()
	var code := str(auth.get("device_code", ""))
	if not code.is_empty():
		DisplayServer.clipboard_set(code)
		status_label.text = "Microsoft sign-in code copied."

func _on_cancel_pressed() -> void:
	runtime.cancel_microsoft_login()
	_refresh_auth_ui()

func _refresh_auth_ui() -> void:
	var auth := runtime.get_microsoft_login_snapshot()
	var configured := bool(auth.get("configured", false))
	var state := str(auth.get("state", "unavailable"))
	var message := str(auth.get("message", ""))
	var error := str(auth.get("error", ""))
	var code := str(auth.get("device_code", ""))
	var verification_url := str(auth.get("verification_url", ""))
	var expires_in := int(auth.get("expires_in", 0))
	var signed_in := bool(auth.get("signed_in", false))
	var profile_name := str(auth.get("profile_name", ""))
	var profile_uuid := str(auth.get("profile_uuid", ""))
	var demo_mode := bool(auth.get("demo_mode", false))

	if not runtime.is_available():
		account_label.text = "Microsoft account: Android build required"
		status_label.text = "The launcher UI can be edited on desktop, but Microsoft sign-in runs through the Android bridge."
		sign_in_button.disabled = true
		code_panel.visible = false
		return

	if not configured:
		account_label.text = "Microsoft account: not configured"
		status_label.text = "Set VoxyQuest's Microsoft application client ID when building the Android bridge."
		sign_in_button.disabled = true
		code_panel.visible = false
		return

	if signed_in:
		account_label.text = "Signed in: %s%s" % [profile_name, " (demo)" if demo_mode else ""]
		status_label.text = "Minecraft profile UUID: %s" % profile_uuid
		sign_in_button.disabled = true
		sign_in_button.text = "Signed in"
		code_panel.visible = false
		return

	sign_in_button.text = "Sign in with Microsoft"
	sign_in_button.disabled = state in ["starting", "waiting_for_user", "exchanging"]
	code_panel.visible = not code.is_empty()
	device_code_label.text = "Code: %s" % code
	verification_label.text = "Open: %s" % verification_url
	open_button.disabled = verification_url.is_empty()
	copy_button.disabled = code.is_empty()
	cancel_button.disabled = not (state in ["starting", "waiting_for_user", "exchanging"])

	if not error.is_empty():
		status_label.text = error
	elif state == "waiting_for_user":
		status_label.text = "%s\nCode expires in %d:%02d." % [message, expires_in / 60, expires_in % 60]
	elif state == "exchanging":
		status_label.text = "Microsoft verified. Connecting to Xbox Live and Minecraft Services..."
	elif state == "starting":
		status_label.text = "Requesting a Microsoft sign-in code..."
	elif state == "cancelled":
		status_label.text = "Microsoft sign-in cancelled."
	else:
		status_label.text = "Sign in to the Microsoft account that owns Minecraft: Java Edition."
