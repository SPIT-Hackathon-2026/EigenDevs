package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.databinding.ActivityCommitHistoryBinding
import com.anonymous.gitlaneapp.ui.adapter.CommitAdapter
import kotlinx.coroutines.launch
import java.io.File

/**
 * CommitHistoryActivity — shows the git log for a repository.
 */
class CommitHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
        const val EXTRA_REPO_NAME = "extra_repo_name"
    }

    private lateinit var binding: ActivityCommitHistoryBinding
    private lateinit var git: GitManager
    private lateinit var repoDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommitHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: "History"
        repoDir = File(repoPath)
        git = GitManager(this)

        supportActionBar?.title = "$repoName — History"

        val adapter = CommitAdapter()
        binding.rvCommits.layoutManager = LinearLayoutManager(this)
        binding.rvCommits.adapter = adapter

        lifecycleScope.launch {
            val commits = git.getLog(repoDir)
            adapter.submitList(commits)
            binding.tvCommitCount.text = if (commits.isEmpty()) "No commits yet" else "${commits.size} commit${if (commits.size != 1) "s" else ""}"
        }
    }
}
