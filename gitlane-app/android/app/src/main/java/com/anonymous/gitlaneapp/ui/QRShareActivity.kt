package com.anonymous.gitlaneapp.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anonymous.gitlaneapp.BundleManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.RepoShareServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shown when the user taps "Share" on a repo.
 * 1. Creates a git bundle via BundleManager
 * 2. Starts RepoShareServer (NanoHTTPD) to serve it
 * 3. Generates + displays a QR code encoding the server URL
 */
class QRShareActivity : AppCompatActivity() {

    private var server: RepoShareServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_share)

        val repoName = intent.getStringExtra("REPO_NAME") ?: run {
            Toast.makeText(this, "No repo selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val repoDir = File(filesDir, "GitLane/$repoName")
        if (!repoDir.exists()) {
            Toast.makeText(this, "Repository not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.title = "Share: $repoName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tvStatus = findViewById<TextView>(R.id.tvShareStatus)
        val tvUrl    = findViewById<TextView>(R.id.tvShareUrl)
        val ivQr     = findViewById<ImageView>(R.id.ivQrCode)

        tvStatus.text = "⏳ Creating git bundle…"

        lifecycleScope.launch {
            try {
                // 1. Create the bundle file
                val bundleFile = File(cacheDir, "$repoName.bundle")
                BundleManager().createBundle(repoDir, bundleFile)

                // 2. Get local IP
                val ip = RepoShareServer.getLocalIp()
                if (ip == null) {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "❌ No WiFi/hotspot detected.\nConnect to a network and retry."
                    }
                    return@launch
                }

                // 3. Start the server
                val srv = RepoShareServer(bundleFile, repoName, RepoShareServer.PORT)
                srv.start()
                server = srv

                // 4. Build the URL that the receiver will hit
                val url = "gitlane://$ip:${RepoShareServer.PORT}/$repoName"

                // 5. Generate QR bitmap
                val qrBitmap = generateQR(url, 600)

                withContext(Dispatchers.Main) {
                    tvStatus.text = "✅ Ready to share!\nMake sure both devices are on the same WiFi / hotspot."
                    tvUrl.text    = url
                    tvUrl.visibility = View.VISIBLE
                    ivQr.setImageBitmap(qrBitmap)
                    ivQr.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "❌ ${e.message}"
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()   // Always stop the server when leaving
    }

    // ── QR Generation ────────────────────────────────────────────────────────

    private fun generateQR(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
