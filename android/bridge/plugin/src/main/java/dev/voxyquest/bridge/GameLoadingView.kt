package dev.voxyquest.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.RandomAccessFile
import pojlib.API
import pojlib.util.Constants

/** Visible while the embedded JVM starts; reads only output from this launch. */
internal class GameLoadingView(context: Context, private val readyFile: File,
    private val gameSurface: SurfaceView, private val vr: Boolean,
    private val onFirstFrame: () -> Unit) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val logFile = File(Constants.USER_HOME, "latestlog.txt")
    private var offset = logFile.length()
    private val recent = ArrayDeque<String>()
    private var partialLine = ""
    private var vrConfiguredAt = 0L
    private var vrConnectedAt = 0L
    private var vrFailed = false
    private val output: TextView
    private var running = true
    private val preview = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            readOutput()
            // The first GLFW swap can be NeoForge's early splash screen. Keep
            // the log visible until Vivecraft has configured the VR renderer.
            val now = SystemClock.elapsedRealtime()
            val vrReady = (vrConfiguredAt > 0L && now - vrConfiguredAt >= 1_500L) ||
                (API.gameReady && vrConnectedAt > 0L && now - vrConnectedAt >= 10_000L)
            if (readyFile.isFile && (!vr || (vrReady && !vrFailed))) {
                if (!vr) {
                    PixelCopy.request(gameSurface, preview, { result ->
                        if (!running) return@request
                        if (result == PixelCopy.SUCCESS && hasVisiblePixels()) {
                            stop()
                            onFirstFrame()
                        } else handler.postDelayed(this, 300)
                    }, handler)
                } else {
                    stop()
                    onFirstFrame()
                }
            } else handler.postDelayed(this, 300)
        }
    }

    private fun hasVisiblePixels(): Boolean {
        var visible = 0
        for (y in 0 until preview.height) for (x in 0 until preview.width) {
            val pixel = preview.getPixel(x, y)
            if (Color.red(pixel) > 32 || Color.green(pixel) > 32 || Color.blue(pixel) > 32)
                visible++
        }
        return visible >= 24
    }

    init {
        val padding = (24 * resources.displayMetrics.density).toInt()
        orientation = VERTICAL
        setPadding(padding, padding, padding, padding)
        setBackgroundColor(Color.rgb(17, 25, 32))
        isFocusable = false
        isClickable = false

        val title = TextView(context).apply {
            text = "Launching Minecraft"
            textSize = 25f
            setTextColor(Color.WHITE)
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val subtitle = TextView(context).apply {
            text = "Starting the game · live output"
            textSize = 15f
            setTextColor(Color.rgb(170, 196, 205))
            setPadding(0, padding / 3, 0, padding / 2)
        }
        addView(subtitle)
        addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }, LayoutParams(LayoutParams.MATCH_PARENT, (4 * resources.displayMetrics.density).toInt()))

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(24, 35, 43))
        }
        output = TextView(context).apply {
            text = "Waiting for Minecraft output…"
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(220, 234, 230))
            setPadding(padding / 2, padding / 2, padding / 2, padding / 2)
        }
        scroll.addView(output)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = padding / 2
        })
        handler.post(tick)
    }

    private fun readOutput() {
        try {
            RandomAccessFile(logFile, "r").use { file ->
                if (file.length() < offset) offset = 0
                if (file.length() - offset > 32768) offset = file.length() - 32768
                file.seek(offset)
                val bytes = ByteArray((file.length() - offset).toInt())
                file.readFully(bytes)
                offset = file.filePointer
                val text = (partialLine + String(bytes, Charsets.UTF_8))
                    .replace(Regex("\\u001B\\[[;\\d]*m"), "")
                val completeLines = text.split('\n')
                partialLine = completeLines.last().takeLast(250)
                val lines = completeLines.dropLast(1).filter { it.isNotBlank() }
                for (line in lines) {
                    if (line.contains("New VR render config:"))
                        vrConfiguredAt = SystemClock.elapsedRealtime()
                    if (line.contains("Vivecraft: OpenXR initialized & VR connected"))
                        vrConnectedAt = SystemClock.elapsedRealtime()
                    if (line.contains("Vivecraft: Failed to initialize VR:")) vrFailed = true
                    recent.addLast(line.take(250))
                    while (recent.size > 60) recent.removeFirst()
                }
                if (lines.isNotEmpty()) output.text = recent.joinToString("\n")
            }
        } catch (_: Exception) {
            // The logger may still be opening its file; retry on the next tick.
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}
