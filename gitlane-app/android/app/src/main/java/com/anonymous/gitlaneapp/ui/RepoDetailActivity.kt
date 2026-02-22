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
import com.anonymous.gitlaneapp.ui.components.RebaseBanner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.eclipse.jgit.lib.RepositoryState
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
        supportActionBar?.hide()

        // Safety net: log + toast any coroutine crash instead of killing the process
        val crashHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("RepoDetail", "Unhandled coroutine exception", throwable)
            Toast.makeText(this, "⚠️ Error: ${throwable.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        // Custom header back button (in activity_repo_detail.xml)
        binding.btnHeaderBack.setOnClickListener {
            if (currentPath != repoDir) {
                currentPath = currentPath.parentFile ?: repoDir
                loadRepoInfo()
            } else {
                finish()
            }
        }

        setupRecyclerView()
        setupButtons()
        setupRebaseBanner()
        loadRepoInfo()
    }

    private fun setupRebaseBanner() {
        binding.rebaseBannerView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRepoInfo()
        checkRebaseState()
    }

    private fun checkRebaseState() {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { git.getRepositoryState(repoDir) }
            if (state == RepositoryState.REBASING_INTERACTIVE) {
                binding.rebaseBannerView.visibility = View.VISIBLE
                binding.rebaseBannerView.setContent {
                    RebaseBanner(
                        onResume = {
                            val intent = Intent(this@RepoDetailActivity, RebaseActivity::class.java).apply {
                                putExtra(RebaseActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                                putExtra(RebaseActivity.EXTRA_UPSTREAM, "")
                            }
                            startActivity(intent)
                        },
                        onAbort = {
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        git.abortRebase(repoDir)
                                        // Also delete the GitLane persistence state file
                                        val stateFile = File(repoDir, ".git/gitlane/rebase_state.json")
                                        stateFile.delete()
                                    }
                                    android.widget.Toast.makeText(
                                        this@RepoDetailActivity,
                                        "✅ Rebase aborted — repo restored to pre-rebase state",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        this@RepoDetailActivity,
                                        "❌ Abort failed: ${e.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    binding.rebaseBannerView.visibility = View.GONE
                                    loadRepoInfo()
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
        // Create File — opens custom dialog (integrated with my UI polish)
        binding.btnCreateFile.setOnClickListener {
            showCreateFileDialog()
        }

        // ✦ Copilot — opens AI assistant with THIS repo as context (Integrated from Remote)
        binding.btnCopilot.setOnClickListener {
            startActivity(Intent(this, CopilotActivity::class.java).apply {
                putExtra(CopilotActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
            })
        }

        // Import File
        binding.btnImportFile.setOnClickListener {
            importLauncher.launch("*/*")
        }

        // Stage & Commit — opens custom dialog (button is enabled in updateStatusUI)
        binding.btnCommit.setOnClickListener {
            showCommitDialog()
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

        // Folder back button in FILES section
        binding.llFolderBack.setOnClickListener {
            currentPath = currentPath.parentFile ?: repoDir
            loadRepoInfo()
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
            try {
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
            binding.btnHistory.text = "History ($count)"
            fileAdapter.submitList(files)

            // Breadcrumb in custom header
            val relative = currentPath.absolutePath
                .removePrefix(repoDir.parent ?: "")
                .replace("\\", "/")
            binding.tvBreadcrumb.text = relative

            // Show/hide folder back button inside FILES section
            val isInsideSubfolder = currentPath != repoDir
            binding.llFolderBack.visibility = if (isInsideSubfolder) View.VISIBLE else View.GONE
            if (isInsideSubfolder) {
                binding.tvFolderBackLabel.text = "..  /  ${currentPath.parentFile?.name ?: "root"}"
            }

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
            } catch (e: Exception) {
                android.util.Log.e("RepoDetail", "loadRepoInfo failed", e)
                Toast.makeText(this@RepoDetailActivity, "⚠️ Refresh error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createFile(name: String, content: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { git.writeFile(currentPath, name, content) }
                Toast.makeText(this@RepoDetailActivity, "✅ File '$name' created", Toast.LENGTH_SHORT).show()
                loadRepoInfo()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stageAndCommit(message: String) {
        lifecycleScope.launch {
            try {
                val sha = withContext(Dispatchers.IO) {
                    git.stageAll(repoDir)
                    git.commit(repoDir, message)
                }
                Toast.makeText(this@RepoDetailActivity, "✅ Committed [$sha]", Toast.LENGTH_SHORT).show()
                loadRepoInfo()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ Commit failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private var currentRepoFullName: String? = null   // cached for remove calls

    private fun fetchCollaborators(remoteUrl: String) {
        // Reset UI to loading state
        binding.tvCollaboratorLoading.visibility = View.VISIBLE
        binding.tvCollaboratorError.visibility = View.GONE
        binding.llCollaboratorRows.removeAllViews()

        lifecycleScope.launch {
            try {
                val fullName = com.anonymous.gitlaneapp.GitHubApiManager.extractFullName(remoteUrl)
                    ?: return@launch
                currentRepoFullName = fullName

                val creds = com.anonymous.gitlaneapp.CredentialsManager(this@RepoDetailActivity)
                val token = creds.getPat("github.com") ?: run {
                    showCollaboratorError("⚠️ No GitHub token — set one in Settings to view members.")
                    return@launch
                }

                val api = com.anonymous.gitlaneapp.GitHubApiManager(token)

                val list: List<String> = try {
                    api.listCollaborators(fullName)
                } catch (e: Exception) {
                    // 403 = no push access → fall back to contributors; otherwise show empty
                    if (e.message?.contains("403") == true) {
                        try { api.listContributors(fullName) } catch (_: Exception) { emptyList() }
                    } else {
                        emptyList()
                    }
                }

                binding.tvCollaboratorLoading.visibility = View.GONE

                if (list.isEmpty()) {
                    showCollaboratorError("No collaborators or contributors found.")
                } else {
                    binding.tvCollaboratorError.visibility = View.GONE
                    list.forEach { username -> addCollaboratorRow(username, fullName, api) }
                }

            } catch (e: Exception) {
                android.util.Log.w("RepoDetail", "fetchCollaborators failed", e)
                val msg = when {
                    e.message?.contains("Unable to resolve host") == true ->
                        "📡 No internet connection — collaborators cannot be loaded offline."
                    e.message?.contains("timeout") == true ->
                        "⏱ Request timed out. Check your connection and try again."
                    else -> "⚠️ ${e.message}"
                }
                showCollaboratorError(msg)
            }
        }
    }

    // ── Dialog: Create File ───────────────────────────────────────────────────
    private fun showCreateFileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_file, null)
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar).apply {
            setContentView(dialogView)
            window?.apply {
                setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setGravity(android.view.Gravity.BOTTOM)
                setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            }
        }

        val etName    = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDialogFileName)
        val etContent = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDialogFileContent)

        dialogView.findViewById<android.view.View>(R.id.btnDialogClose).setOnClickListener   { dialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btnDialogCancel).setOnClickListener  { dialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btnDialogCreate).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "Enter a file name"; return@setOnClickListener }
            val content = etContent.text.toString()
            dialog.dismiss()
            createFile(name, content)
        }
        dialog.show()
    }

    // ── Dialog: Commit ────────────────────────────────────────────────────────
    private fun showCommitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_commit, null)
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar).apply {
            setContentView(dialogView)
            window?.apply {
                setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setGravity(android.view.Gravity.BOTTOM)
                setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            }
        }

        val etMsg   = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCommitDialogMsg)
        val tvSummary = dialogView.findViewById<android.widget.TextView>(R.id.tvCommitChangeSummary)

        // Populate change summary
        lifecycleScope.launch {
            val status = withContext(kotlinx.coroutines.Dispatchers.IO) { git.getStatus(repoDir) }
            val parts = mutableListOf<String>()
            if (status.modified.isNotEmpty())   parts.add("${status.modified.size} modified")
            if (status.added.isNotEmpty())       parts.add("${status.added.size} added")
            if (status.untracked.isNotEmpty())   parts.add("${status.untracked.size} new")
            if (status.removed.isNotEmpty())     parts.add("${status.removed.size} deleted")
            tvSummary.text = if (parts.isEmpty()) "" else "Changes: ${parts.joinToString(" · ")}"
        }

        dialogView.findViewById<android.view.View>(R.id.btnCommitDialogClose).setOnClickListener  { dialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btnCommitDialogCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btnCommitDialogConfirm).setOnClickListener {
            val msg = etMsg.text.toString().trim()
            if (msg.isEmpty()) { etMsg.error = "Enter a commit message"; return@setOnClickListener }
            dialog.dismiss()
            stageAndCommit(msg)
        }
        dialog.show()
    }
    private fun showCollaboratorError(msg: String) {
        binding.tvCollaboratorLoading.visibility = View.GONE
        binding.tvCollaboratorError.text = msg
        binding.tvCollaboratorError.visibility = View.VISIBLE
    }

    private fun addCollaboratorRow(
        username: String,
        repoFullName: String,
        api: com.anonymous.gitlaneapp.GitHubApiManager
    ) {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * resources.displayMetrics.density).toInt() }
            layoutParams = lp
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(android.graphics.Color.parseColor("#1A4FC3FF"))
            // Rounded corners via background drawable
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 10 * resources.displayMetrics.density
                setColor(android.graphics.Color.parseColor("#1A4FC3FF"))
            }
        }

        // Avatar circle placeholder
        val avatar = android.widget.TextView(this).apply {
            text = username.first().uppercaseChar().toString()
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            val size = (36 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).also {
                it.marginEnd = (10 * resources.displayMetrics.density).toInt()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#4FC3F7"))
            }
        }

        // Username text
        val nameView = android.widget.TextView(this).apply {
            text = "@$username"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.gravity = android.view.Gravity.CENTER_VERTICAL }
        }

        // Remove button
        val removeBtn = android.widget.TextView(this).apply {
            text = "✕ Remove"
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#FF5252"))
            setPadding(
                (8 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt()
            )
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 6 * resources.displayMetrics.density
                setStroke(
                    (1 * resources.displayMetrics.density).toInt(),
                    android.graphics.Color.parseColor("#FF5252")
                )
                setColor(android.graphics.Color.TRANSPARENT)
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.CENTER_VERTICAL }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                confirmRemoveCollaborator(username, repoFullName, api, row)
            }
        }

        row.addView(avatar)
        row.addView(nameView)
        row.addView(removeBtn)
        binding.llCollaboratorRows.addView(row)
    }

    private fun confirmRemoveCollaborator(
        username: String,
        repoFullName: String,
        api: com.anonymous.gitlaneapp.GitHubApiManager,
        row: android.view.View
    ) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove Collaborator?")
            .setMessage("Remove @$username from $repoFullName?\n\nThey will lose push access immediately.")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.removeCollaborator(repoFullName, username) }
                        binding.llCollaboratorRows.removeView(row)
                        Toast.makeText(
                            this@RepoDetailActivity,
                            "✅ @$username removed from $repoFullName",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (binding.llCollaboratorRows.childCount == 0) {
                            showCollaboratorError("No collaborators remaining.")
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@RepoDetailActivity,
                            "❌ ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


>>>>>>> 94b59930ce74a586ccfcc14c7822cf331c59c8dc
    private fun updateStatusUI(status: com.anonymous.gitlaneapp.GitStatus) {
        // Show/Hide conflict alert
        binding.cardConflict.visibility = if (status.conflicting.isNotEmpty()) View.VISIBLE else View.GONE

        val hasChanges = status.hasChanges()

        // Enable commit button only when there are pending changes
        binding.btnCommit.isEnabled = hasChanges
        binding.btnCommit.alpha = if (hasChanges) 1f else 0.40f

        if (!hasChanges) {
            binding.llChangesContainer.visibility = View.GONE
            return
        }

        binding.llChangesContainer.visibility = View.VISIBLE
        binding.llChangesList.removeAllViews()

        val allChanges = status.allChanges()
        val displayLimit = 10
        val toDisplay = allChanges.take(displayLimit)

        toDisplay.forEach { path ->
            // Row container
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }

            val isConflict  = status.conflicting.contains(path)
            val isUntracked = status.untracked.contains(path)
            val icon = when {
                isConflict  -> "❗"
                status.modified.contains(path) || status.added.contains(path) -> "📝"
                isUntracked -> "🆕"
                status.removed.contains(path) -> "🗑️"
                else -> "•"
            }

            // File label
            val label = android.widget.TextView(this).apply {
                text = "$icon $path"
                textSize = 13f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                if (isConflict) {
                    setTextColor(android.graphics.Color.parseColor("#FF5252"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    setTextColor(resources.getColor(R.color.text_primary, theme))
                }
            }

            // Delete / remove button
            val deleteBtn = android.widget.TextView(this).apply {
                text = "✕"
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#475569"))
                setPadding(24, 0, 4, 0)
                setOnClickListener {
                    deleteOrRemoveFile(path, isUntracked)
                }
            }

            row.addView(label)
            row.addView(deleteBtn)
            binding.llChangesList.addView(row)
        }

        if (allChanges.size > displayLimit) {
            val moreText = android.widget.TextView(this).apply {
                text = "… and ${allChanges.size - displayLimit} more changes"
                textSize = 12f
                setPadding(0, 8, 0, 4)
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setTypeface(null, android.graphics.Typeface.ITALIC)
            }
            binding.llChangesList.addView(moreText)
        }
    }

    /** Delete an untracked file physically, or stage removal of a tracked file. */
    private fun deleteOrRemoveFile(relativePath: String, isUntracked: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val target = File(repoDir, relativePath)
                    if (isUntracked) {
                        target.delete()
                    } else {
                        // Stage the deletion so git knows about it
                        git.stageRemoval(repoDir, relativePath)
                        target.delete()
                    }
                }
                Toast.makeText(this@RepoDetailActivity, "🗑️ Removed $relativePath", Toast.LENGTH_SHORT).show()
                loadRepoInfo()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
            }
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
}
