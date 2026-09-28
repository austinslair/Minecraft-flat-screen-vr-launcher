package dev.voxyquest.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import java.io.File
import pojlib.util.Constants

/** Saves the captured launch log through Android's user-selected document location. */
class LogExportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Export VoxyQuest log…"; textSize = 22f })
        if (savedInstanceState == null) {
            val source = intent.getStringExtra("log_name")
            if (source !in listOf("latestlog.txt", "previouslog.txt") ||
                !File(Constants.USER_HOME, source!!).isFile) {
                showResult("No launch log is available yet.")
                return
            }
            try {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "VoxyQuest-${source.removeSuffix(".txt")}.txt")
                }, REQUEST_SAVE_LOG)
            } catch (_: Exception) {
                showResult("No document save picker is available on this device.")
            }
        }
    }

    @Deprecated("Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SAVE_LOG) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) { finish(); return }
        val sourceName = intent.getStringExtra("log_name")
        if (sourceName !in listOf("latestlog.txt", "previouslog.txt")) { finish(); return }
        Thread({
            val message = runCatching {
                val source = File(Constants.USER_HOME, sourceName!!)
                check(source.isFile && source.length() > 0L) { "The launch log is empty." }
                val target = contentResolver.openOutputStream(uri, "w")
                    ?: error("Could not write to the selected location.")
                target.use { output -> source.inputStream().use { input -> input.copyTo(output) } }
                "Launch log saved to the selected location."
            }.getOrElse { "Could not save the log. Try another location." }
            runOnUiThread { showResult(message) }
        }, "VoxyQuest-LogExport").start()
    }

    private fun showResult(message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle("Export log").setMessage(message)
            .setCancelable(false).setPositiveButton("Done") { _, _ -> finish() }.show()
    }

    companion object { private const val REQUEST_SAVE_LOG = 1 }
}
