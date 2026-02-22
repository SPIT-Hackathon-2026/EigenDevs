package com.anonymous.gitlaneapp.ui

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.GitManager
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

    // Full list for search filtering
    private var allRepos: List<GithubRepo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGithubRepoListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        val creds = CredentialsManager(this)
        token = creds.getPat("github.com") ?: run {
            Toast.makeText(this, "GitHub token missing — connect in Profile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        setupRecyclerView()
        setupSearch()
        fetchRepos()
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                val filtered = if (q.isEmpty()) allRepos
                    else allRepos.filter {
                        it.name.contains(q, ignoreCase = true) ||
                        it.fullName.contains(q, ignoreCase = true) ||
                        it.description.contains(q, ignoreCase = true)
                    }
                adapter.submitList(filtered)
                if (filtered.isEmpty() && allRepos.isNotEmpty()) {
                    binding.tvEmpty.text = "No results for \"$q\""
                    binding.layoutEmpty.visibility = View.VISIBLE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                }
            }
        })
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = GithubRepoAdapter(
            onClone         = { repo -> cloneRepo(repo) },
            onCommitHistory = { repo -> showCommitHistory(repo) }
        )
        binding.rvGithubRepos.layoutManager = LinearLayoutManager(this)
        binding.rvGithubRepos.adapter = adapter
    }

    // ─── Fetch Repos ──────────────────────────────────────────────────────────

    private fun fetchRepos() {
        binding.pbLoader.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/user/repos?per_page=100&sort=updated")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "token $token")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr  = JSONArray(body)

                val repos = mutableListOf<GithubRepo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    repos.add(
                        GithubRepo(
                            name        = o.getString("name"),
                            fullName    = o.getString("full_name"),
                            cloneUrl    = o.getString("clone_url"),
                            htmlUrl     = o.optString("html_url", ""),
                            description = o.optString("description", ""),
                            isPrivate   = o.getBoolean("private"),
                            language    = o.optString("language", ""),
                            starCount   = o.optInt("stargazers_count", 0),
                            updatedAt   = o.optString("updated_at", "")
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    if (repos.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                    } else {
                        allRepos = repos                           // store for search
                        binding.tvRepoCount.text = "${repos.size} repositories"
                        adapter.submitList(repos)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    Toast.makeText(this@GitHubRepoListActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── Clone Locally ────────────────────────────────────────────────────────

    private fun cloneRepo(repo: GithubRepo) {
        binding.pbLoader.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            // Check if already cloned locally (suspend call must be inside coroutine)
            val git      = GitManager(this@GitHubRepoListActivity)
            val existing = git.listRepos().find { it.name == repo.name }
            if (existing != null) {
                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    Toast.makeText(
                        this@GitHubRepoListActivity,
                        "\"${repo.name}\" is already in your local repos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val remoteGit = RemoteGitManager(this@GitHubRepoListActivity)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GitHubRepoListActivity, "⏳ Cloning ${repo.name}…", Toast.LENGTH_SHORT).show()
            }

            try {
                remoteGit.cloneRepo(
                    remoteUrl = repo.cloneUrl,
                    repoName  = repo.name,
                    pat       = token
                )
                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    Toast.makeText(
                        this@GitHubRepoListActivity,
                        "✅ Cloned \"${repo.name}\" to local storage",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_OK)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbLoader.visibility = View.GONE
                    Toast.makeText(
                        this@GitHubRepoListActivity,
                        "❌ Clone failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ─── Commit History ───────────────────────────────────────────────────────

    private fun showCommitHistory(repo: GithubRepo) {
        val sheet = CommitHistoryBottomSheet.newInstance(
            repoFullName = repo.fullName,
            token        = token
        )
        sheet.show(supportFragmentManager, "commit_history")
    }
}

// ─── Data class ───────────────────────────────────────────────────────────────

data class GithubRepo(
    val name:        String,
    val fullName:    String,
    val cloneUrl:    String,
    val htmlUrl:     String  = "",
    val description: String  = "",
    val isPrivate:   Boolean = false,
    val language:    String? = null,
    val starCount:   Int     = 0,
    val updatedAt:   String  = ""
)
