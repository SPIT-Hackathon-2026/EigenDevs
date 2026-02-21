package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
class ModernHistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repoPath = intent.getStringExtra("extra_repo_path") ?: finish().run { return }
        val gitManager = GitManager(this)
        val viewModel = HistoryViewModel(gitManager, File(repoPath))

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            
            MaterialTheme(colorScheme = darkColorScheme(
                surface = Color(0xFF0D1117),
                background = Color(0xFF010409)
            )) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // Layer 1: The Visual Graph (Static beneath the list or part of scroll)
                    // For a professional feel, we overlay the list on top of the graphgutter
                    GitGraphVisualizer(
                        nodes = uiState.graphNodes,
                        onNodeSelected = { viewModel.selectCommit(it) }
                    )

                    // Layer 2: The Commit List
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
            }
        }
    }
}
