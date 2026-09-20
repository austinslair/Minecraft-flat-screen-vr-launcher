package dev.voxyquest.bridge

import android.view.KeyEvent
import android.view.MotionEvent
import org.lwjgl.glfw.CallbackBridge

/** Android window mode, controlled with a keyboard and mouse. */
class MinecraftFlatActivity : MinecraftGameActivity() {
    private var grabbing = false
    private val grabListener = pojlib.input.GrabListener { active ->
        runOnUiThread {
            grabbing = active
            if (active && hasWindowFocus()) window.decorView.requestPointerCapture()
            else window.decorView.releasePointerCapture()
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setOnCapturedPointerListener { _, event ->
            CallbackBridge.sendCursorPos(CallbackBridge.mouseX + event.x, CallbackBridge.mouseY + event.y)
            handleMouse(event)
            true
        }
        CallbackBridge.addGrabListener(grabListener)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && grabbing) window.decorView.requestPointerCapture()
    }

    override fun onDestroy() {
        CallbackBridge.removeGrabListener(grabListener)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 65 + event.keyCode - KeyEvent.KEYCODE_A
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 48 + event.keyCode - KeyEvent.KEYCODE_0
            KeyEvent.KEYCODE_SPACE -> 32
            KeyEvent.KEYCODE_ESCAPE -> 256
            KeyEvent.KEYCODE_ENTER -> 257
            KeyEvent.KEYCODE_TAB -> 258
            KeyEvent.KEYCODE_DEL -> 259
            KeyEvent.KEYCODE_SHIFT_LEFT -> 340
            KeyEvent.KEYCODE_CTRL_LEFT -> 341
            KeyEvent.KEYCODE_ALT_LEFT -> 342
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        CallbackBridge.sendKeyPress(key, CallbackBridge.getCurrentMods(), event.action == KeyEvent.ACTION_DOWN)
        if (event.action == KeyEvent.ACTION_DOWN && event.unicodeChar > 0) {
            CallbackBridge.sendChar(event.unicodeChar.toChar(), CallbackBridge.getCurrentMods())
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        CallbackBridge.sendCursorPos(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> CallbackBridge.sendMouseButton(0, true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> CallbackBridge.sendMouseButton(0, false)
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        CallbackBridge.sendCursorPos(event.x, event.y)
        return handleMouse(event)
    }

    private fun handleMouse(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> CallbackBridge.sendScroll(
                event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble())
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button = when (event.actionButton) {
                    MotionEvent.BUTTON_PRIMARY -> 0
                    MotionEvent.BUTTON_SECONDARY -> 1
                    MotionEvent.BUTTON_TERTIARY -> 2
                    else -> return super.onGenericMotionEvent(event)
                }
                CallbackBridge.sendMouseButton(button, event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
            }
        }
        return true
    }
}
