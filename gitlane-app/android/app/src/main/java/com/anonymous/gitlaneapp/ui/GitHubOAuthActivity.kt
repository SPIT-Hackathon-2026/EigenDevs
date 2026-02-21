package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.anonymous.gitlaneapp.CredentialsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubOAuthActivity : ComponentActivity() {

    // IMPORTANT: You must register your own OAuth App on GitHub to get a valid Client ID.
    // Go to: Settings -> Developer Settings -> OAuth Apps -> New OAuth App
    // Homepage URL: https://github.com/SPIT-Hackathon-2026/EigenDevs
    // Authorization Callback URL: gitlane://oauth
    companion object {
        const val CLIENT_ID = "Ov23ligYVhZzA19pbwFA"
        const val CLIENT_SECRET = "67b951b20abb06abb4955a586f085ccf4cacd2d0"
        const val REDIRECT_URI = "gitlane://oauth"
        const val AUTH_URL = "https://github.com/login/oauth/authorize"
        const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val SCOPES = "repo,workflow,user,notifications"
    }

    private lateinit var credentialsManager: CredentialsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialsManager = CredentialsManager(this)

        // Check if we are being opened via the redirect URI
        val data: Uri? = intent.data
        if (data != null && data.toString().contains("oauth")) {
            val code = data.getQueryParameter("code")
            if (code != null) {
                exchangeCodeForToken(code)
            } else {
                Toast.makeText(this, "OAuth failed: No code received", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        setContent {
            OAuthScreen { launchBrowser() }
        }
    }

    private fun launchBrowser() {
        val url = "$AUTH_URL?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&scope=$SCOPES&state=${System.currentTimeMillis()}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun exchangeCodeForToken(code: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(TOKEN_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                // NOTE: In a production app, the CLIENT_SECRET should NOT be stored in the app.
                // It should be handled by a backend proxy. For this MVP/Hackathon, we assume
                // the user might use a Client Secret if they have one, or we use a flow that doesn't strictly require it (like PKCE if GitHub supported it natively for mobile easily, or a backend).
                // For now, we'll use a placeholder secret or ask for it.
                val postData = "client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&code=$code&redirect_uri=$REDIRECT_URI"
                
                conn.outputStream.write(postData.toByteArray())

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val token = json.optString("access_token")

                withContext(Dispatchers.Main) {
                    if (token.isNotEmpty()) {
                        credentialsManager.savePat("github.com", token)
                        Toast.makeText(this@GitHubOAuthActivity, "Successfully logged in with GitHub!", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        val error = json.optString("error_description", "Unknown error")
                        Toast.makeText(this@GitHubOAuthActivity, "OAuth Error: $error", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GitHubOAuthActivity, "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}

@Composable
fun OAuthScreen(onLogin: () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF64FFDA),
            surface = Color(0xFF161B22),
            background = Color(0xFF0D1117)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GitHub Authentication",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Securely connect your GitHub account to GitLane",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text("Login with GitHub", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
