package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.databinding.ActivityRepoDetailBinding
import com.anonymous.gitlaneapp.ui.adapter.FileAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var currentPath: File // For folder browsing
    private lateinit var fileAdapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRepoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: "Repository"
        repoDir = File(repoPath)
        currentPath = repoDir

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

    private val editLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadRepoInfo()
        }
    }

    private val importLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { importFile(it) }
    }

    private fun importFile(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    val name = cursor?.use {
                        if (it.moveToFirst()) {
                            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) it.getString(idx) else "imported_file"
                        } else "imported_file"
                    } ?: "imported_file"

                    val targetFile = File(currentPath, name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Toast.makeText(this@RepoDetailActivity, "✅ File imported", Toast.LENGTH_SHORT).show()
                loadRepoInfo()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onClick = { file ->
                if (file.isDirectory) {
                    currentPath = file
                    loadRepoInfo()
                } else {
                    val intent = Intent(this, FileEditorActivity::class.java).apply {
                        putExtra(FileEditorActivity.EXTRA_FILE_PATH, file.absolutePath)
                        putExtra(FileEditorActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                    }
                    editLauncher.launch(intent)
                }
            }
        )
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

        // Import File
        binding.btnImportFile.setOnClickListener {
            importLauncher.launch("*/*")
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

        // Pro Visual Graph
        binding.btnModernHistory.setOnClickListener {
            val intent = Intent(this, ModernHistoryActivity::class.java).apply {
                putExtra("extra_repo_path", repoDir.absolutePath)
            }
            startActivity(intent)
        }

        binding.btnHistory.setOnLongClickListener {
            optimizeRepoStorage()
            true
        }

        binding.btnResolveConflict.setOnClickListener {
            // Launch conflict resolution activity
            val intent = Intent(this, ConflictResolutionActivity::class.java).apply {
                putExtra(ConflictResolutionActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
            }
            editLauncher.launch(intent)
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

        // Tags
        binding.btnTags.setOnClickListener {
            val intent = Intent(this, TagActivity::class.java).apply {
                putExtra(TagActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                putExtra(TagActivity.EXTRA_REPO_NAME, repoDir.name)
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

        // Push to Remote
        binding.btnPush.setOnClickListener {
            pushToRemote()
        }

        // Add Collaborator
        binding.btnAddCollaborator.setOnClickListener {
            showAddCollaboratorDialog()
        }
    }

    private fun optimizeRepoStorage() {
        lifecycleScope.launch {
            Toast.makeText(this@RepoDetailActivity, "⏳ Optimizing storage (Delta Compression)...", Toast.LENGTH_SHORT).show()
            val success = git.optimizeStorage(repoDir)
            if (success) {
                Toast.makeText(this@RepoDetailActivity, "✅ Storage optimized! Objects packed.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@RepoDetailActivity, "❌ Optimization failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pushToRemote() {
        lifecycleScope.launch {
            try {
                val remotes = withContext(Dispatchers.IO) { git.listRemotes(repoDir) }
                
                if (remotes.isEmpty()) {
                    // Ask to create a new remote repo
                    showCreateRemoteRepoDialog()
                } else {
                    performPush(remotes.first().fetchUrl)
                }
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCreateRemoteRepoDialog() {
        val options = arrayOf("🌍 Public Repository", "🔒 Private Repository")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create Remote on GitHub?")
            .setItems(options) { _, which ->
                val isPrivate = (which == 1)
                createAndPushRemote(isPrivate)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createAndPushRemote(isPrivate: Boolean) {
        binding.btnPush.isEnabled = false
        Toast.makeText(this, "⏳ Creating repository on GitHub...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val creds = com.anonymous.gitlaneapp.CredentialsManager(this@RepoDetailActivity)
                val token = creds.getPat("github.com") ?: throw Exception("GitHub token not found. Please add it in Settings.")
                
                val api = com.anonymous.gitlaneapp.GitHubApiManager(token)
                val cloneUrl = api.createRepo(repoDir.name, isPrivate)
                
                // Add as 'origin'
                withContext(Dispatchers.IO) {
                    git.addRemote(repoDir, "origin", cloneUrl)
                }
                
                Toast.makeText(this@RepoDetailActivity, "🚀 Remote created! Pushing data...", Toast.LENGTH_SHORT).show()
                performPush(cloneUrl)
                loadRepoInfo() // Refresh buttons
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ Failed: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnPush.isEnabled = true
            }
        }
    }

    private fun performPush(remoteUrl: String) {
        binding.btnPush.isEnabled = false
        lifecycleScope.launch {
            try {
                val creds = com.anonymous.gitlaneapp.CredentialsManager(this@RepoDetailActivity)
                val pat = creds.getPatForUrl(remoteUrl) 
                    ?: throw Exception("No PAT saved for ${com.anonymous.gitlaneapp.CredentialsManager.extractHost(remoteUrl)}")

                withContext(Dispatchers.IO) {
                    git.push(repoDir, pat = pat)
                }
                Toast.makeText(this@RepoDetailActivity, "✅ Pushed successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ Push failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnPush.isEnabled = true
            }
        }
    }

    private fun showAddCollaboratorDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "GitHub Username"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Collaborator")
            .setMessage("Enter the GitHub username of the person you want to invite.")
            .setView(input)
            .setPositiveButton("Send Invitation") { _, _ ->
                val username = input.text.toString().trim()
                if (username.isNotEmpty()) addCollaborator(username)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCollaborator(username: String) {
        Toast.makeText(this, "⏳ Sending invitation to $username...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val remotes = withContext(Dispatchers.IO) { git.listRemotes(repoDir) }
                val remoteUrl = remotes.first().fetchUrl
                val fullName = com.anonymous.gitlaneapp.GitHubApiManager.extractFullName(remoteUrl) 
                    ?: throw Exception("Could not determine repository name for GitHub")
                
                val creds = com.anonymous.gitlaneapp.CredentialsManager(this@RepoDetailActivity)
                val token = creds.getPat("github.com") ?: throw Exception("GitHub token missing")
                
                val api = com.anonymous.gitlaneapp.GitHubApiManager(token)
                val success = api.addCollaborator(fullName, username)
                
                if (success) {
                    Toast.makeText(this@RepoDetailActivity, "✅ Invitation sent to $username!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@RepoDetailActivity, "❌ Could not send invitation", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadRepoInfo() {
        lifecycleScope.launch {
            val branch = withContext(Dispatchers.IO) { git.currentBranch(repoDir) }
            val count  = withContext(Dispatchers.IO) { git.commitCount(repoDir) }
            val status = withContext(Dispatchers.IO) { git.getStatus(repoDir) }
            
            // Modified listFiles to take currentPath
            val files  = withContext(Dispatchers.IO) {
                currentPath.listFiles { f -> f.name != ".git" }
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                    ?: emptyList()
            }

            binding.tvBranch.text = "🌿 $branch"
            binding.tvCommitCount.text = "$count commit${if (count != 1) "s" else ""}"
            binding.btnHistory.text = "View History ($count)"
            fileAdapter.submitList(files)

            // Update title to show breadcrumb or folder
            val relative = currentPath.absolutePath.removePrefix(repoDir.parent ?: "")
            supportActionBar?.subtitle = relative

            // Show 'Add Collaborator' if linked to GitHub
            val remotes = withContext(Dispatchers.IO) { git.listRemotes(repoDir) }
            val githubRemote = remotes.find { it.fetchUrl.contains("github.com") }
            val isGitHub = githubRemote != null
            
            binding.btnAddCollaborator.visibility = if (isGitHub) View.VISIBLE else View.GONE
            binding.llCollaboratorsContainer.visibility = if (isGitHub) View.VISIBLE else View.GONE
            
            if (isGitHub) {
                fetchCollaborators(githubRemote!!.fetchUrl)
            }

            // Update uncommitted changes UI
            updateStatusUI(status)
        }
    }

    private fun fetchCollaborators(remoteUrl: String) {
        lifecycleScope.launch {
            try {
                val fullName = com.anonymous.gitlaneapp.GitHubApiManager.extractFullName(remoteUrl) ?: return@launch
                val creds = com.anonymous.gitlaneapp.CredentialsManager(this@RepoDetailActivity)
                val token = creds.getPat("github.com") ?: return@launch
                
                val api = com.anonymous.gitlaneapp.GitHubApiManager(token)
                
                // Try listing collaborators (requires push access)
                val list = try {
                    api.listCollaborators(fullName)
                } catch (e: Exception) {
                    if (e.message?.contains("403") == true) {
                        // Fallback to contributors for public repos where we don't have push access
                        api.listContributors(fullName)
                    } else {
                        throw e
                    }
                }
                
                binding.tvCollaboratorList.text = if (list.isEmpty()) {
                    "No members or contributors found"
                } else {
                    "Team: " + list.joinToString(", ")
                }
            } catch (e: Exception) {
                binding.tvCollaboratorList.text = "Error fetching: ${e.message}"
            }
        }
    }

    private fun updateStatusUI(status: com.anonymous.gitlaneapp.GitStatus) {
        // Show/Hide Merge Conflict Alert
        binding.cardConflict.visibility = if (status.conflicting.isNotEmpty()) View.VISIBLE else View.GONE

        if (!status.hasChanges()) {
            binding.llChangesContainer.visibility = View.GONE
            return
        }

        binding.llChangesContainer.visibility = View.VISIBLE
        binding.llChangesList.removeAllViews()

        val allChanges = status.allChanges()
        val displayLimit = 10
        val toDisplay = allChanges.take(displayLimit)

        toDisplay.forEach { path ->
            val textView = android.widget.TextView(this).apply {
                val isConflict = status.conflicting.contains(path)
                val icon = when {
                    isConflict -> "❗"
                    status.modified.contains(path) || status.added.contains(path) -> "📝"
                    status.untracked.contains(path) -> "🆕"
                    status.removed.contains(path) -> "🗑️"
                    else -> "•"
                }
                text = "$icon $path"
                textSize = 13f
                setPadding(0, 4, 0, 4)
                if (isConflict) {
                    setTextColor(android.graphics.Color.parseColor("#FF5252"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    setTextColor(resources.getColor(R.color.text_primary, theme))
                }
            }
            binding.llChangesList.addView(textView)
        }

        if (allChanges.size > displayLimit) {
            val moreText = android.widget.TextView(this).apply {
                text = "... and ${allChanges.size - displayLimit} more changes"
                textSize = 12f
                setPadding(0, 8, 0, 4)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setTypeface(null, android.graphics.Typeface.ITALIC)
            }
            binding.llChangesList.addView(moreText)
        }
    }

    override fun onBackPressed() {
        if (currentPath != repoDir) {
            currentPath = currentPath.parentFile ?: repoDir
            loadRepoInfo()
        } else {
            super.onBackPressed()
        }
    }

    private fun createFile(name: String, content: String) {
        binding.btnCreateFile.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    git.writeFile(repoDir, name, content)
                }
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
                withContext(Dispatchers.IO) {
                    git.stageAll(repoDir)
                    git.commit(repoDir, message)
                }
                binding.etCommitMsg.setText("")
                Toast.makeText(
                    this@RepoDetailActivity,
                    "✅ Committed!",
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
