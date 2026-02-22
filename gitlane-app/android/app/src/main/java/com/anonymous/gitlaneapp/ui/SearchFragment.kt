package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import java.io.File

class SearchFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TransferScreen()
            }
        }
    }
}

// ── Colours matching GitHub dark theme ──────────────────────────────────────

private val BgPrimary    = Color(0xFF0D1117)
private val BgSurface    = Color(0xFF161B22)
private val BgCard       = Color(0xFF21262D)
private val TextPrimary  = Color(0xFFE6EDF3)
private val TextMuted    = Color(0xFF8B949E)
private val GreenAccent  = Color(0xFF238636)
private val BlueAccent   = Color(0xFF1F6FEB)
private val BorderColor  = Color(0xFF30363D)

// ── Main Screen ─────────────────────────────────────────────────────────────

@Composable
private fun TransferScreen() {
    val context = LocalContext.current
    var showRepoPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Text(
                text = "📡",
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "Transfer",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Share repos via QR code over Wi-Fi. No internet needed.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
            )

            // ── RECEIVE button ───────────────────────────────────────────────
            TransferButton(
                emoji = "📷",
                title = "Receive",
                subtitle = "Scan a QR code to download a repo",
                containerColor = BlueAccent.copy(alpha = 0.15f),
                borderColor = BlueAccent,
                labelColor = Color(0xFF58A6FF),
                onClick = {
                    context.startActivity(Intent(context, QRScanActivity::class.java))
                }
            )

            Spacer(Modifier.height(16.dp))

            // ── SEND button ──────────────────────────────────────────────────
            TransferButton(
                emoji = "📤",
                title = "Send",
                subtitle = "Show a QR code — let another device download your repo",
                containerColor = GreenAccent.copy(alpha = 0.15f),
                borderColor = GreenAccent,
                labelColor = Color(0xFF3FB950),
                onClick = { showRepoPicker = true }
            )

            Spacer(Modifier.height(40.dp))

            // ── Info card ────────────────────────────────────────────────────
            Surface(
                color = BgCard,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("ℹ️  How it works", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("• Both devices must be on the same Wi-Fi or hotspot.", color = TextMuted, fontSize = 12.sp)
                    Text("• Sender hosts a local HTTP server; receiver downloads the bundle.", color = TextMuted, fontSize = 12.sp)
                    Text("• Tap 'Edit IP' if auto-detection picks the wrong address.", color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        // ── Bottom sheet repo picker ─────────────────────────────────────────
        if (showRepoPicker) {
            RepoPickerSheet(
                onDismiss = { showRepoPicker = false },
                onRepoSelected = { repoName ->
                    showRepoPicker = false
                    context.startActivity(
                        Intent(context, QRShareActivity::class.java).apply {
                            putExtra("REPO_NAME", repoName)
                        }
                    )
                }
            )
        }
    }
}

// ── Reusable button card ─────────────────────────────────────────────────────

@Composable
private fun TransferButton(
    emoji: String,
    title: String,
    subtitle: String,
    containerColor: Color,
    borderColor: Color,
    labelColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 32.sp, modifier = Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = labelColor)
                Text(subtitle, fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(top = 3.dp))
            }
            Text("›", fontSize = 22.sp, color = labelColor)
        }
    }
}

// ── Repo Picker Bottom Sheet ─────────────────────────────────────────────────

@Composable
private fun RepoPickerSheet(onDismiss: () -> Unit, onRepoSelected: (String) -> Unit) {
    val context = LocalContext.current
    val repos: List<String> = remember {
        val root = File(context.filesDir, "GitLane")
        if (root.exists()) root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted()
            ?: emptyList()
        else emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = BgSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clickable(enabled = false) {}
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                // Handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(BorderColor, RoundedCornerShape(2.dp))
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                )
                Text(
                    "Select Repository",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
                Divider(color = BorderColor)
                if (repos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No local repositories found.", color = TextMuted)
                    }
                } else {
                    LazyColumn {
                        items(repos) { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRepoSelected(name) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📁", fontSize = 20.sp, modifier = Modifier.padding(end = 14.dp))
                                Text(
                                    name,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Divider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 20.dp))
                        }
                    }
                }
            }
        }
    }
}
