package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.databinding.ActivityDashboardBinding
<<<<<<< HEAD
=======
import com.anonymous.gitlaneapp.ui.components.RebaseBanner
import com.anonymous.gitlaneapp.ui.adapter.RepoAdapter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.eclipse.jgit.lib.RepositoryState
import kotlinx.coroutines.launch
import java.io.File
>>>>>>> 94b59930ce74a586ccfcc14c7822cf331c59c8dc

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

<<<<<<< HEAD
        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home     -> HomeFragment()
                R.id.nav_search   -> SearchFragment()
                R.id.nav_chatbot  -> ChatbotFragment()
                R.id.nav_profile  -> ProfileFragment()
                else              -> HomeFragment()
=======
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
>>>>>>> 94b59930ce74a586ccfcc14c7822cf331c59c8dc
            }
            loadFragment(fragment)
            true
        }
    }

    fun switchToTab(tabIndex: Int) {
        val itemId = when (tabIndex) {
            0    -> R.id.nav_home
            1    -> R.id.nav_search
            2    -> R.id.nav_chatbot
            3    -> R.id.nav_profile
            else -> R.id.nav_home
        }
        binding.bottomNavigation.selectedItemId = itemId
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
