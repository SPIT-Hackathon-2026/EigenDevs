package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.databinding.ActivityRepoDetailBinding
import com.anonymous.gitlaneapp.ui.adapter.FileAdapter
import kotlinx.coroutines.launch
import java.io.File

/**
 * RepoDetailActivity
 *
 * Shows repo info (branch, commit count), lists files, lets user create files,
 * stage all, and commit.
 */
class RepoDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
        const val EXTRA_REPO_NAME = "extra_repo_name"
    }

    private lateinit var binding: ActivityRepoDetailBinding
    private lateinit var git: GitManager
    private lateinit var repoDir: File
    private lateinit var fileAdapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRepoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: "Repository"
        repoDir = File(repoPath)

        git = GitManager(this)
        supportActionBar?.title = repoName

        setupRecyclerView()
        setupButtons()
        loadRepoInfo()
    }

    override fun onResume() {
        super.onResume()
        loadRepoInfo()
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter()
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = fileAdapter
    }

    private fun setupButtons() {
        // Create File
        binding.btnCreateFile.setOnClickListener {
            val name = binding.etFileName.text.toString().trim()
            val content = binding.etFileContent.text.toString()
            if (name.isEmpty()) {
                binding.etFileName.error = "Enter a file name"
                return@setOnClickListener
            }
            createFile(name, content)
        }

        // Stage & Commit
        binding.btnCommit.setOnClickListener {
            val msg = binding.etCommitMsg.text.toString().trim()
            if (msg.isEmpty()) {
                binding.etCommitMsg.error = "Enter a commit message"
                return@setOnClickListener
            }
            stageAndCommit(msg)
        }

        // View History
        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, CommitHistoryActivity::class.java).apply {
                putExtra(CommitHistoryActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                putExtra(CommitHistoryActivity.EXTRA_REPO_NAME, repoDir.name)
            }
            startActivity(intent)
        }

        // Share via QR
        binding.btnShareQr.setOnClickListener {
            val intent = Intent(this, QRShareActivity::class.java).apply {
                putExtra("REPO_NAME", repoDir.name)
            }
            startActivity(intent)
        }

        // Branches
        binding.btnBranches.setOnClickListener {
            val intent = Intent(this, BranchActivity::class.java).apply {
                putExtra(BranchActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                putExtra(BranchActivity.EXTRA_REPO_NAME, repoDir.name)
            }
            startActivity(intent)
        }

        // Remotes
        binding.btnRemotes.setOnClickListener {
            val intent = Intent(this, RemoteActivity::class.java).apply {
                putExtra(RemoteActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                putExtra(RemoteActivity.EXTRA_REPO_NAME, repoDir.name)
            }
            startActivity(intent)
        }
    }

    private fun loadRepoInfo() {
        lifecycleScope.launch {
            val branch = git.currentBranch(repoDir)
            val count  = git.commitCount(repoDir)
            val files  = git.listFiles(repoDir)

            binding.tvBranch.text = "🌿 $branch"
            binding.tvCommitCount.text = "$count commit${if (count != 1) "s" else ""}"
            binding.btnHistory.text = "View History ($count)"
            fileAdapter.submitList(files)
        }
    }

    private fun createFile(name: String, content: String) {
        binding.btnCreateFile.isEnabled = false
        lifecycleScope.launch {
            try {
                git.writeFile(repoDir, name, content)
                binding.etFileName.setText("")
                binding.etFileContent.setText("")
                Toast.makeText(this@RepoDetailActivity, "✅ File \"$name\" created", Toast.LENGTH_SHORT).show()
                loadRepoInfo()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnCreateFile.isEnabled = true
            }
        }
    }

    private fun stageAndCommit(message: String) {
        binding.btnCommit.isEnabled = false
        lifecycleScope.launch {
            try {
                git.stageAll(repoDir)
                val sha = git.commit(repoDir, message)
                binding.etCommitMsg.setText("")
                Toast.makeText(
                    this@RepoDetailActivity,
                    "✅ Committed! SHA: $sha",
                    Toast.LENGTH_SHORT
                ).show()
                loadRepoInfo()
            } catch (e: Exception) {
                Toast.makeText(
                    this@RepoDetailActivity,
                    "❌ Commit failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnCommit.isEnabled = true
            }
        }
    }
}
