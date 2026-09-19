package dev.voxyquest.bridge

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import pojlib.API
import pojlib.PojlibRuntime
import pojlib.account.LoginHelper
import pojlib.install.VoxyQuestInstaller
import pojlib.util.JREUtils
import pojlib.util.VLoader
import pojlib.util.json.MinecraftInstances

/** Dedicated game host; the launcher itself never starts an OpenXR session. */
class MinecraftGameActivity : Activity() {
    companion object {
        @Volatile var isRunning = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isRunning) { finish(); return }
        val name = intent.getStringExtra("instance_name") ?: run { finish(); return }
        if (!LoginHelper.isSignedIn() || API.currentAcc == null || LauncherOperations.isBusy()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val status = TextView(this).apply {
            text = "Starting Minecraft VR…"
            textSize = 24f
            setPadding(48, 48, 48, 48)
        }
        setContentView(status)
        isRunning = true
        Thread({
            try {
                PojlibRuntime.initialize(this)
                val registry = VoxyQuestInstaller.readRegistry()
                val instance = registry.toArray().firstOrNull { it.instanceName == name }
                    ?: error("Instance no longer exists")
                check(VoxyQuestInstaller.isInstalled(instance)) { "Instance files are incomplete" }
                val account = API.currentAcc ?: error("Sign in again")
                check(account.isDemoMode || account.expiresOn >= System.currentTimeMillis()) { "Sign in again" }
                MinecraftInstances.CheckVivecraftConfig(instance)
                API.currentInstance = instance
                API.gameReady = false
                VLoader.setAndroidInitInfo(this)
                val exitCode = JREUtils.launchJavaVM(this, instance.generateLaunchArgs(account), instance)
                runOnUiThread { showExit("Minecraft stopped (exit code $exitCode).") }
            } catch (_: Throwable) {
                runOnUiThread { showExit("Minecraft could not start. Restart the launcher and check that the instance finished installing.") }
            }
        }, "VoxyQuest-Minecraft").start()
    }

    private fun showExit(message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle("VoxyQuest").setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Return to launcher") { _, _ -> PojlibRuntime.restartSession(this) }
            .show()
    }

    @Deprecated("Android back callback")
    override fun onBackPressed() {
        AlertDialog.Builder(this).setTitle("Exit Minecraft?")
            .setMessage("This restarts the launcher. Save your world in Minecraft first.")
            .setNegativeButton("Keep playing", null)
            .setPositiveButton("Exit") { _, _ -> PojlibRuntime.restartSession(this) }.show()
    }
}
