package com.classic.myappblocker

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Downloads TikTok videos without watermark using the free public API at
 * https://www.tikwm.com (no key required). Files are saved to
 * Downloads/MyAppBlocker (Android 10+) or the app's private folder (Android 8-9).
 */
class TikTokDownloaderActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var button: Button
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tiktok)
        input = findViewById(R.id.input_url)
        status = findViewById(R.id.text_status)
        button = findViewById(R.id.btn_download)
        button.setOnClickListener { startDownload() }
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun startDownload() {
        if (busy) return
        val raw = input.text.toString().trim()
        if (raw.isEmpty()) {
            status.text = "Paste a TikTok link first"
            return
        }
        busy = true
        button.isEnabled = false
        status.text = "Resolving video..."
        Thread { resolveAndDownload(raw) }.start()
    }

    private fun resolveAndDownload(raw: String) {
        try {
            val api = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(raw, "UTF-8")
            val json = httpGetText(api)
            val obj = JSONObject(json)
            val code = obj.optInt("code", -1)
            if (code != 200 && code != 0) {
                throw Exception(obj.optString("msg", "API error"))
            }
            val data = obj.getJSONObject("data")
            var play = data.optString("play", "")
            if (play.isEmpty()) play = data.optString("wmplay", "")
            if (play.isEmpty()) throw Exception("No video link in response")
            if (!play.startsWith("http")) play = "https://www.tikwm.com$play"

            val safeTitle = data.optString("title", "")
                .filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
                .trim()
                .take(40)
            val name = if (safeTitle.isNotEmpty()) {
                "TikTok_$safeTitle.mp4"
            } else {
                "TikTok_${System.currentTimeMillis()}.mp4"
            }

            runOnUiThread { status.text = "Downloading video..." }
            val savedTo = saveVideo(play, name)
            runOnUiThread {
                busy = false
                button.isEnabled = true
                status.text = "Saved: $savedTo"
                Toast.makeText(this, "Download complete", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                busy = false
                button.isEnabled = true
                status.text = "Failed: ${e.message}"
            }
        }
    }

    private fun httpGetText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() }
                ?: throw Exception("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    private fun saveVideo(videoUrl: String, name: String): String {
        val conn = URL(videoUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            conn.connect()
            val total = conn.contentLengthLong
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/MyAppBlocker"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Could not create file")
                resolver.openOutputStream(uri)?.use { out ->
                    copyWithProgress(conn, out, total)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Downloads/MyAppBlocker/$name"
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: throw Exception("Storage unavailable")
                val file = File(dir, name)
                FileOutputStream(file).use { copyWithProgress(conn, it, total) }
                return "App folder/$name"
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun copyWithProgress(conn: HttpURLConnection, out: OutputStream, total: Long) {
        conn.inputStream.use { input ->
            val buf = ByteArray(16384)
            var done = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                if (total > 0) {
                    val pct = (done * 100 / total).toInt()
                    runOnUiThread { status.text = "Downloading... $pct%" }
                }
            }
            out.flush()
        }
    }
}
