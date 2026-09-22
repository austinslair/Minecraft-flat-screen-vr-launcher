package dev.voxyquest.bridge

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.lwjgl.glfw.CallbackBridge
import pojlib.input.EfficientAndroidLWJGLKeycode

/** Android window mode, controlled with a keyboard and mouse. */
class MinecraftFlatActivity : MinecraftGameActivity() {
    private var grabbing = false
    private var pressedButtons = 0
    private val heldKeys = mutableSetOf<Int>()
    private val grabListener = pojlib.input.GrabListener { active ->
        runOnUiThread {
            grabbing = active
            if (active && hasWindowFocus()) window.decorView.requestPointerCapture()
            else window.decorView.releasePointerCapture()
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setOnCapturedPointerListener { _, event -> handleMouse(event, true) }
        CallbackBridge.addGrabListener(grabListener)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && grabbing) window.decorView.requestPointerCapture()
        if (!hasFocus) {
            window.decorView.releasePointerCapture()
            releaseInput()
        }
    }

    override fun onDestroy() {
        releaseInput()
        CallbackBridge.removeGrabListener(grabListener)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_KEYBOARD != InputDevice.SOURCE_KEYBOARD)
            return super.dispatchKeyEvent(event)
        val index = EfficientAndroidLWJGLKeycode.getIndexByKey(event.keyCode)
        if (index < 0) return super.dispatchKeyEvent(event)
        val key = EfficientAndroidLWJGLKeycode.getValueByIndex(index).toInt()
        if (key < 0) return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!down && event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)
        if (down && !heldKeys.add(key)) return true
        if (!down && !heldKeys.remove(key)) return true
        CallbackBridge.holdingAlt = event.isAltPressed
        CallbackBridge.holdingCapslock = event.isCapsLockOn
        CallbackBridge.holdingCtrl = event.isCtrlPressed
        CallbackBridge.holdingNumlock = event.isNumLockOn
        CallbackBridge.holdingShift = event.isShiftPressed
        CallbackBridge.sendKeyPress(key, CallbackBridge.getCurrentMods(), down)
        if (down && event.unicodeChar > 0 && !Character.isISOControl(event.unicodeChar)) {
            CallbackBridge.sendChar(event.unicodeChar.toChar(), CallbackBridge.getCurrentMods())
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            if (window.decorView.hasPointerCapture()) return true
            return handleMouse(event, false)
        }
        CallbackBridge.sendCursorPos(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> CallbackBridge.sendMouseButton(0, true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> CallbackBridge.sendMouseButton(0, false)
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE != InputDevice.SOURCE_MOUSE)
            return super.onGenericMotionEvent(event)
        if (window.decorView.hasPointerCapture()) return true
        return handleMouse(event, false)
    }

    private fun handleMouse(event: MotionEvent, captured: Boolean): Boolean {
        if (captured) {
            CallbackBridge.sendCursorPos(
                CallbackBridge.mouseX + event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                CallbackBridge.mouseY + event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y))
        } else {
            CallbackBridge.sendCursorPos(event.x, event.y)
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            CallbackBridge.sendScroll(
                event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble())
        }
        for ((mask, button) in arrayOf(MotionEvent.BUTTON_PRIMARY to 0,
                MotionEvent.BUTTON_SECONDARY to 1, MotionEvent.BUTTON_TERTIARY to 2)) {
            val wasDown = pressedButtons and mask != 0
            val isDown = event.buttonState and mask != 0
            if (wasDown != isDown) CallbackBridge.sendMouseButton(button, isDown)
        }
        pressedButtons = event.buttonState
        return true
    }

    private fun releaseInput() {
        for (key in heldKeys) CallbackBridge.sendKeyPress(key, 0, false)
        heldKeys.clear()
        for ((mask, button) in arrayOf(MotionEvent.BUTTON_PRIMARY to 0,
                MotionEvent.BUTTON_SECONDARY to 1, MotionEvent.BUTTON_TERTIARY to 2)) {
            if (pressedButtons and mask != 0) CallbackBridge.sendMouseButton(button, false)
        }
        pressedButtons = 0
        CallbackBridge.holdingAlt = false
        CallbackBridge.holdingCtrl = false
        CallbackBridge.holdingShift = false
    }
}
