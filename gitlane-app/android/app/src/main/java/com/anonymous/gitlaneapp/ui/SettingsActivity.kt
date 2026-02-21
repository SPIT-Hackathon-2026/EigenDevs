package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.R

/**
 * Settings screen — manage Personal Access Tokens per hosting service.
 * Tokens are stored encrypted via CredentialsManager.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var creds: CredentialsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "Settings — Auth Tokens"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        creds = CredentialsManager(this)
        refreshTokenList()

        // Pre-fill buttons for the three main services
        listOf(
            R.id.btnAddGithub    to "github.com",
            R.id.btnAddGitlab    to "gitlab.com",
            R.id.btnAddBitbucket to "bitbucket.org"
        ).forEach { (btnId, host) ->
            findViewById<Button>(btnId).setOnClickListener {
                showAddTokenDialog(host)
            }
        }

        findViewById<Button>(R.id.btnAddCustom).setOnClickListener {
            showAddCustomHostDialog()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun refreshTokenList() {
        val container = findViewById<LinearLayout>(R.id.llTokenList)
        container.removeAllViews()

        val tokens = creds.listAll()
        if (tokens.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No tokens saved yet.\nAdd a token below to enable clone, push & pull."
                textSize = 13f
                setPadding(0, 8, 0, 8)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
            }
            container.addView(tv)
            return
        }

        tokens.forEach { (host, masked) ->
            val row = layoutInflater.inflate(R.layout.item_token_row, container, false)
            row.findViewById<TextView>(R.id.tvTokenHost).text =
                CredentialsManager.serviceLabel(host)
            row.findViewById<TextView>(R.id.tvTokenUrl).text = host
            row.findViewById<TextView>(R.id.tvTokenMasked).text = masked
            row.findViewById<Button>(R.id.btnDeleteToken).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Remove token for $host?")
                    .setMessage("You will need to re-add it to access private repos on $host.")
                    .setPositiveButton("Remove") { _, _ ->
                        creds.deletePat(host)
                        refreshTokenList()
                        Toast.makeText(this, "Token for $host removed", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            container.addView(row)
        }
    }

    private fun showAddTokenDialog(host: String) {
        val input = EditText(this).apply {
            hint = "Paste your PAT here"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Add token for ${CredentialsManager.serviceLabel(host)}")
            .setMessage("Generate a Personal Access Token on $host with 'repo' scope.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val token = input.text.toString().trim()
                if (token.isBlank()) {
                    Toast.makeText(this, "Token cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    creds.savePat(host, token)
                    refreshTokenList()
                    Toast.makeText(this, "✅ Token saved for $host", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCustomHostDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etHost = EditText(this).apply { hint = "Host (e.g. git.mycompany.com)" }
        val etToken = EditText(this).apply {
            hint = "Personal Access Token"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(etHost)
        container.addView(etToken)

        AlertDialog.Builder(this)
            .setTitle("Add Custom Server Token")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val host  = etHost.text.toString().trim()
                val token = etToken.text.toString().trim()
                if (host.isBlank() || token.isBlank()) {
                    Toast.makeText(this, "Host and token cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    creds.savePat(host, token)
                    refreshTokenList()
                    Toast.makeText(this, "✅ Token saved for $host", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
