package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anonymous.gitlaneapp.rebase.RebaseAssistant
import com.anonymous.gitlaneapp.rebase.RebaseRepository
import com.anonymous.gitlaneapp.rebase.RebaseViewModel
import java.io.File

/**
 * RebaseActivity — Hosting the Interactive Rebase UI.
 */
class RebaseActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
        const val EXTRA_UPSTREAM  = "extra_upstream"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val upstream = intent.getStringExtra(EXTRA_UPSTREAM) ?: "main"

        val repository = RebaseRepository(this)
        val assistant = RebaseAssistant()
        
        val viewModel = ViewModelProvider(this, RebaseViewModelFactory(repository, assistant))[RebaseViewModel::class.java]
        viewModel.init(File(repoPath), upstream)

        setContent {
            RebaseScreen(viewModel, onBack = { finish() })
        }
    }
}

class RebaseViewModelFactory(
    private val repository: RebaseRepository,
    private val assistant: RebaseAssistant
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RebaseViewModel(repository, assistant) as T
    }
}
