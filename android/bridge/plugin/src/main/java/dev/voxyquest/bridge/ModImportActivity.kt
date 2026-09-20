package dev.voxyquest.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView

/** User-granted document access; no broad storage permission is required. */
class ModImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Choose a Fabric mod JAR…"; textSize = 22f })
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
                    LauncherOperations.importMod(instance, filename, it)
                } ?: "Could not open that file."
            }.getOrDefault("Could not import the mod. Check the file and free space.")
            runOnUiThread { showResult(message) }
        }, "VoxyQuest-ModImport").start()
    }

    private fun showResult(message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle("Add mod").setMessage(message)
            .setCancelable(false).setPositiveButton("Done") { _, _ -> finish() }.show()
    }
}
