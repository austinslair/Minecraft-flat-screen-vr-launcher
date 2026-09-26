package dev.voxyquest.bridge

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.File
import pojlib.API
import pojlib.PojlibRuntime
import pojlib.account.LoginHelper
import pojlib.install.VoxyQuestInstaller
import pojlib.util.JREUtils
import pojlib.util.Logger
import pojlib.util.VLoader
import pojlib.util.json.MinecraftInstances

/** Dedicated game host; the launcher itself never starts an OpenXR session. */
open class MinecraftGameActivity : Activity() {
    companion object {
        @Volatile var isRunning = false
            private set
    }

    private var started = false
    protected lateinit var gameSurface: SurfaceView
        private set
    private var loadingView: GameLoadingView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isRunning) { finish(); return }
        val name = intent.getStringExtra("instance_name") ?: run { finish(); return }
        if (!LoginHelper.isSignedIn() || API.currentAcc == null || LauncherOperations.isBusy()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        isRunning = true
        Logger.getInstance().appendToLog(
            "VoxyQuest launch: game activity created (${if (this is MinecraftFlatActivity) "flat" else "vr"})",
        )
        // Both modes need a visible surface while Minecraft starts. Vivecraft
        // takes over headset presentation once its OpenXR session is ready.
        val vr = this !is MinecraftFlatActivity
        val frame = FrameLayout(this)
        val surface = SurfaceView(this)
        gameSurface = surface
        surface.holder.setFormat(android.graphics.PixelFormat.OPAQUE)
        surface.isFocusable = true
        surface.isFocusableInTouchMode = true
        surface.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {}
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                if (width <= 0 || height <= 0) return
                pojlib.util.FlatDisplay.attach(holder.surface, width, height)
                org.lwjgl.glfw.CallbackBridge.sendUpdateWindowSize(width, height)
                if (!started) startGame(name, vr)
            }
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                // Do not restart the JVM during the Android-to-OpenXR handoff.
                // Flat mode cannot reuse a destroyed native window.
                if (vr) pojlib.util.FlatDisplay.detachNative()
                if (started && !vr) PojlibRuntime.restartSession(this@MinecraftGameActivity)
            }
        })
        frame.addView(surface, FrameLayout.LayoutParams(-1, -1))
        val readyFile = File(filesDir, "minecraft-first-frame")
        readyFile.delete() // Never accept a frame marker left by a previous launch.
        val loading = GameLoadingView(this, readyFile, surface, !vr) {
            loadingView?.let { frame.removeView(it) }
            loadingView = null
            surface.requestFocus()
        }
        loadingView = loading
        frame.addView(loading, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        surface.requestFocus()
    }

    private fun configureJvmMemory() {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val usableMb = ((info.availMem - info.threshold).coerceAtLeast(0L) / (1024L * 1024L))
        // The JVM heap is only part of Minecraft's memory use. Vivecraft/OpenXR, LWJGL,
        // graphics drivers, native libraries, and the still-resident Godot host all need
        // room outside the Java heap. The previous 1 GiB minimum left only ~336 MiB when
        // the device reported 1360 MiB usable, which can make Android kill the process.
        val heapMb = (usableMb / 2L).coerceIn(768L, 1536L)
        API.customRAMValue = true
        API.memoryValue = heapMb.toString()
        Logger.getInstance().appendToLog(
            "VoxyQuest launch: usable RAM ${usableMb}MB, JVM heap ${heapMb}MB",
        )
    }

    private fun startGame(name: String, vr: Boolean) {
        if (started) return
        started = true
        Thread({
            try {
                Logger.getInstance().appendToLog("VoxyQuest launch: initializing runtime")
                PojlibRuntime.initialize(this)
                val registry = VoxyQuestInstaller.readRegistry()
                val instance = registry.toArray().firstOrNull { it.instanceName == name }
                    ?: error("Instance no longer exists")
                check(VoxyQuestInstaller.isInstalled(instance)) { "Instance files are incomplete" }
                VoxyQuestInstaller.ensureLaunchRuntime(this, instance)
                if (instance.loaderId() == "neoforge") {
                    Logger.getInstance().appendToLog("VoxyQuest launch: NeoForge game libraries ready")
                }
                val account = API.currentAcc ?: error("Sign in again")
                check(account.isDemoMode || account.expiresOn >= System.currentTimeMillis()) { "Sign in again" }
                if (vr && pojlib.util.VivecraftRefreshRateFix.apply(java.io.File(instance.gameDir))) {
                    Logger.getInstance().appendToLog("VoxyQuest launch: applied Vivecraft refresh-rate compatibility fix")
                }
                MinecraftInstances.configurePlayMode(instance, vr)
                API.currentInstance = instance
                API.gameReady = false
                configureJvmMemory()

                // This is part of Pojlib's normal launch sequence. It redirects the embedded
                // JVM's stdout/stderr into latestlog.txt so startup failures survive a process
                // exit instead of looking like an unexplained return to Quest Home.
                Logger.getInstance().appendToLog("VoxyQuest launch: enabling Java output capture")
                JREUtils.redirectAndPrintJRELog()
                Logger.getInstance().appendToLog("VoxyQuest launch: Java output capture ready")

                if (vr) {
                    Logger.getInstance().appendToLog("VoxyQuest launch: configuring OpenXR")
                    VLoader.setAndroidInitInfo(this)
                    Logger.getInstance().appendToLog("VoxyQuest launch: OpenXR configuration ready")
                }
                Logger.getInstance().appendToLog("VoxyQuest launch: starting Java VM")
                val exitCode = JREUtils.launchJavaVM(this, instance.generateLaunchArgs(account), instance)
                Logger.getInstance().appendToLog("VoxyQuest launch: Java VM returned $exitCode")
                runOnUiThread { showExit("Minecraft stopped (exit code $exitCode).") }
            } catch (failure: Throwable) {
                Logger.getInstance().appendToLog(
                    "VoxyQuest launch failure: ${failure.javaClass.name}: ${failure.message ?: "no message"}",
                )
                Logger.getInstance().appendToLog(Log.getStackTraceString(failure))
                runOnUiThread {
                    showExit("Minecraft could not start. Restart the launcher and check that the instance finished installing.")
                }
            }
        }, "VoxyQuest-Minecraft").start()
    }

    private fun showExit(message: String) {
        if (isFinishing || isDestroyed) return
        loadingView?.stop()
        AlertDialog.Builder(this).setTitle("VoxyQuest").setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Return to launcher") { _, _ -> PojlibRuntime.restartSession(this) }
            .show()
    }

    override fun onDestroy() {
        loadingView?.stop()
        loadingView = null
        super.onDestroy()
    }

    @Deprecated("Android back callback")
    override fun onBackPressed() {
        AlertDialog.Builder(this).setTitle("Exit Minecraft?")
            .setMessage("This restarts the launcher. Save your world in Minecraft first.")
            .setNegativeButton("Keep playing", null)
            .setPositiveButton("Exit") { _, _ -> PojlibRuntime.restartSession(this) }.show()
    }
}
