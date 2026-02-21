package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.RemoteGitManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CloneActivity — Clone a remote Git repository.
 *
 * Supports:
 *  - Public repos (no token needed)
 *  - Private repos via saved PAT or manually entered token
 *  - GitHub, GitLab, Bitbucket, any HTTPS server
 */
class CloneActivity : AppCompatActivity() {

    private lateinit var creds: CredentialsManager
    private lateinit var remoteGit: RemoteGitManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clone)
        supportActionBar?.title = "Clone Repository"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        creds     = CredentialsManager(this)
        remoteGit = RemoteGitManager(this)

        val etUrl       = findViewById<EditText>(R.id.etCloneUrl)
        val etName      = findViewById<EditText>(R.id.etCloneName)
        val etPat       = findViewById<EditText>(R.id.etClonePat)
        val tvStatus    = findViewById<TextView>(R.id.tvCloneStatus)
        val progressBar = findViewById<ProgressBar>(R.id.cloneProgress)
        val btnClone    = findViewById<Button>(R.id.btnClone)

        // Auto-detect saved PAT when URL changes
        etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val url = etUrl.text.toString().trim()
                val savedPat = creds.getPatForUrl(url)
                if (!savedPat.isNullOrBlank() && etPat.text.isBlank()) {
                    etPat.setText(savedPat)
                    tvStatus.text = "✅ Saved token loaded for ${CredentialsManager.extractHost(url)}"
                }
            }
        }

        btnClone.setOnClickListener {
            val url  = etUrl.text.toString().trim()
            val name = etName.text.toString().trim().ifBlank { null }
            val pat  = etPat.text.toString().trim().ifBlank { null }
                ?: creds.getPatForUrl(url)

            if (url.isBlank()) {
                etUrl.error = "Enter a repository URL"
                return@setOnClickListener
            }
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                etUrl.error = "Only HTTPS URLs supported (e.g. https://github.com/…)"
                return@setOnClickListener
            }

            btnClone.isEnabled = false
            progressBar.visibility = View.VISIBLE
            tvStatus.text = "⏳ Cloning from $url…"

            lifecycleScope.launch {
                try {
                    val repoDir = remoteGit.cloneRepo(
                        remoteUrl = url,
                        repoName  = name,
                        pat       = pat
                    ) { task, _ ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            tvStatus.text = "⏳ $task"
                        }
                    }

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "✅ Cloned to '${repoDir.name}' successfully!"
                        Toast.makeText(
                            this@CloneActivity,
                            "Cloned: ${repoDir.name}",
                            Toast.LENGTH_LONG
                        ).show()
                        // Return to Dashboard with refresh signal
                        setResult(RESULT_OK)
                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        btnClone.isEnabled = true
                        tvStatus.text = "❌ Clone failed:\n${e.message}"
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
