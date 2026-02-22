package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.GitHubApiManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.ui.viewmodel.InboxViewModel

class InvitationInboxActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gitManager = GitManager(this)
        val remoteGit = com.anonymous.gitlaneapp.RemoteGitManager(this)
        val credentialsManager = CredentialsManager(this)
        val viewModel = InboxViewModel(gitManager, remoteGit, credentialsManager)

        setContent {
            InboxScreen(viewModel) { finish() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: InboxViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let { 
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF64FFDA),
            surface = Color(0xFF161B22),
            background = Color(0xFF0D1117)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Collaboration Inbox", fontWeight = FontWeight.Black)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadInvitations() }) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF64FFDA))
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "Sync")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
                )
            },
            containerColor = Color(0xFF0D1117)
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (state.isLoggedOut) {
                    LoggedOutView {
                        val intent = android.content.Intent(context, GitHubOAuthActivity::class.java)
                        context.startActivity(intent)
                    }
                } else if (state.isLoading && state.invitations.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFF64FFDA))
                } else if (state.invitations.isEmpty()) {
                    EmptyInboxView()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.invitations) { invitation ->
                            InvitationCard(invitation) {
                                viewModel.acceptAndImport(invitation)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoggedOutView(onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF64FFDA).copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Connect your GitHub",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Log in to see your repository invitations and manage collaborations.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Login with GitHub", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun InvitationCard(invitation: GitHubApiManager.InvitationInfo, onAccept: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = invitation.inviter.take(1).uppercase(),
                    color = Color(0xFF8B5CF6),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = invitation.repoName,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "Invited by @${invitation.inviter}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("Accept", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmptyInboxView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Inbox,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF30363D)
        )
        Spacer(Modifier.height(16.dp))
        Text("No pending invitations", color = Color.Gray, fontWeight = FontWeight.Medium)
        Text("Requests from GitHub will appear here", fontSize = 12.sp, color = Color(0xFF30363D))
    }
}
