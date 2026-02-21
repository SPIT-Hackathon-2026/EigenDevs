package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.RemoteGitManager
import com.anonymous.gitlaneapp.databinding.ActivityGithubRepoListBinding
import com.anonymous.gitlaneapp.ui.adapter.GithubRepoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class GitHubRepoListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGithubRepoListBinding
    private lateinit var adapter: GithubRepoAdapter
    private lateinit var token: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGithubRepoListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Your GitHub Repos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val creds = CredentialsManager(this)
        token = creds.getPat("github.com") ?: run {
            Toast.makeText(this, "GitHub token missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        fetchRepos()
    }

    private fun setupRecyclerView() {
        adapter = GithubRepoAdapter { repo ->
            cloneRepo(repo)
        }
        binding.rvGithubRepos.layoutManager = LinearLayoutManager(this)
        binding.rvGithubRepos.adapter = adapter
    }

    private fun fetchRepos() {
        binding.pbLoader.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/user/repos?per_page=100&sort=updated")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "token $token")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                
                val repos = mutableListOf<GithubRepo>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    repos.add(
                        GithubRepo(
                            name = obj.getString("name"),
                            fullName = obj.getString("full_name"),
                            cloneUrl = obj.getString("clone_url"),
                            description = obj.optString("description", ""),
                            isPrivate = obj.getBoolean("private")
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    if (repos.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                    } else {
                        adapter.submitList(repos)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    Toast.makeText(this@GitHubRepoListActivity, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun cloneRepo(repo: GithubRepo) {
        val remoteGit = RemoteGitManager(this)
        binding.pbLoader.visibility = View.VISIBLE
        Toast.makeText(this, "⏳ Cloning ${repo.name}...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                remoteGit.cloneRepo(
                    remoteUrl = repo.cloneUrl,
                    repoName = repo.name,
                    pat = token
                )
                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    Toast.makeText(this@GitHubRepoListActivity, "✅ Successfully cloned!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    Toast.makeText(this@GitHubRepoListActivity, "❌ Clone failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

data class GithubRepo(
    val name: String,
    val fullName: String,
    val cloneUrl: String,
    val description: String,
    val isPrivate: Boolean
)
