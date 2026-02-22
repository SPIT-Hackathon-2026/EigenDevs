package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.databinding.ActivityDashboardBinding
import com.anonymous.gitlaneapp.ui.components.RebaseBanner
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.eclipse.jgit.lib.RepositoryState
import kotlinx.coroutines.launch
import java.io.File

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val git by lazy { GitManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupRebaseBanner()

        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        checkRebaseState()
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home    -> HomeFragment()
                R.id.nav_search  -> SearchFragment()
                R.id.nav_chatbot -> ChatbotFragment()
                R.id.nav_profile -> ProfileFragment()
                else             -> HomeFragment()
            }
            loadFragment(fragment)
            true
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
                val targetRepo = rebasingRepo 
                binding.rebaseBannerView.visibility = View.VISIBLE
                binding.rebaseBannerView.setContent {
                    RebaseBanner(
                        onResume = {
                            val intent = Intent(this@DashboardActivity, RebaseActivity::class.java).apply {
                                putExtra(RebaseActivity.EXTRA_REPO_PATH, targetRepo.absolutePath)
                                putExtra(RebaseActivity.EXTRA_UPSTREAM, "") 
                            }
                            startActivity(intent)
                        },
                        onAbort = {
                            lifecycleScope.launch {
                                try {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        git.abortRebase(targetRepo)
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
