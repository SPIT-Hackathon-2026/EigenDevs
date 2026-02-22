package com.anonymous.gitlaneapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.gitlaneapp.rebase.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebaseScreen(
    viewModel: RebaseViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val explanation by viewModel.rebaseExplanation.collectAsState()
    val isSafeWarning by viewModel.isSafeModeWarning.collectAsState()

    // When the rebase is aborted the state becomes Idle — navigate back automatically.
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is RebaseUiState.Idle) onBack()
    }

    if (explanation != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExplanation() },
            title = { Text("Optimization Details") },
            text = { Text(explanation ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissExplanation() }) { Text("Got it") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interactive Rebase", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is RebaseUiState.Planning) {
                        TextButton(onClick = { viewModel.applySuggestions() }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Auto-Optimize")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color(0xFF64FFDA)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            if (isSafeWarning && state is RebaseUiState.Planning) {
                Surface(
                    color = Color(0xFF332200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Safe Mode: This branch is tracked on a remote. Rebasing will require a force-push.",
                            color = Color(0xFFFFC107),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            when (val s = state) {
                is RebaseUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF64FFDA))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading commits…", color = Color.Gray)
                    }
                }
                is RebaseUiState.NoBranch -> {
                    NoBranchView(viewModel)
                }
                is RebaseUiState.RebasingInProgress -> {
                    // Git is already mid-rebase (no saved plan found). Let user continue or abort.
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⏸ Rebase Interrupted", color = Color(0xFFFFC107), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "A rebase is already in progress in this repository. " +
                                "You can either continue it (if conflicts are resolved) or abort it to restore the original state.",
                                color = Color.Gray, fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(28.dp))
                            Button(
                                onClick = { viewModel.continueRebase() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64FFDA))
                            ) {
                                Text("▶ Continue Rebase", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.abortRebase() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF5252))
                            ) {
                                Text("🗑 Abort Rebase", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
                is RebaseUiState.Planning -> {
                    RebasePlanningList(s.plan, s.upstream, viewModel)
                }
                is RebaseUiState.Executing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF64FFDA))
                        Spacer(Modifier.height(16.dp))
                        Text("Executing rebase...", color = Color.White, fontWeight = FontWeight.Medium)
                        Text("Applying commits to target branch", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                is RebaseUiState.Conflict -> {
                    RebaseConflictView(s.info, viewModel)
                }
                is RebaseUiState.Success -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("✅ Rebase Successful!", color = Color(0xFF64FFDA), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Repository state has been updated.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onBack, 
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64FFDA))
                        ) { Text("Back to Repository", color = Color.Black) }
                    }
                }
                is RebaseUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("❌ Error", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFF2A1010),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(s.message, color = Color(0xFFFF8A80), fontSize = 14.sp, modifier = Modifier.padding(16.dp), fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { onBack() }) { Text("Close") }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun NoBranchView(viewModel: RebaseViewModel) {
    var branchInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Select Upstream Branch",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the name of the branch you want to rebase onto (e.g. \"main\").",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = branchInput,
            onValueChange = { branchInput = it },
            label = { Text("Target branch") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF64FFDA),
                unfocusedBorderColor = Color.Gray
            )
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (branchInput.isNotBlank()) viewModel.setUpstreamAndLoad(branchInput.trim())
            },
            enabled = branchInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64FFDA))
        ) {
            Text("Load Commits", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RebasePlanningList(plan: List<RebaseStep>, upstream: String, viewModel: RebaseViewModel) {
    var localLoading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Rebasing onto: $upstream",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color(0xFF90CAF9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(plan) { step ->
                RebaseStepItem(step) { action ->
                    viewModel.updateAction(step.sha, action)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFF222222))
            }
        }

        Surface(
            tonalElevation = 8.dp, 
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { 
                    localLoading = true
                    viewModel.executeRebase() 
                },
                enabled = !localLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    disabledContainerColor = Color(0xFF1565C0)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                if (localLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Execute Rebase Plan (${plan.size} commits)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebaseStepItem(step: RebaseStep, onActionChange: (RebaseAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(step.sha.take(8), color = Color(0xFF64FFDA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(2.dp))
            Text(step.originalMessage, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }

        Box {
            Surface(
                onClick = { expanded = true },
                color = when(step.action) {
                    RebaseAction.PICK   -> Color(0xFF1B5E20)
                    RebaseAction.DROP   -> Color(0xFFB71C1C)
                    else                -> Color(0xFFE65100)
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    step.action.name, 
                    color = Color.White, 
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF2A2A2A))
            ) {
                RebaseAction.values().forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.name, color = Color.White) },
                        onClick = {
                            onActionChange(action)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RebaseConflictView(info: RebaseConflictInfo, viewModel: RebaseViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("🚨 Rebase Stopped: Conflicts", color = Color(0xFFFF5252), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Please resolve conflicts manually in these files, then return and click Continue.", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(Modifier.height(24.dp))
        Surface(color = Color(0xFF1E1E1E), shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.padding(16.dp)) {
                items(info.conflictedFiles) { file ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(file, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { viewModel.abortRebase() }, 
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
            ) { Text("Abort") }
            Button(
                onClick = { viewModel.continueRebase() }, 
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64FFDA))
            ) { Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}
