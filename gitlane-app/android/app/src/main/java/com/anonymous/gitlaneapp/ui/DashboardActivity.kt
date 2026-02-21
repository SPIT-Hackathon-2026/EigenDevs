package com.anonymous.gitlaneapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    // Launcher for activities that might add/change repos (Clone, Scan)
    private val refreshLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadRepos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        git = GitManager(this)

        setupRecyclerView()
        setupCreateButton()
        setupCloneButton()
        setupSettingsButton()
        setupScanFab()
        loadRepos()
    }

    override fun onResume() {
        super.onResume()
        loadRepos()
    }

    private fun setupRecyclerView() {
        adapter = RepoAdapter(
            onClick = { repoDir ->
                val intent = Intent(this, RepoDetailActivity::class.java).apply {
                    putExtra(RepoDetailActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                    putExtra(RepoDetailActivity.EXTRA_REPO_NAME, repoDir.name)
                }
                startActivity(intent)
            },
            onMore = { repoDir, anchor ->
                showRepoMenu(repoDir, anchor)
            }
        )
        binding.rvRepos.layoutManager = LinearLayoutManager(this)
        binding.rvRepos.adapter = adapter
    }

    private fun showRepoMenu(repoDir: File, anchor: android.view.View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add("📝 Rename")
        popup.menu.add("📂 Duplicate")
        popup.menu.add("🗑️ Delete")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "📝 Rename"    -> showRenameRepoDialog(repoDir)
                "📂 Duplicate" -> showDuplicateRepoDialog(repoDir)
                "🗑️ Delete"    -> showDeleteRepoDialog(repoDir)
            }
            true
        }
        popup.show()
    }

    private fun showRenameRepoDialog(repoDir: File) {
        val input = android.widget.EditText(this).apply {
            setText(repoDir.name)
            selectAll()
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rename Repository")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == repoDir.name) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        git.renameRepo(repoDir, newName)
                        loadRepos()
                    } catch (e: Exception) {
                        Toast.makeText(this@DashboardActivity, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDuplicateRepoDialog(repoDir: File) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Duplicate Repository?")
            .setMessage("Create a full copy of '${repoDir.name}'?")
            .setPositiveButton("Duplicate") { _, _ ->
                lifecycleScope.launch {
                    try {
                        git.duplicateRepo(repoDir)
                        loadRepos()
                        Toast.makeText(this@DashboardActivity, "✅ Duduplicated successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@DashboardActivity, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteRepoDialog(repoDir: File) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Repository?")
            .setMessage("This will permanently delete '${repoDir.name}' and its entire history. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        git.deleteRepo(repoDir)
                        loadRepos()
                    } catch (e: Exception) {
                        Toast.makeText(this@DashboardActivity, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun setupCloneButton() {
        binding.btnGoToClone.setOnClickListener {
            refreshLauncher.launch(Intent(this, CloneActivity::class.java))
        }
    }

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupScanFab() {
        binding.fabScanQr.setOnClickListener {
            refreshLauncher.launch(Intent(this, QRScanActivity::class.java))
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
