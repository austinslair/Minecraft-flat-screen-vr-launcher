extends RefCounted
## Shared control styling for the launcher and its dynamically created pages.

static func surface(fill: Color, border: Color) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = fill
	box.border_color = border
	box.set_border_width_all(1)
	box.set_corner_radius_all(12)
	box.content_margin_left = 20
	box.content_margin_right = 20
	box.content_margin_top = 16
	box.content_margin_bottom = 16
	return box

static func create() -> Theme:
	var result := Theme.new()
	result.default_font = preload("res://assets/fonts/DejaVuSans.ttf")
	result.default_font_size = 18
	var field := surface(Color("202722"), Color("38443b"))
	var muted := surface(Color("1a201c"), Color("29332c"))
	var focus := surface(Color.TRANSPARENT, Color("a5d578"))
	var selected := surface(Color("314631"), Color("83b863"))
	for type in ["LineEdit", "OptionButton", "Button"]:
		result.set_stylebox("normal", type, field)
		result.set_stylebox("hover", type, surface(Color("29352c"), Color("70915f")))
		result.set_stylebox("pressed", type, selected)
		result.set_stylebox("focus", type, focus)
		result.set_stylebox("disabled", type, muted)
		result.set_stylebox("read_only", type, muted)
		result.set_color("font_color", type, Color("f2f5ed"))
		result.set_color("font_disabled_color", type, Color("839083"))
		result.set_color("font_uneditable_color", type, Color("c7d6c3"))
		result.set_color("font_placeholder_color", type, Color("97a797"))
		result.set_color("caret_color", type, Color("c6e89d"))
		result.set_color("selection_color", type, Color("466d3d"))
	result.set_stylebox("panel", "ItemList", field)
	result.set_stylebox("selected", "ItemList", selected)
	result.set_stylebox("selected_focus", "ItemList", selected)
	result.set_stylebox("cursor", "ItemList", focus)
	result.set_stylebox("cursor_unfocused", "ItemList", StyleBoxEmpty.new())
	result.set_constant("v_separation", "ItemList", 20)
	result.set_color("font_color", "ItemList", Color("e7f0e2"))
	result.set_color("font_selected_color", "ItemList", Color.WHITE)
	result.set_stylebox("panel", "PopupMenu", field)
	result.set_stylebox("hover", "PopupMenu", selected)
	result.set_constant("v_separation", "PopupMenu", 16)
	var scroll := surface(Color("253028"), Color.TRANSPARENT)
	scroll.content_margin_left = 7
	scroll.content_margin_right = 7
	result.set_stylebox("scroll", "VScrollBar", scroll)
	for state in ["grabber", "grabber_highlight", "grabber_pressed"]:
		result.set_stylebox(state, "VScrollBar", surface(Color("668861"), Color.TRANSPARENT))
	return result
