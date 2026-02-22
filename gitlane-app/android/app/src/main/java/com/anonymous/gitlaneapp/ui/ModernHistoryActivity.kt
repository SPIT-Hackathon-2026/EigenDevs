package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.ui.components.CommitListItem
import com.anonymous.gitlaneapp.ui.components.GitGraphVisualizer
import com.anonymous.gitlaneapp.ui.viewmodel.HistoryViewModel
import java.io.File

/**
 * ModernHistoryActivity
 * 
 * Jetpack Compose implementation of the Pro Git History view.
 * Combines the visual curved graph with a scrollable commit list.
 */
@OptIn(ExperimentalMaterial3Api::class)
class ModernHistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repoPath = intent.getStringExtra("extra_repo_path") ?: finish().run { return }
        val gitManager = GitManager(this)
        val viewModel = HistoryViewModel(gitManager, File(repoPath))

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val sheetState = rememberModalBottomSheetState()
            var showBottomSheet by remember { mutableStateOf(false) }

            LaunchedEffect(uiState.selectedCommitFiles) {
                if (uiState.selectedCommitFiles.isNotEmpty()) {
                    showBottomSheet = true
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                surface = Color(0xFF1E293B),
                background = Color(0xFF0F172A)
            )) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    GitGraphVisualizer(
                        nodes = uiState.graphNodes,
                        onNodeSelected = { viewModel.selectCommit(it) }
                    )

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(uiState.graphNodes) { index, node ->
                            CommitListItem(
                                node = node,
                                onClick = { viewModel.selectCommit(node) }
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                    }
                }

                if (showBottomSheet) {
                    val selectedNode = uiState.selectedNode
                    ModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = sheetState,
                        containerColor = Color(0xFF1E293B)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            if (selectedNode != null) {
                                Text(selectedNode.commit.message, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text(
                                    "${selectedNode.commit.author} • ${selectedNode.commit.date}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                            
                            Text("Changed Files", style = MaterialTheme.typography.labelLarge, color = Color(0xFF38BDF8))
                            Spacer(Modifier.height(8.dp))
                            
                            LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                                items(uiState.selectedCommitFiles.size) { index ->
                                    val fileName = uiState.selectedCommitFiles[index]
                                    Surface(
                                        onClick = {
                                            val intent = android.content.Intent(this@ModernHistoryActivity, CommitDiffActivity::class.java).apply {
                                                putExtra(CommitDiffActivity.EXTRA_REPO_PATH, repoPath)
                                                putExtra(CommitDiffActivity.EXTRA_COMMIT_SHA, selectedNode?.commit?.sha)
                                                putExtra(CommitDiffActivity.EXTRA_FILE_PATH, fileName)
                                            }
                                            startActivity(intent)
                                        },
                                        color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Text("• $fileName", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Filled.ArrowForward, null, tint = Color.DarkGray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
