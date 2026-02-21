package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.anonymous.gitlaneapp.GitHubApiManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.databinding.ActivityRepoSearchBinding
import kotlinx.coroutines.launch

/**
 * PublicRepoSearchActivity
 * 
 * Allows users to search for public GitHub repositories by full name (owner/repo).
 * Displays repository details and provides an option to clone locally.
 */
class PublicRepoSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepoSearchBinding
    private lateinit var api: GitHubApiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRepoSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Search GitHub"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // We use a blank token for general public search, 
        // but prefer the stored token if available to avoid rate limits
        val creds = com.anonymous.gitlaneapp.CredentialsManager(this)
        val token = creds.getPat("github.com") ?: ""
        api = GitHubApiManager(token)

        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupListeners() {
        binding.btnSearchRepo.setOnClickListener {
            val query = binding.etSearchRepo.text.toString().trim().removePrefix("https://github.com/").removeSuffix(".git")
            if (query.isEmpty() || !query.contains("/")) {
                binding.etSearchRepo.error = "Enter a valid owner/repo"
                return@setOnClickListener
            }
            performSearch(query)
        }
    }

    private fun performSearch(fullName: String) {
        binding.pbSearch.visibility = View.VISIBLE
        binding.cardResult.visibility = View.GONE
        binding.btnSearchRepo.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = api.getPublicRepo(fullName)
                displayResult(result)
            } catch (e: Exception) {
                Toast.makeText(this@PublicRepoSearchActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pbSearch.visibility = View.GONE
                binding.btnSearchRepo.isEnabled = true
            }
        }
    }

    private fun displayResult(repo: GitHubApiManager.RepoSearchInfo) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvRepoFullName.text = repo.fullName
        binding.tvRepoDesc.text = repo.description
        binding.tvStars.text = "⭐ ${repo.stars} stars"
        binding.ivOwnerAvatar.load(repo.avatarUrl)

        binding.btnCloneResult.setOnClickListener {
            val intent = Intent(this, CloneActivity::class.java).apply {
                putExtra("prefill_url", repo.cloneUrl)
                putExtra("prefill_name", repo.fullName.substringAfter("/"))
            }
            startActivity(intent)
            finish()
        }
    }
}
