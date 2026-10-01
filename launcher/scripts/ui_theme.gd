extends RefCounted
## Shared control styling for the launcher and its dynamically created pages.

static func surface(fill: Color, border: Color) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = fill
	box.border_color = border
	box.set_border_width_all(1)
	box.set_corner_radius_all(10)
	box.content_margin_left = 16
	box.content_margin_right = 16
	box.content_margin_top = 12
	box.content_margin_bottom = 12
	return box

static func create() -> Theme:
	var result := Theme.new()
	result.default_font = preload("res://assets/fonts/DejaVuSans.ttf")
	result.default_font_size = 18
	var field := surface(Color("f6f8f2"), Color("cbd5c8"))
	var muted := surface(Color("e5eae1"), Color("d0d9ce"))
	var focus := surface(Color.TRANSPARENT, Color("668a67"))
	var selected := surface(Color("dce8cb"), Color("a9c69f"))
	for type in ["LineEdit", "OptionButton", "Button"]:
		result.set_stylebox("normal", type, field)
		result.set_stylebox("hover", type, surface(Color("eef3e9"), Color("8ca98b")))
		result.set_stylebox("pressed", type, selected)
		result.set_stylebox("focus", type, focus)
		result.set_stylebox("disabled", type, muted)
		result.set_stylebox("read_only", type, muted)
		result.set_color("font_color", type, Color("24372a"))
		result.set_color("font_disabled_color", type, Color("809084"))
		result.set_color("font_uneditable_color", type, Color("66796a"))
		result.set_color("font_placeholder_color", type, Color("748578"))
		result.set_color("caret_color", type, Color("24372a"))
		result.set_color("selection_color", type, Color("bed6ad"))
	result.set_stylebox("panel", "ItemList", field)
	result.set_stylebox("selected", "ItemList", selected)
	result.set_stylebox("selected_focus", "ItemList", selected)
	result.set_stylebox("cursor", "ItemList", focus)
	result.set_stylebox("cursor_unfocused", "ItemList", StyleBoxEmpty.new())
	result.set_constant("v_separation", "ItemList", 16)
	result.set_color("font_color", "ItemList", Color("24372a"))
	result.set_color("font_selected_color", "ItemList", Color("1b3324"))
	result.set_stylebox("panel", "PopupMenu", field)
	result.set_stylebox("hover", "PopupMenu", selected)
	result.set_constant("v_separation", "PopupMenu", 16)
	var scroll := surface(Color("dce5d9"), Color.TRANSPARENT)
	scroll.content_margin_left = 7
	scroll.content_margin_right = 7
	result.set_stylebox("scroll", "VScrollBar", scroll)
	for state in ["grabber", "grabber_highlight", "grabber_pressed"]:
		result.set_stylebox(state, "VScrollBar", surface(Color("819b80"), Color.TRANSPARENT))
	return result
