package dev.voxyquest.bridge

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import org.lwjgl.glfw.CallbackBridge
import pojlib.input.EfficientAndroidLWJGLKeycode

/** Physical keyboard and mouse input is delivered to the focused game surface. */
class MinecraftFlatActivity : MinecraftGameActivity() {
    private var grabbing = false
    private val keys = mutableSetOf<Int>()
    private var buttons = 0
    private val gameView: View get() = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
    private val grabListener = pojlib.input.GrabListener { active ->
        runOnUiThread {
            grabbing = active
            if (active && hasWindowFocus()) gameView.requestPointerCapture()
            else gameView.releasePointerCapture()
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        gameView.setOnCapturedPointerListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX + event.x, CallbackBridge.mouseY + event.y)
            }
            handleMouse(event)
            true
        }
        CallbackBridge.addGrabListener(grabListener)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) releaseInputs()
        else if (!isFinishing) {
            gameView.requestFocus()
            if (grabbing) gameView.requestPointerCapture()
        }
    }

    private fun releaseInputs() {
        keys.forEach { CallbackBridge.sendKeyPress(it, 0, false) }
        keys.clear()
        for (button in 0..4) CallbackBridge.sendMouseButton(button, false)
        buttons = 0
    }

    override fun onDestroy() {
        CallbackBridge.removeGrabListener(grabListener)
        releaseInputs()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val index = EfficientAndroidLWJGLKeycode.getIndexByKey(event.keyCode)
        if (index < 0 || event.keyCode == KeyEvent.KEYCODE_UNKNOWN) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)
        val key = EfficientAndroidLWJGLKeycode.getValueByIndex(index).toInt()
        if (event.action == KeyEvent.ACTION_DOWN) keys.add(key) else keys.remove(key)
        EfficientAndroidLWJGLKeycode.execKey(event, index)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE) && !event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE))
            return super.dispatchGenericMotionEvent(event)
        if (!grabbing) moveAbsolute(event)
        handleMouse(event)
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (!grabbing) moveAbsolute(event)
            handleMouse(event)
            return true
        }
        moveAbsolute(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { gameView.requestFocus(); CallbackBridge.sendMouseButton(0, true) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> CallbackBridge.sendMouseButton(0, false)
        }
        return true
    }

    private fun moveAbsolute(event: MotionEvent) {
        val offset = IntArray(2)
        gameView.getLocationInWindow(offset)
        CallbackBridge.sendCursorPos(event.x - offset[0], event.y - offset[1])
    }

    private fun handleMouse(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            CallbackBridge.sendScroll(event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble())
        }
        val next = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.buttonState
        val masks = intArrayOf(MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY,
            MotionEvent.BUTTON_TERTIARY, MotionEvent.BUTTON_BACK, MotionEvent.BUTTON_FORWARD)
        masks.forEachIndexed { button, mask ->
            if ((buttons xor next) and mask != 0) CallbackBridge.sendMouseButton(button, next and mask != 0)
        }
        buttons = next
    }
}
