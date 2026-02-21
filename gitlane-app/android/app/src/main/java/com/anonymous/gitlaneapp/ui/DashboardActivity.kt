package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.databinding.ActivityDashboardBinding
import com.anonymous.gitlaneapp.ui.adapter.RepoAdapter
import kotlinx.coroutines.launch
import java.io.File

/**
 * DashboardActivity
 *
 * Entry screen. Lists all local git repositories and allows creating new ones.
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var git: GitManager
    private lateinit var adapter: RepoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        git = GitManager(this)

        setupRecyclerView()
        setupCreateButton()
        loadRepos()
    }

    override fun onResume() {
        super.onResume()
        loadRepos()
    }

    private fun setupRecyclerView() {
        adapter = RepoAdapter { repoDir ->
            val intent = Intent(this, RepoDetailActivity::class.java).apply {
                putExtra(RepoDetailActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                putExtra(RepoDetailActivity.EXTRA_REPO_NAME, repoDir.name)
            }
            startActivity(intent)
        }
        binding.rvRepos.layoutManager = LinearLayoutManager(this)
        binding.rvRepos.adapter = adapter
    }

    private fun setupCreateButton() {
        binding.btnCreateRepo.setOnClickListener {
            val name = binding.etRepoName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etRepoName.error = "Enter a repository name"
                return@setOnClickListener
            }
            if (!name.matches(Regex("[a-zA-Z0-9_\\-.]+"))) {
                binding.etRepoName.error = "Use only letters, numbers, - _ ."
                return@setOnClickListener
            }
            createRepo(name)
        }
    }

    private fun createRepo(name: String) {
        binding.btnCreateRepo.isEnabled = false
        lifecycleScope.launch {
            try {
                git.initRepo(name)
                binding.etRepoName.setText("")
                Toast.makeText(
                    this@DashboardActivity,
                    "✅ Repository \"$name\" created on branch main",
                    Toast.LENGTH_SHORT
                ).show()
                loadRepos()
            } catch (e: Exception) {
                Toast.makeText(
                    this@DashboardActivity,
                    "❌ Failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnCreateRepo.isEnabled = true
            }
        }
    }

    private fun loadRepos() {
        lifecycleScope.launch {
            val repos: List<File> = git.listRepos()
            adapter.submitList(repos)
            binding.tvRepoCount.text = "Repositories (${repos.size})"
        }
    }
}
