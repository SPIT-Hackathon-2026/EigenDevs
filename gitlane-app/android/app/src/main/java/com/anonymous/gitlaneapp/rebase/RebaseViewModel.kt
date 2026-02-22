package com.anonymous.gitlaneapp.rebase

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

/**
 * State representation for the Rebase UI.
 */
sealed class RebaseUiState {
    object Idle : RebaseUiState()
    object Loading : RebaseUiState()
    object NoBranch : RebaseUiState()
    data class Planning(val plan: List<RebaseStep>, val upstream: String) : RebaseUiState()
    object Executing : RebaseUiState()
    /** Git is already mid-rebase (REBASING_INTERACTIVE) but we have no saved plan. Show continue/abort only. */
    object RebasingInProgress : RebaseUiState()
    data class Conflict(val info: RebaseConflictInfo) : RebaseUiState()
    object Success : RebaseUiState()
    data class Error(val message: String) : RebaseUiState()
}

class RebaseViewModel(
    private val repository: RebaseRepository,
    private val assistant: RebaseAssistant
) : ViewModel() {

    private val _uiState = MutableStateFlow<RebaseUiState>(RebaseUiState.Idle)
    val uiState: StateFlow<RebaseUiState> = _uiState

    private val _rebaseExplanation = MutableStateFlow<String?>(null)
    val rebaseExplanation: StateFlow<String?> = _rebaseExplanation

    private val _isSafeModeWarning = MutableStateFlow(false)
    val isSafeModeWarning: StateFlow<Boolean> = _isSafeModeWarning

    private var currentRepoDir: File? = null
    private var currentUpstream: String? = null

    fun init(repoDir: File, upstream: String) {
        Log.d("RebaseViewModel", "Init: repo=$repoDir, upstream=$upstream")
        currentRepoDir = repoDir
        currentUpstream = upstream.ifBlank { null }

        val savedState = repository.loadState(repoDir)
        if (savedState != null) {
            Log.d("RebaseViewModel", "Restoring saved state for upstream ${savedState.upstream}")
            // Always restore currentUpstream from persisted state so executeRebase() can find it
            // even if the Activity was recreated and the incoming `upstream` intent extra was blank.
            currentUpstream = savedState.upstream.ifBlank { currentUpstream }
            _uiState.value = RebaseUiState.Planning(savedState.plan, savedState.upstream)
            return
        }

        // No persisted plan — check if git itself is already mid-rebase.
        // This happens when the user exits and re-enters the RebaseActivity without a saved state.
        // Attempting to start a new rebase in this state throws "Wrong Repository State".
        viewModelScope.launch {
            val repoState = withContext(Dispatchers.IO) {
                try {
                    FileRepositoryBuilder()
                        .setGitDir(java.io.File(repoDir, ".git"))
                        .build()
                        .use { it.repositoryState }
                } catch (e: Exception) { RepositoryState.SAFE }
            }

            if (repoState == RepositoryState.REBASING_INTERACTIVE) {
                Log.d("RebaseViewModel", "Repo is already REBASING_INTERACTIVE — entering resume mode")
                _uiState.value = RebaseUiState.RebasingInProgress
                return@launch
            }

            if (currentUpstream.isNullOrBlank()) {
                _uiState.value = RebaseUiState.NoBranch
                return@launch
            }

            loadCommits()
            checkSafety(repoDir, currentUpstream!!)
        }
    }

    fun setUpstreamAndLoad(upstream: String) {
        val repoDir = currentRepoDir ?: return
        currentUpstream = upstream
        loadCommits()
        checkSafety(repoDir, upstream)
    }

    private fun checkSafety(repoDir: File, upstream: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val repo = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                        .setGitDir(File(repoDir, ".git"))
                        .build()
                    val config = repo.config
                    val branch = repo.branch
                    val remote = config.getString("branch", branch, "remote")
                    if (remote != null) {
                        _isSafeModeWarning.value = true
                    }
                    repo.close()
                } catch (e: Exception) { 
                    Log.e("RebaseViewModel", "Safety check failed", e)
                }
            }
        }
    }

    private fun loadCommits() {
        val repoDir = currentRepoDir ?: return
        val upstream = currentUpstream ?: return

        viewModelScope.launch {
            _uiState.value = RebaseUiState.Loading
            try {
                val commits = repository.getCommitsForRebase(repoDir, upstream)
                if (commits.isEmpty()) {
                    _uiState.value = RebaseUiState.Error(
                        "No commits found between current branch and '$upstream'.\n" +
                        "Make sure you are rebasing onto a branch that is behind the current one."
                    )
                } else {
                    _uiState.value = RebaseUiState.Planning(commits, upstream)
                }
            } catch (e: Exception) {
                Log.e("RebaseViewModel", "Load commits failed", e)
                _uiState.value = RebaseUiState.Error(e.message ?: "Failed to load commits")
            }
        }
    }

    fun updateAction(sha: String, action: RebaseAction) {
        val currentState = _uiState.value
        if (currentState is RebaseUiState.Planning) {
            val newPlan = currentState.plan.map {
                if (it.sha == sha) it.copy(action = action) else it
            }
            _uiState.value = RebaseUiState.Planning(newPlan, currentState.upstream)
        }
    }

    fun applySuggestions() {
        val state = _uiState.value
        if (state is RebaseUiState.Planning) {
            val optimized = assistant.suggestOptimizations(state.plan)
            _uiState.value = RebaseUiState.Planning(optimized, state.upstream)
            _rebaseExplanation.value = assistant.explainSuggestions(optimized)
        }
    }

    fun dismissExplanation() {
        _rebaseExplanation.value = null
    }

    fun executeRebase() {
        val repoDir = currentRepoDir ?: run {
            _uiState.value = RebaseUiState.Error("Internal Error: Repository path is missing")
            return
        }
        val currentState = _uiState.value
        if (currentState !is RebaseUiState.Planning) {
            Log.w("RebaseViewModel", "executeRebase called but state is not Planning")
            return
        }
        // Prefer currentUpstream field; fall back to the upstream carried in the Planning state
        // in case the ViewModel was recreated by Android and the field was reset to null.
        val upstream = currentUpstream
            ?: currentState.upstream.ifBlank { null }
            ?: run {
                _uiState.value = RebaseUiState.Error("Cannot start rebase: upstream branch is unknown. Please go back and try again.")
                return
            }
        // Keep field in sync
        currentUpstream = upstream

        Log.d("RebaseViewModel", "Executing rebase on $repoDir onto $upstream")
        viewModelScope.launch {
            _uiState.value = RebaseUiState.Executing
            try {
                val result = repository.executeRebase(repoDir, upstream, currentState.plan)
                handleResult(result)
            } catch (e: Exception) {
                Log.e("RebaseViewModel", "Rebase execution failed", e)
                _uiState.value = RebaseUiState.Error("Execution Crash: ${e.message}")
            }
        }
    }

    fun continueRebase() {
        val repoDir = currentRepoDir ?: return
        viewModelScope.launch {
            _uiState.value = RebaseUiState.Executing
            try {
                val result = repository.continueRebase(repoDir)
                handleResult(result)
            } catch (e: Exception) {
                _uiState.value = RebaseUiState.Error(e.message ?: "Error continuing")
            }
        }
    }

    fun abortRebase() {
        val repoDir = currentRepoDir ?: return
        viewModelScope.launch {
            _uiState.value = RebaseUiState.Executing
            try {
                repository.abortRebase(repoDir)
                _uiState.value = RebaseUiState.Idle
            } catch (e: Exception) {
                _uiState.value = RebaseUiState.Error("Abort failed: ${e.message}")
            }
        }
    }

    private fun handleResult(result: RebaseResult) {
        Log.d("RebaseViewModel", "Rebase result: $result")
        when (result) {
            is RebaseResult.Success  -> _uiState.value = RebaseUiState.Success
            is RebaseResult.Conflict -> _uiState.value = RebaseUiState.Conflict(result.info)
            is RebaseResult.Error    -> _uiState.value = RebaseUiState.Error(result.message)
            is RebaseResult.Aborted  -> _uiState.value = RebaseUiState.Idle
        }
    }
}
