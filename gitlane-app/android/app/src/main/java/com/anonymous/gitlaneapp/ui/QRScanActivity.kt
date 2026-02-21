package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anonymous.gitlaneapp.BundleManager
import com.anonymous.gitlaneapp.R
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Opened when the user taps the 📷 scan button on Dashboard.
 *
 * Flow:
 * 1. Opens ZXing camera scanner immediately
 * 2. Parses the scanned gitlane:// URL
 * 3. Downloads the .bundle file from the sender's HTTP server
 * 4. Imports it using JGit (BundleManager.importBundle)
 * 5. Finishes → Dashboard refreshes and shows the new repo
 */
class QRScanActivity : AppCompatActivity() {

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            handleScannedUrl(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        supportActionBar?.title = "Scan Repo QR"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Launch scanner immediately on open
        launchScanner()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setPrompt("Point camera at the GitLane share QR code")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
        barcodeLauncher.launch(options)
    }

    /**
     * Called after a successful QR scan.
     * Expected format: gitlane://192.168.x.x:8088/repoName
     */
    private fun handleScannedUrl(raw: String) {
        val tvStatus = findViewById<TextView>(R.id.tvScanStatus)
        tvStatus.text = "📡 Scanned: $raw\n\n⏳ Downloading repo…"

        if (!raw.startsWith("gitlane://")) {
            tvStatus.text = "❌ Not a GitLane QR code.\nScanned: $raw"
            return
        }

        // Parse: gitlane://192.168.1.5:8088/myrepo
        val stripped   = raw.removePrefix("gitlane://")       // 192.168.1.5:8088/myrepo
        val slashIdx   = stripped.indexOf('/')
        val hostPort   = stripped.substring(0, slashIdx)      // 192.168.1.5:8088
        val repoName   = stripped.substring(slashIdx + 1)     // myrepo
        val bundleUrl  = "http://$hostPort/$repoName.bundle"

        lifecycleScope.launch {
            try {
                // 1. Download the bundle
                val bundleFile = File(cacheDir, "$repoName.bundle")
                withContext(Dispatchers.IO) {
                    val connection = URL(bundleUrl).openConnection()
                    connection.connectTimeout = 10000 // 10 seconds
                    connection.readTimeout = 10000    // 10 seconds
                    
                    connection.getInputStream().use { input ->
                        FileOutputStream(bundleFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    tvStatus.text = "✅ Bundle downloaded!\n⏳ Importing repository…"
                }

                // 2. Check if repo already exists; rename if needed
                val gitLaneDir = File(filesDir, "GitLane")
                var targetDir  = File(gitLaneDir, repoName)
                var attempt    = 1
                while (targetDir.exists()) {
                    targetDir = File(gitLaneDir, "${repoName}_$attempt")
                    attempt++
                }

                // 3. Import (JGit clone from bundle)
                val branch = BundleManager().importBundle(bundleFile, targetDir)
                bundleFile.delete()     // clean up temp file

                withContext(Dispatchers.Main) {
                    tvStatus.text = "🎉 Repo '${targetDir.name}' imported!\nBranch: $branch"
                    Toast.makeText(
                        this@QRScanActivity,
                        "Repo imported: ${targetDir.name}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Signal Dashboard to refresh
                    setResult(RESULT_OK, Intent().putExtra("IMPORTED_REPO", targetDir.name))
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val detailedError = "${e.javaClass.simpleName}: ${e.message ?: "No detail"}"
                    tvStatus.text = "❌ Import failed:\n$detailedError\n\nVerify: IP address, Port forwarding, and Private Network profile."
                }
            }
        }
    }
}
