package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anonymous.gitlaneapp.data.SettingsRepository

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(this)

        setContent {
            SettingsScreen(
                settings = settings,
                onBack = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apiKey by remember { mutableStateOf(settings.getGroqApiKey() ?: "") }
    var selectedModel by remember { mutableStateOf(settings.getGroqModel()) }
    val models = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "mixtral-8x7b-32768",
        "gemma2-9b-it"
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF3B82F6),
            surface = Color(0xFF1E293B),
            background = Color(0xFF0F172A)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        settings.saveGroqApiKey(apiKey)
                        settings.saveGroqModel(selectedModel)
                        onBack()
                    },
                    containerColor = Color(0xFF22C55E),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Settings")
                }
            },
            containerColor = Color(0xFF0F172A)
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    "AI Merge Assistant (Groq)",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Groq API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("gsk_...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Text("Select Model", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                
                models.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        RadioButton(
                            selected = (model == selectedModel),
                            onClick = { selectedModel = model }
                        )
                        Text(
                            text = model,
                            modifier = Modifier.padding(start = 8.dp).padding(vertical = 12.dp),
                            color = Color.White
                        )
                    }
                }

                Divider(color = Color(0xFF475569))

                Text(
                    "Accounts",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                Button(
                    onClick = {
                        val intent = android.content.Intent(context, GitHubOAuthActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_lock_power_off), // Placeholder icon
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Connect to GitHub (OAuth)", fontWeight = FontWeight.Bold)
                }

                Text(
                    "Your tokens and API keys are stored securely using hardware-backed encryption (EncryptedSharedPreferences).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
