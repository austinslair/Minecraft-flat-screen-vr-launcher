package dev.voxyquest.bridge

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import java.io.DataOutputStream
import java.io.File
import org.lwjgl.glfw.CallbackBridge
import pojlib.input.EfficientAndroidLWJGLKeycode

/** Physical keyboard and mouse input is delivered to the focused game surface. */
class MinecraftFlatActivity : MinecraftGameActivity() {
    private var grabbing = false
    private val keys = mutableSetOf<Int>()
    private var buttons = 0
    private var controllerId = -1
    private var controllerButtons = 0
    private val controllerAxes = FloatArray(6)
    private val controllerFile: File by lazy { File(filesDir, "flat-gamepad.bin") }
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
        gameView.isFocusableInTouchMode = true
        gameView.requestFocus()
        controllerId = InputDevice.getDeviceIds().firstOrNull { id ->
            InputDevice.getDevice(id)?.sources?.and(InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        } ?: -1
        writeController()
        gameView.setOnCapturedPointerListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                var dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                var dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                // Some Android mouse drivers put captured deltas in x/y instead.
                if (dx == 0f && dy == 0f) { dx = event.x; dy = event.y }
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX + dx, CallbackBridge.mouseY + dy)
            }
            handleMouse(event)
            true
        }
        CallbackBridge.addGrabListener(grabListener)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            releaseInputs()
            controllerButtons = 0
            controllerAxes.fill(0f)
            writeController()
        }
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
        CallbackBridge.holdingAlt = false
        CallbackBridge.holdingCtrl = false
        CallbackBridge.holdingShift = false
    }

    override fun onDestroy() {
        CallbackBridge.removeGrabListener(grabListener)
        releaseInputs()
        controllerId = -1
        writeController()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            val button = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> 0; KeyEvent.KEYCODE_BUTTON_B -> 1
                KeyEvent.KEYCODE_BUTTON_X -> 2; KeyEvent.KEYCODE_BUTTON_Y -> 3
                KeyEvent.KEYCODE_BUTTON_L1 -> 4; KeyEvent.KEYCODE_BUTTON_R1 -> 5
                KeyEvent.KEYCODE_BUTTON_SELECT -> 6; KeyEvent.KEYCODE_BUTTON_START -> 7
                KeyEvent.KEYCODE_BUTTON_THUMBL -> 8; KeyEvent.KEYCODE_BUTTON_THUMBR -> 9
                KeyEvent.KEYCODE_DPAD_UP -> 11; KeyEvent.KEYCODE_DPAD_RIGHT -> 12
                KeyEvent.KEYCODE_DPAD_DOWN -> 13; KeyEvent.KEYCODE_DPAD_LEFT -> 14
                else -> -1
            }
            if (button >= 0 && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
                controllerId = event.deviceId
                controllerButtons = if (event.action == KeyEvent.ACTION_DOWN)
                    controllerButtons or (1 shl button) else controllerButtons and (1 shl button).inv()
                writeController()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        val index = EfficientAndroidLWJGLKeycode.getIndexByKey(event.keyCode)
        if (index < 0 || event.keyCode == KeyEvent.KEYCODE_UNKNOWN) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)
        val key = EfficientAndroidLWJGLKeycode.getValueByIndex(index).toInt()
        if (event.action == KeyEvent.ACTION_DOWN) keys.add(key) else keys.remove(key)
        EfficientAndroidLWJGLKeycode.execKey(event, index)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            controllerId = event.deviceId
            val device = event.device
            val axisIds = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RZ, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER)
            axisIds.forEachIndexed { i, axis ->
                controllerAxes[i] = if (device?.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null)
                    event.getAxisValue(axis).coerceIn(-1f, 1f) else 0f
            }
            if (device?.getMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK) == null) {
                controllerAxes[2] = event.getAxisValue(MotionEvent.AXIS_RX).coerceIn(-1f, 1f)
                controllerAxes[3] = event.getAxisValue(MotionEvent.AXIS_RY).coerceIn(-1f, 1f)
            }
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (device?.getMotionRange(MotionEvent.AXIS_HAT_X, InputDevice.SOURCE_JOYSTICK) != null) {
                controllerButtons = controllerButtons and (0x7800).inv()
                if (hatY < -0.5f) controllerButtons = controllerButtons or (1 shl 11)
                if (hatX > 0.5f) controllerButtons = controllerButtons or (1 shl 12)
                if (hatY > 0.5f) controllerButtons = controllerButtons or (1 shl 13)
                if (hatX < -0.5f) controllerButtons = controllerButtons or (1 shl 14)
            }
            for (i in 4..5) controllerAxes[i] = controllerAxes[i] * 2f - 1f
            writeController()
            return true
        }
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

    private fun writeController() {
        // A complete snapshot is written to a temporary file, then renamed for the JVM reader.
        val temp = File(filesDir, "flat-gamepad.tmp")
        runCatching {
            DataOutputStream(temp.outputStream().buffered()).use { stream ->
                stream.writeInt(0x56475143)
                stream.writeBoolean(controllerId >= 0 && InputDevice.getDevice(controllerId) != null)
                stream.writeInt(controllerButtons)
                controllerAxes.forEach(stream::writeFloat)
            }
            if (!temp.renameTo(controllerFile)) {
                controllerFile.delete()
                check(temp.renameTo(controllerFile))
            }
        }
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
