package com.anonymous.gitlaneapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                                border = BorderStroke(1.5.dp, Color(0xFFFF5252))
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

// ─── Rebase Planning ──────────────────────────────────────────────────────────

@Composable
fun RebasePlanningList(plan: List<RebaseStep>, upstream: String, viewModel: RebaseViewModel) {
    var localLoading by remember { mutableStateOf(false) }
    var rewordTarget by remember { mutableStateOf<RebaseStep?>(null) }

    if (rewordTarget != null) {
        RewordDialog(
            step = rewordTarget!!,
            onDismiss = { rewordTarget = null },
            onConfirm = { newMsg ->
                viewModel.updateRewordMessage(rewordTarget!!.sha, newMsg)
                rewordTarget = null
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = Color(0xFF1E1E2C),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "REBASING ONTO: $upstream",
                    color = Color(0xFF64FFDA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${plan.size} commits will be re-applied. You can reorder them or change actions.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        RebaseHelperHeader()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(plan) { index, step ->
                RebaseStepItem(
                    step = step,
                    index = index,
                    isFirst = index == 0,
                    isLast = index == plan.size - 1,
                    onActionChange = { viewModel.updateAction(step.sha, it) },
                    onReword = { rewordTarget = step },
                    onMoveUp = { viewModel.moveStep(index, index - 1) },
                    onMoveDown = { viewModel.moveStep(index, index + 1) }
                )
            }
        }

        Surface(
            tonalElevation = 12.dp,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.abortRebase() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        localLoading = true
                        viewModel.executeRebase()
                    },
                    enabled = !localLoading,
                    modifier = Modifier.weight(2f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (localLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Rebase", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RebaseHelperHeader() {
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        color = Color(0xFF252525),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Command Legend", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Color.Gray
                )
            }
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                RebaseAction.values().forEach { action ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            action.name.padEnd(8),
                            color = getActionColor(action),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(60.dp)
                        )
                        Text(action.helper, color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebaseStepItem(
    step: RebaseStep,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onActionChange: (RebaseAction) -> Unit,
    onReword: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, null, tint = if (isFirst) Color.DarkGray else Color.Gray)
                }
                Text("${index + 1}", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = if (isLast) Color.DarkGray else Color.Gray)
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            step.sha.take(7),
                            color = Color(0xFF64FFDA),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (step.newMessage != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Edit, null, tint = Color(0xFF64FFDA), modifier = Modifier.size(12.dp))
                        Text(" Modified", color = Color(0xFF64FFDA), fontSize = 10.sp)
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = step.newMessage ?: step.originalMessage,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (step.action == RebaseAction.SQUASH || step.action == RebaseAction.FIXUP) {
                    Text(
                        "Melding into previous commit ↑",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box {
                Surface(
                    onClick = { menuExpanded = true },
                    color = getActionColor(step.action).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, getActionColor(step.action).copy(alpha = 0.4f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            step.action.name,
                            color = getActionColor(step.action),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = getActionColor(step.action), modifier = Modifier.size(16.dp))
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color(0xFF333333))
                ) {
                    RebaseAction.values().forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(action.name, color = getActionColor(action), fontWeight = FontWeight.Bold)
                                    Text(action.description, color = Color.Gray, fontSize = 11.sp)
                                }
                            },
                            onClick = {
                                onActionChange(action)
                                menuExpanded = false
                                if (action == RebaseAction.REWORD) onReword()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RewordDialog(step: RebaseStep, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(step.newMessage ?: step.originalMessage) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252525),
        title = { Text("Edit Commit Message", color = Color.White) },
        text = {
            Column {
                Text("Original: ${step.originalMessage}", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF64FFDA)
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

private fun getActionColor(action: RebaseAction): Color = when (action) {
    RebaseAction.PICK   -> Color(0xFF4CAF50)
    RebaseAction.REWORD -> Color(0xFF2196F3)
    RebaseAction.EDIT   -> Color(0xFF00BCD4)
    RebaseAction.SQUASH -> Color(0xFFFF9800)
    RebaseAction.FIXUP  -> Color(0xFFFFC107)
    RebaseAction.DROP   -> Color(0xFFF44336)
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
