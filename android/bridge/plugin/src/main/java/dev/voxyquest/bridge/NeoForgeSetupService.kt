package dev.voxyquest.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.File
import pojlib.util.Constants
import pojlib.util.JREUtils
import pojlib.util.Logger

/**
 * Runs NeoForge's official installer on the headset, in its own process (see the manifest).
 *
 * The APK no longer carries processed Minecraft clients for each NeoForge version; the
 * installer downloads Minecraft and NeoForge and produces them, as it does on a desktop.
 * The embedded JVM can start only once per process and the installer ends with
 * System.exit, so this process exists only for the length of one install.
 */
class NeoForgeSetupService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val installer = intent?.getStringExtra(EXTRA_INSTALLER)
        val target = intent?.getStringExtra(EXTRA_TARGET)
        if (installer == null || target == null || started) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        started = true
        val status = File(target, STATUS_NAME)
        // Tells the launcher this process exists, and which one, before anything can fail.
        runCatching { status.writeText("$STARTED ${android.os.Process.myPid()}") }
        Thread({
            // USER_HOME is where the launcher keeps libraries; the log stays out of latestlog.txt.
            Constants.USER_HOME = target
            Logger.useLogFile(LOG_NAME)
            Logger.getInstance().appendToLog("VoxyQuest NeoForge setup: process ${android.os.Process.myPid()} started")
            val result = try {
                "exit " + JREUtils.launchTool(
                    this, File(target), 1024L, listOf("-jar", installer, "--installClient", target),
                )
            } catch (failure: Throwable) {
                Logger.getInstance().appendToLog(
                    "VoxyQuest NeoForge setup: " + android.util.Log.getStackTraceString(failure))
                "error " + (failure.message ?: failure.javaClass.simpleName)
            }
            Logger.getInstance().appendToLog("VoxyQuest NeoForge setup: finished, $result")
            runCatching { status.writeText(result) }
            android.os.Process.killProcess(android.os.Process.myPid())
        }, "NeoForge setup").start()
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_INSTALLER = "installer"
        const val EXTRA_TARGET = "target"
        const val LOG_NAME = "neoforge-setup-log.txt"
        const val STATUS_NAME = "neoforge-setup-status.txt"
        /** First word of the status file while the setup process runs; the PID follows it. */
        const val STARTED = "started"
        @Volatile private var started = false
    }
}
