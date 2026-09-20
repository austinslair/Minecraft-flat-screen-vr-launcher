extends RefCounted
## Shared control styling for the launcher and its dynamically created pages.

static func surface(fill: Color, border: Color) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = fill
	box.border_color = border
	box.set_border_width_all(1)
	box.set_corner_radius_all(6)
	box.content_margin_left = 16
	box.content_margin_right = 16
	box.content_margin_top = 12
	box.content_margin_bottom = 12
	return box

static func create() -> Theme:
	var result := Theme.new()
	result.default_font = preload("res://assets/fonts/DejaVuSans.ttf")
	result.default_font_size = 18
	var field := surface(Color("191d23"), Color("3b434e"))
	var muted := surface(Color("1d2127"), Color("303640"))
	var focus := surface(Color.TRANSPARENT, Color("c2ccd6"))
	var selected := surface(Color("303943"), Color("677788"))
	for type in ["LineEdit", "OptionButton", "Button"]:
		result.set_stylebox("normal", type, field)
		result.set_stylebox("hover", type, surface(Color("2a3039"), Color("647487")))
		result.set_stylebox("pressed", type, selected)
		result.set_stylebox("focus", type, focus)
		result.set_stylebox("disabled", type, muted)
		result.set_stylebox("read_only", type, muted)
		result.set_color("font_color", type, Color("edf0f4"))
		result.set_color("font_disabled_color", type, Color("7c8693"))
		result.set_color("font_uneditable_color", type, Color("c7d0db"))
		result.set_color("font_placeholder_color", type, Color("98a4b3"))
		result.set_color("caret_color", type, Color("edf0f4"))
		result.set_color("selection_color", type, Color("42576b"))
	result.set_stylebox("panel", "ItemList", field)
	result.set_stylebox("selected", "ItemList", selected)
	result.set_stylebox("selected_focus", "ItemList", selected)
	result.set_stylebox("cursor", "ItemList", focus)
	result.set_stylebox("cursor_unfocused", "ItemList", StyleBoxEmpty.new())
	result.set_constant("v_separation", "ItemList", 16)
	result.set_color("font_color", "ItemList", Color("e4e9f0"))
	result.set_color("font_selected_color", "ItemList", Color.WHITE)
	result.set_stylebox("panel", "PopupMenu", field)
	result.set_stylebox("hover", "PopupMenu", selected)
	result.set_constant("v_separation", "PopupMenu", 16)
	var scroll := surface(Color("20262e"), Color.TRANSPARENT)
	scroll.content_margin_left = 7
	scroll.content_margin_right = 7
	result.set_stylebox("scroll", "VScrollBar", scroll)
	for state in ["grabber", "grabber_highlight", "grabber_pressed"]:
		result.set_stylebox(state, "VScrollBar", surface(Color("66778a"), Color.TRANSPARENT))
	return result
