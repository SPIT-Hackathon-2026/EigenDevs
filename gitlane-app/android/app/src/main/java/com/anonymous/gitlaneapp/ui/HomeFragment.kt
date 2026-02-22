package com.anonymous.gitlaneapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.databinding.FragmentHomeBinding
import com.anonymous.gitlaneapp.ui.adapter.RepoAdapter
import kotlinx.coroutines.launch
import java.io.File

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var git: GitManager
    private lateinit var adapter: RepoAdapter
    private lateinit var creds: CredentialsManager

    private val refreshLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) loadRepos()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        git   = GitManager(requireContext())
        creds = CredentialsManager(requireContext())

        setupRecyclerView()
        setupButtons()
        loadRepos()
        updateHeader()
        checkFirstRun()
    }

    override fun onResume() {
        super.onResume()
        loadRepos()
        updateHeader()
    }

    // ─── Header ──────────────────────────────────────────────────────────────

    private fun updateHeader() {
        val token = creds.getPat("github.com")
        if (!token.isNullOrBlank()) {
            // Try to extract stored login — fall back to "Connected"
            binding.tvUsername.text = "Connected as @github"
            // If we have a cached login from a previous profile load we could show it;
            // for now just mark connected. Full name shown on Profile tab.
        } else {
            binding.tvUsername.text = "Not connected · go to Profile to sign in"
        }
        binding.cardSynced.visibility = if (!token.isNullOrBlank()) View.VISIBLE else View.GONE
    }

    // ─── Buttons ─────────────────────────────────────────────────────────────

    private fun setupButtons() {
        // + New Repository → bottom-sheet style dialog
        binding.btnCreateRepo.setOnClickListener { showCreateRepoDialog() }

        // Clone → open PublicRepoSearchActivity (already has clone flow)
        binding.btnGoToClone.setOnClickListener {
            refreshLauncher.launch(Intent(requireContext(), PublicRepoSearchActivity::class.java))
        }

        // Fetch My Repos
        binding.btnFetchGithub.setOnClickListener {
            val token = creds.getPat("github.com")
            if (token.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Set a GitHub token in Profile first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            refreshLauncher.launch(Intent(requireContext(), GitHubRepoListActivity::class.java))
        }


        // QR FAB
        binding.fabScanQr.setOnClickListener {
            refreshLauncher.launch(Intent(requireContext(), QRScanActivity::class.java))
        }
    }

    // ─── Create Repo Dialog ───────────────────────────────────────────────────

    private fun showCreateRepoDialog() {
        val context  = requireContext()
        val dialogView = layoutInflater.inflate(com.anonymous.gitlaneapp.R.layout.dialog_new_repo, null)

        val tilName   = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(com.anonymous.gitlaneapp.R.id.tilRepoName)
        val etName    = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.anonymous.gitlaneapp.R.id.etRepoName)
        val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.anonymous.gitlaneapp.R.id.btnDialogCreate)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.anonymous.gitlaneapp.R.id.btnDialogCancel)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        // Transparent window so our custom dark bg shows through
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.show()

        btnCreate.setOnClickListener {
            val name = etName?.text.toString().trim()
            when {
                name.isEmpty()                              -> tilName.error = "Enter a name"
                !name.matches(Regex("[a-zA-Z0-9_.\\-]+")) -> tilName.error = "Letters, numbers, - _ . only"
                else -> { dialog.dismiss(); createRepo(name) }
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        // Auto-focus and show keyboard
        etName?.requestFocus()
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                  as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(etName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }


    // ─── RecyclerView ────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = RepoAdapter(
            onClick = { repoDir ->
                val intent = Intent(requireContext(), RepoDetailActivity::class.java).apply {
                    putExtra(RepoDetailActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
                    putExtra(RepoDetailActivity.EXTRA_REPO_NAME, repoDir.name)
                }
                startActivity(intent)
            },
            onMore = { repoDir, anchor -> showRepoMenu(repoDir, anchor) }
        )
        binding.rvRepos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRepos.adapter = adapter
        binding.rvRepos.isNestedScrollingEnabled = false
    }

    private fun showRepoMenu(repoDir: File, anchor: android.view.View) {
        val items   = arrayOf("✏️  Rename", "📂  Duplicate", "🗑️  Delete")
        val listPop = android.widget.ListPopupWindow(requireContext())
        listPop.anchorView = anchor
        listPop.setAdapter(
            android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        )
        listPop.width  = (200 * resources.displayMetrics.density).toInt()
        listPop.isModal = true
        listPop.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#0F172A"))
                cornerRadius = (12 * resources.displayMetrics.density)
                setStroke(
                    (1 * resources.displayMetrics.density).toInt(),
                    android.graphics.Color.parseColor("#1E293B")
                )
            }
        )
        listPop.setOnItemClickListener { _, _, position, _ ->
            listPop.dismiss()
            when (position) {
                0 -> showRenameRepoDialog(repoDir)
                1 -> showDuplicateRepoDialog(repoDir)
                2 -> showDeleteRepoDialog(repoDir)
            }
        }
        listPop.show()
    }

    private fun showRenameRepoDialog(repoDir: File) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(repoDir.name); selectAll()
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Rename Repository")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == repoDir.name) return@setPositiveButton
                lifecycleScope.launch {
                    try { git.renameRepo(repoDir, newName); loadRepos() }
                    catch (e: Exception) { toast("❌ ${e.message}") }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDuplicateRepoDialog(repoDir: File) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Duplicate Repository?")
            .setMessage("Create a full copy of '${repoDir.name}'?")
            .setPositiveButton("Duplicate") { _, _ ->
                lifecycleScope.launch {
                    try { git.duplicateRepo(repoDir); loadRepos(); toast("✅ Duplicated") }
                    catch (e: Exception) { toast("❌ ${e.message}") }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDeleteRepoDialog(repoDir: File) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Repository?")
            .setMessage("This will permanently delete '${repoDir.name}'. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try { git.deleteRepo(repoDir); loadRepos() }
                    catch (e: Exception) { toast("❌ ${e.message}") }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ─── Repo Loading ─────────────────────────────────────────────────────────

    private fun createRepo(name: String) {
        lifecycleScope.launch {
            try {
                git.initRepo(name)
                toast("✅ Repository \"$name\" created")
                loadRepos()
            } catch (e: Exception) {
                toast("❌ Failed: ${e.message}")
            }
        }
    }

    private fun loadRepos() {
        lifecycleScope.launch {
            val repos: List<File> = git.listRepos()
            adapter.submitList(repos)
            val count = repos.size
            binding.tvRepoCount.text = "Local Repositories ($count)"
            binding.tvEmptyRepos.visibility = if (count == 0) View.VISIBLE else View.GONE
            binding.rvRepos.visibility     = if (count == 0) View.GONE  else View.VISIBLE
        }
    }

    // ─── First Run ────────────────────────────────────────────────────────────

    private fun checkFirstRun() {
        if (!creds.hasAnyToken()) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Welcome to GitLane!")
                .setMessage("To clone private repos and view your GitHub projects, connect your GitHub account in the Profile tab.")
                .setPositiveButton("Go to Profile") { _, _ ->
                    // Switch to Profile tab
                    (activity as? DashboardActivity)?.switchToTab(2)
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
