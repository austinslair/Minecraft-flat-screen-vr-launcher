package dev.voxyquest.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView

/** User-granted document access; no broad storage permission is required. */
class ModImportActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "Choose a Fabric mod JAR…"; textSize = 22f }
        setContentView(status)
        if (savedInstanceState == null) {
            try {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, 1)
            } catch (_: Exception) { showResult("No file picker is available on this device.") }
        }
    }

    @Deprecated("Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) { finish(); return }
        val instance = intent.getStringExtra("instance_name") ?: run { finish(); return }
        Thread({
            val message = runCatching {
                var filename = ""
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) filename = it.getString(0)
                }
                contentResolver.openInputStream(uri)?.use {
                    if (filename.endsWith(".mrpack", true)) {
                        LauncherOperations.importModpack(this, it) { step -> runOnUiThread { status.text = step } }
                    } else {
                        val added = LauncherOperations.importMod(instance, filename, it)
                        if (!added.startsWith("Added ")) added else {
                            runOnUiThread { status.text = "Checking what this mod needs…" }
                            added + LauncherOperations.installMissingDependencies(instance)
                        }
                    }
                } ?: "Could not open that file."
            }.getOrDefault("Could not import the file. Check the file and free space.")
            runOnUiThread { showResult(message) }
        }, "VoxyQuest-ModImport").start()
    }

    private fun showResult(message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle(if (message.contains("modpack")) "Add modpack" else "Add mod").setMessage(message)
            .setCancelable(false).setPositiveButton("Done") { _, _ -> finish() }.show()
    }
}
