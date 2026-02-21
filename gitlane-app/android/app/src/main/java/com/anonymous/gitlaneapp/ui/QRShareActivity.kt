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

                // 2. Get initial IP
                var currentIp = RepoShareServer.getLocalIp() ?: "127.0.0.1"

                // 3. Start the server
                val srv = RepoShareServer(bundleFile, repoName, RepoShareServer.PORT)
                srv.start()
                server = srv

                // UI setup function
                fun updateUI(ip: String) {
                    val url = "gitlane://$ip:${RepoShareServer.PORT}/$repoName"
                    val qrBitmap = generateQR(url, 600)
                    
                    tvStatus.text = "✅ Ready to share!\nMake sure both devices are on the same WiFi / hotspot."
                    tvUrl.text    = url
                    tvUrl.visibility = View.VISIBLE
                    ivQr.setImageBitmap(qrBitmap)
                    ivQr.visibility = View.VISIBLE

                    // Show emulator specific stuff
                    val isEmul = isEmulator()
                    findViewById<View>(R.id.btnEditIp).visibility = View.VISIBLE
                    if (isEmul) {
                        findViewById<View>(R.id.tvEmulatorHint).visibility = View.VISIBLE
                        if (ip.startsWith("10.0.2.")) {
                            tvStatus.text = "⚠️ Emulator detected!\n'10.0.2.x' is only visible to your PC. Tap 'Edit IP' and enter your PC's WiFi IP so your phone can scan it."
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    updateUI(currentIp)
                    
                    findViewById<View>(R.id.btnEditIp).setOnClickListener {
                        val input = android.widget.EditText(this@QRShareActivity).apply {
                            setText(currentIp)
                            setHint("Enter IP (e.g. 192.168.1.10)")
                        }
                        android.app.AlertDialog.Builder(this@QRShareActivity)
                            .setTitle("Set Sender IP")
                            .setMessage("Enter the IP address that the receiver should use to connect to this device.")
                            .setView(input)
                            .setPositiveButton("Apply") { _, _ ->
                                currentIp = input.text.toString().trim()
                                updateUI(currentIp)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "❌ ${e.message}"
                }
            }
        }
    }

    private fun isEmulator(): Boolean {
        val build = android.os.Build.FINGERPRINT
        return build.contains("generic") || build.contains("unknown") || 
               android.os.Build.MODEL.contains("google_sdk") || 
               android.os.Build.MODEL.contains("Emulator") || 
               android.os.Build.MODEL.contains("Android SDK built for x86")
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
