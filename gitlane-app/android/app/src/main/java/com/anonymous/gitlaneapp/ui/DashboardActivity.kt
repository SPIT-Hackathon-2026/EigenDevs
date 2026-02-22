package com.anonymous.gitlaneapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.databinding.ActivityDashboardBinding
import com.anonymous.gitlaneapp.ui.components.RebaseBanner
import com.anonymous.gitlaneapp.ui.adapter.RepoAdapter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.eclipse.jgit.lib.RepositoryState
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
        setupFetchGithubButton()
        setupSettingsButton()
        setupInboxButton()
        setupScanFab()
        setupCopilotFab()
        loadRepos()

        checkFirstRun()
        setupRebaseBanner()
    }

    override fun onResume() {
        super.onResume()
        loadRepos()
        checkRebaseState()
    }

    private fun setupCopilotFab() {
        binding.fabCopilot.setOnClickListener {
            startActivity(Intent(this, CopilotActivity::class.java))
        }
    }

    private fun setupRebaseBanner() {
        binding.rebaseBannerView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    private fun checkRebaseState() {
        lifecycleScope.launch {
            val repos = git.listRepos()
            var rebasingRepo: File? = null
            for (repo in repos) {
                if (git.getRepositoryState(repo) == RepositoryState.REBASING_INTERACTIVE) {
                    rebasingRepo = repo
                    break
                }
            }

            if (rebasingRepo != null) {
                val targetRepo = rebasingRepo // capture for lambdas
                binding.rebaseBannerView.visibility = View.VISIBLE
                binding.rebaseBannerView.setContent {
                    RebaseBanner(
                        onResume = {
                            val intent = Intent(this@DashboardActivity, RebaseActivity::class.java).apply {
                                putExtra(RebaseActivity.EXTRA_REPO_PATH, targetRepo.absolutePath)
                                putExtra(RebaseActivity.EXTRA_UPSTREAM, "") // Will load from persistence
                            }
                            startActivity(intent)
                        },
                        onAbort = {
                            lifecycleScope.launch {
                                try {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        git.abortRebase(targetRepo)
                                        // Delete GitLane persistence state file
                                        File(targetRepo, ".git/gitlane/rebase_state.json").delete()
                                    }
                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "✅ Rebase aborted for '${targetRepo.name}'",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "❌ Abort failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    binding.rebaseBannerView.visibility = View.GONE
                                }
                            }
                        }
                    )
                }
            } else {
                binding.rebaseBannerView.visibility = View.GONE
            }
        }
    }

    private fun setupInboxButton() {
        binding.btnInbox.setOnClickListener {
            val creds = com.anonymous.gitlaneapp.CredentialsManager(this)
            if (!creds.hasAnyToken()) {
                Toast.makeText(this, "Set a GitHub token in Settings to view invitations", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, SettingsActivity::class.java))
                return@setOnClickListener
            }
            startActivity(Intent(this, InvitationInboxActivity::class.java))
        }
    }

    private fun checkFirstRun() {
        val creds = com.anonymous.gitlaneapp.CredentialsManager(this)
        if (!creds.hasAnyToken()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Welcome to GitLane!")
                .setMessage("To clone private repositories and retrieve your GitHub projects, you'll need a Personal Access Token (PAT). Would you like to set one up now?")
                .setPositiveButton("Set PAT") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun setupFetchGithubButton() {
        binding.btnFetchGithub.setOnClickListener {
            val creds = com.anonymous.gitlaneapp.CredentialsManager(this)
            val token = creds.getPat("github.com")
            if (token == null) {
                Toast.makeText(this, "Please set a GitHub token in Settings first", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, SettingsActivity::class.java))
                return@setOnClickListener
            }
            // Start GitHub Repo listing activity
            val intent = Intent(this, GitHubRepoListActivity::class.java)
            refreshLauncher.launch(intent)
        }

        binding.btnSearchPublic.setOnClickListener {
            startActivity(Intent(this, PublicRepoSearchActivity::class.java))
        }
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
                        Toast.makeText(this@DashboardActivity, "✅ Duplicate successfully", Toast.LENGTH_SHORT).show()
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
