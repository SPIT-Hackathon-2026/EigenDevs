package com.anonymous.gitlaneapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.GitHubApiManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.RemoteGitManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class InboxUiState(
    val invitations: List<GitHubApiManager.InvitationInfo> = emptyList(),
    val isLoading: Boolean = false,
    val refreshing: Boolean = false,
    val isLoggedOut: Boolean = false,
    val userInfo: GitHubApiManager.UserInfo? = null,
    val error: String? = null,
    val successMessage: String? = null
)

class InboxViewModel(
    private val git: GitManager,
    private val remoteGit: RemoteGitManager,
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState

    init {
        loadInvitations()
    }

    fun loadInvitations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "Checking GitHub...")
            try {
                val token = credentialsManager.getPat("github.com")
                if (token == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoggedOut = true,
                        isLoading = false
                    )
                    return@launch
                }
                
                val api = GitHubApiManager(token)
                val list = api.listInvitations()
                val profile = try { api.getUserProfile() } catch (e: Exception) { null }

                _uiState.value = _uiState.value.copy(
                    invitations = list,
                    isLoading = false,
                    isLoggedOut = false,
                    userInfo = profile,
                    error = if (list.isEmpty()) "Checked GitHub: No pending invitations found." else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun acceptAndImport(invitation: GitHubApiManager.InvitationInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "Accepting invitation on GitHub...")
            try {
                val token = credentialsManager.getPat("github.com") ?: throw Exception("Token missing")
                val api = GitHubApiManager(token)
                
                // 1. Accept on GitHub
                val accepted = api.acceptInvitation(invitation.id)
                if (!accepted) throw Exception("Failed to accept invitation on GitHub")
                
                // 2. Add to GitLane (Import locally)
                _uiState.value = _uiState.value.copy(error = "Importing repo to GitLane...")
                remoteGit.cloneRepo(
                    remoteUrl = invitation.cloneUrl,
                    repoName = invitation.repoName.substringAfterLast('/'),
                    pat = token
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Successfully accepted and imported ${invitation.repoName}!",
                    invitations = _uiState.value.invitations.filter { it.id != invitation.id },
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error during import: ${e.message}", isLoading = false)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
