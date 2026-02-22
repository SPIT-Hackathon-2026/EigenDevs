package com.anonymous.gitlaneapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anonymous.gitlaneapp.CommitInfo
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.engine.CommitGraphEngine
import com.anonymous.gitlaneapp.engine.DiffEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class HistoryUiState(
    val commits: List<com.anonymous.gitlaneapp.CommitInfo> = emptyList(),
    val graphNodes: List<CommitGraphEngine.GraphNode> = emptyList(),
    val isLoading: Boolean = false,
    val selectedCommitFiles: List<String> = emptyList(),
    val selectedNode: CommitGraphEngine.GraphNode? = null,
    val error: String? = null
)

class HistoryViewModel(
    private val git: GitManager,
    private val repoDir: File
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    private val graphEngine = CommitGraphEngine()
    
    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val commits = git.getLog(repoDir)
                // In a real app, we'd fetch actual branch data from git.listBranches
                val layout = graphEngine.calculateLayout(commits, emptyMap())
                
                _uiState.value = _uiState.value.copy(
                    commits = commits,
                    graphNodes = layout.nodes,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun selectCommit(node: CommitGraphEngine.GraphNode) {
        _uiState.value = _uiState.value.copy(selectedNode = node)
        viewModelScope.launch {
            val files = git.getChangedFiles(repoDir, node.commit.sha)
            _uiState.value = _uiState.value.copy(selectedCommitFiles = files)
        }
    }
}
