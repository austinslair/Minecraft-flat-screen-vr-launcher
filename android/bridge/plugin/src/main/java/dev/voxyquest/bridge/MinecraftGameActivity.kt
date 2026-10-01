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
import pojlib.util.PerformanceTuning
import pojlib.util.Renderer
import pojlib.util.SableNativeFix
import pojlib.util.VLoader
import pojlib.util.json.MinecraftInstances

/** Dedicated game host; the launcher itself never starts an OpenXR session. */
open class MinecraftGameActivity : Activity() {
    companion object {
        const val EXTRA_RENDERER = "renderer"
        @Volatile var isRunning = false
            private set
    }

    private var started = false
    private var launchStartedAt = 0L
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
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                if (!vr) fastestRefreshRate?.let { hintSurfaceFrameRate(holder.surface, it) }
            }
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
        val loading = GameLoadingView(this, readyFile, surface, vr) {
            if (vr) {
                // Vivecraft now owns headset presentation. The EGL bridge moves
                // this context to a pbuffer on its next render-thread swap, so
                // the Android mirror cannot pace either OpenXR eye.
                pojlib.util.FlatDisplay.detachNative()
            }
            Logger.getInstance().appendToLog(
                "VoxyQuest launch: ${if (vr) "VR renderer handoff" else "first visible frame"} after ${android.os.SystemClock.elapsedRealtime() - launchStartedAt}ms",
            )
            loadingView?.let { frame.removeView(it) }
            loadingView = null
            surface.requestFocus()
        }
        loadingView = loading
        frame.addView(loading, FrameLayout.LayoutParams(-1, -1))
        frame.bringChildToFront(loading)
        setContentView(frame)
        if (!vr) requestFastestDisplayMode()
        surface.requestFocus()
    }

    private var fastestRefreshRate: Float? = null

    /**
     * Flat mode: vsync paces Minecraft to the display, so a panel left at 60/72/90 Hz caps
     * the frame rate there. Ask for the fastest mode at the current resolution instead.
     */
    private fun requestFastestDisplayMode() {
        runCatching {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val current = display.mode
            val fastest = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            fastestRefreshRate = fastest.refreshRate
            window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
            Logger.getInstance().appendToLog(
                "VoxyQuest launch: display ${current.refreshRate}Hz, requested ${fastest.refreshRate}Hz",
            )
        }
    }

    private fun hintSurfaceFrameRate(surface: android.view.Surface, rate: Float) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        runCatching { surface.setFrameRate(rate, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
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
        // Up to 3 GiB where half of the free memory allows it: mods and long render distances
        // otherwise keep the collector running against a full heap, which costs frames.
        val heapMb = (usableMb / 2L).coerceIn(768L, 3072L)
        API.customRAMValue = true
        API.memoryValue = heapMb.toString()
        Logger.getInstance().appendToLog(
            "VoxyQuest launch: usable RAM ${usableMb}MB, JVM heap ${heapMb}MB",
        )
    }

    private fun startGame(name: String, vr: Boolean) {
        if (started) return
        started = true
        launchStartedAt = android.os.SystemClock.elapsedRealtime()
        Thread({
            try {
                Logger.getInstance().appendToLog("VoxyQuest launch: initializing runtime")
                PojlibRuntime.initialize(this)
                val registry = VoxyQuestInstaller.readRegistry()
                val instance = registry.toArray().firstOrNull { it.instanceName == name }
                    ?: error("Instance no longer exists")
                if (vr && instance.loaderId() == "neoforge" &&
                    !VoxyQuestInstaller.supportsNeoForgeVr(instance.versionName)) {
                    error("No packaged NeoForge VR build is available for ${instance.versionName}.")
                }
                check(VoxyQuestInstaller.isInstalled(instance)) { "Instance files are incomplete" }
                VoxyQuestInstaller.ensureLaunchRuntime(this, instance)
                ModDoctor.prepareForLaunch(instance)
                Logger.getInstance().appendToLog(
                    "VoxyQuest launch: runtime checked after ${android.os.SystemClock.elapsedRealtime() - launchStartedAt}ms",
                )
                if (instance.loaderId() == "neoforge") {
                    Logger.getInstance().appendToLog("VoxyQuest launch: NeoForge game libraries ready")
                }
                val account = API.currentAcc ?: error("Sign in again")
                check(account.isDemoMode || account.expiresOn >= System.currentTimeMillis()) { "Sign in again" }
                if (vr) {
                    val gameDir = java.io.File(instance.gameDir)
                    // Asks Horizon OS for sustained high CPU/GPU levels (compat/vivecraft).
                    val performance = runCatching {
                        assets.open("voxyquest/compat/VoxyQuestXr.bin").use { it.readBytes() }
                    }.getOrNull()
                    val patched = if (instance.loaderId() == "neoforge") {
                        assets.open(VoxyQuestInstaller.neoForgeVivecraftAsset(instance.versionName)).use { bundled ->
                            pojlib.util.VivecraftRefreshRateFix.apply(gameDir, bundled, performance)
                        }
                    } else pojlib.util.VivecraftRefreshRateFix.apply(gameDir, null, performance)
                    if (patched) Logger.getInstance().appendToLog(
                        "VoxyQuest launch: applied Vivecraft OpenXR compatibility fixes",
                    )
                }
                if (instance.loaderId() != "neoforge" ||
                    VoxyQuestInstaller.supportsNeoForgeVr(instance.versionName)) {
                    MinecraftInstances.configurePlayMode(instance, vr)
                }
                if (File(applicationInfo.nativeLibraryDir, "libsable_rapier.so").isFile) {
                    try {
                        val sable = SableNativeFix.apply(File(instance.gameDir))
                        if (sable.patched.isNotEmpty()) Logger.getInstance().appendToLog(
                            "VoxyQuest launch: patched ${sable.patched} to load Sable's Android physics natives",
                        )
                        if (sable.unsupported.isNotEmpty()) Logger.getInstance().appendToLog(
                            "VoxyQuest launch: ${sable.unsupported} is not Sable " +
                                "${SableNativeFix.SUPPORTED_VERSION}; its physics natives cannot load on Android",
                        )
                    } catch (e: java.io.IOException) {
                        Logger.getInstance().appendToLog("VoxyQuest launch: Sable natives not patched: ${e.message}")
                    }
                }
                if (vr) {
                    val helper = runCatching {
                        assets.open("voxyquest/compat/VoxyQuestVrHold.bin").use { it.readBytes() }
                    }.getOrNull()
                    if (helper != null) {
                        val simulated = pojlib.util.SimulatedVrFix.apply(File(instance.gameDir), helper)
                        if (simulated.patched.isNotEmpty()) Logger.getInstance().appendToLog(
                            "VoxyQuest launch: patched ${simulated.patched} so Create Aeronautics controls follow the VR controller",
                        )
                        if (simulated.unsupported.isNotEmpty()) Logger.getInstance().appendToLog(
                            "VoxyQuest launch: left ${simulated.unsupported} unpatched; its Simulated version is unfamiliar",
                        )
                    }
                }
                PerformanceTuning.apply(java.io.File(instance.gameDir), vr)
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
                JREUtils.renderer = Renderer.fromId(intent.getStringExtra(EXTRA_RENDERER))
                Logger.getInstance().appendToLog("VoxyQuest launch: renderer ${JREUtils.renderer.displayName}")
                Logger.getInstance().appendToLog("VoxyQuest launch: starting Java VM")
                Logger.getInstance().appendToLog(
                    "VoxyQuest launch: Java entry after ${android.os.SystemClock.elapsedRealtime() - launchStartedAt}ms",
                )
                // Minecraft's render loop runs on this launch thread. Let it
                // compete with Android UI work at display priority in both modes.
                val threadId = android.os.Process.myTid()
                val oldPriority = android.os.Process.getThreadPriority(threadId)
                val raised = runCatching {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY)
                }.isSuccess
                val exitCode = try {
                    JREUtils.launchJavaVM(this, instance.generateLaunchArgs(account), instance, !vr)
                } finally {
                    if (raised) runCatching { android.os.Process.setThreadPriority(oldPriority) }
                }
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
