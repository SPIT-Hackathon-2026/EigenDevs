package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anonymous.gitlaneapp.BranchInfo
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.databinding.ActivityBranchBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BranchActivity — Full branch management UI.
 *
 * Features:
 *  - List all local branches with current marker + ahead/behind counts
 *  - Create new branch (optionally checkout)
 *  - Checkout branch
 *  - Rename branch
 *  - Delete branch (with force option)
 *  - Compare two branches (shows ahead/behind commit counts)
 */
class BranchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
        const val EXTRA_REPO_NAME = "extra_repo_name"
    }

    private lateinit var git: GitManager
    private lateinit var repoDir: File
    private lateinit var adapter: BranchAdapter
    private lateinit var binding: ActivityBranchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBranchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: "Branches"
        repoDir = File(repoPath)
        git = GitManager(this)

        supportActionBar?.title = "🌿 $repoName — Branches"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = BranchAdapter(
            onCheckout = { branch -> checkoutBranch(branch) },
            onDelete   = { branch -> confirmDelete(branch) },
            onRename   = { branch -> showRenameDialog(branch) },
            onMerge    = { branch -> confirmMerge(branch) },
            onRebase   = { branch -> startRebase(branch) }
        )

        binding.rvBranches.apply {
            layoutManager = LinearLayoutManager(this@BranchActivity)
            adapter = this@BranchActivity.adapter
        }

        binding.swipes.setOnRefreshListener { loadBranches() }

        setupButtons()
        loadBranches()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun setupButtons() {
        binding.fabAddBranch.setOnClickListener { showCreateBranchDialog() }
        binding.btnCompare.setOnClickListener   { showCompareDialog() }
        binding.btnRestoreBranch.setOnClickListener { showRestoreDialog() }
    }

    private fun showRestoreDialog() {
        lifecycleScope.launch {
            val logs = git.listRecoverableRefs(repoDir)
            if (logs.isEmpty()) {
                Toast.makeText(this@BranchActivity, "No deleted branches found to recover.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val names = logs.map { logEntry -> "${logEntry.name} (${logEntry.sha.take(7)})" }.toTypedArray()
            AlertDialog.Builder(this@BranchActivity)
                .setTitle("Restore Deleted Branch")
                .setItems(names as Array<CharSequence>) { _, which ->
                    val log = logs[which]
                    lifecycleScope.launch {
                        try {
                            git.recoverRef(repoDir, log.name, log.sha)
                            Toast.makeText(this@BranchActivity, "✅ Branch '${log.name}' restored!", Toast.LENGTH_SHORT).show()
                            loadBranches()
                        } catch (e: Exception) {
                            Toast.makeText(this@BranchActivity, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadBranches() {
        lifecycleScope.launch {
            try {
                binding.swipes.isRefreshing = true
                val current = git.currentBranch(repoDir)
                val rawList = withContext(Dispatchers.IO) { git.listBranches(repoDir) }
                
                // For each branch (except current), check if it's ahead of current branch
                val branches = rawList.map { b ->
                    if (!b.isCurrent) {
                        val diff = git.compareBranches(repoDir, current, b.name)
                        b.copy(aheadOfCurrent = diff.ahead)
                    } else b
                }
                
                adapter.submitList(branches)
                binding.tvBranchCount.text =
                    "${branches.size} branch${if (branches.size != 1) "es" else ""}"
            } catch (e: Exception) {
                Toast.makeText(this@BranchActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipes.isRefreshing = false
            }
        }
    }

    private fun checkoutBranch(branch: BranchInfo) {
        if (branch.isCurrent) {
            Toast.makeText(this, "Already on '${branch.name}'", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                git.checkoutBranch(repoDir, branch.name)
                Toast.makeText(this@BranchActivity, "✅ Switched to '${branch.name}'", Toast.LENGTH_SHORT).show()
                loadBranches()
            } catch (e: Exception) {
                Toast.makeText(this@BranchActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(branch: BranchInfo) {
        if (branch.isCurrent) {
            Toast.makeText(this, "Cannot delete the current branch", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete '${branch.name}'?")
            .setMessage("Are you sure? This branch will be removed locally.\n(You can recover it later from the Restore menu).")
            .setPositiveButton("Delete") { _, _ -> deleteBranch(branch) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteBranch(branch: BranchInfo) {
        lifecycleScope.launch {
            try {
                // Log for recovery BEFORE deleting
                val sha = git.resolveRef(repoDir, branch.name) ?: ""
                git.logDeletedRef(repoDir, branch.name, sha)
                
                git.deleteBranch(repoDir, branch.name)
                Toast.makeText(this@BranchActivity, "🗑️ Deleted branch: ${branch.name}", Toast.LENGTH_SHORT).show()
                loadBranches()
            } catch (e: Exception) {
                Toast.makeText(this@BranchActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRenameDialog(branch: BranchInfo) {
        val input = EditText(this).apply {
            setText(branch.name)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Rename branch '${branch.name}'")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank() || newName == branch.name) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        git.renameBranch(repoDir, branch.name, newName)
                        Toast.makeText(this@BranchActivity, "✅ Renamed to '$newName'", Toast.LENGTH_SHORT).show()
                        loadBranches()
                    } catch (e: Exception) {
                        Toast.makeText(this@BranchActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateBranchDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etName = EditText(this).apply { hint = "Branch name" }
        val cbCheckout = CheckBox(this).apply { text = "Checkout after creating"; isChecked = true }
        container.addView(etName)
        container.addView(cbCheckout)

        AlertDialog.Builder(this)
            .setTitle("Create New Branch")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) { Toast.makeText(this, "Enter a branch name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                lifecycleScope.launch {
                    try {
                        if (cbCheckout.isChecked) {
                            git.createAndCheckoutBranch(repoDir, name)
                        } else {
                            git.createBranch(repoDir, name)
                        }
                        Toast.makeText(this@BranchActivity, "✅ Branch '$name' created", Toast.LENGTH_SHORT).show()
                        loadBranches()
                    } catch (e: Exception) {
                        Toast.makeText(this@BranchActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCompareDialog() {
        lifecycleScope.launch {
            val branches = git.listBranches(repoDir).map { it.name }.toTypedArray()
            if (branches.size < 2) {
                Toast.makeText(this@BranchActivity, "Need at least 2 branches to compare", Toast.LENGTH_SHORT).show()
                return@launch
            }
            var base   = branches[0]
            var target = branches[1]

            val view = layoutInflater.inflate(R.layout.dialog_compare_branches, null)
            val spBase   = view.findViewById<Spinner>(R.id.spBase)
            val spTarget = view.findViewById<Spinner>(R.id.spTarget)
            val tvResult = view.findViewById<TextView>(R.id.tvCompareResult)

            val spinAdapter = ArrayAdapter<String>(this@BranchActivity, android.R.layout.simple_spinner_item, branches)
            spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spBase.adapter   = spinAdapter
            spTarget.adapter = spinAdapter
            spTarget.setSelection(1)

            val listener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    base   = spBase.selectedItem as String
                    target = spTarget.selectedItem as String
                    lifecycleScope.launch {
                        val result = git.compareBranches(repoDir, base, target)
                        tvResult.text = "'$target' is ${result.ahead} commit(s) ahead and ${result.behind} commit(s) behind '$base'"
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            spBase.onItemSelectedListener   = listener
            spTarget.onItemSelectedListener = listener

            AlertDialog.Builder(this@BranchActivity)
                .setTitle("Compare Branches")
                .setView(view)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun confirmMerge(branch: BranchInfo) {
        if (branch.isCurrent) return
        AlertDialog.Builder(this)
            .setTitle("Merge '${branch.name}'?")
            .setMessage("This will merge all changes from '${branch.name}' INTO the current branch. Are you sure?")
            .setPositiveButton("Merge") { _, _ -> mergeBranch(branch.name) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun mergeBranch(name: String) {
        lifecycleScope.launch {
            try {
                val result = git.merge(repoDir, name)
                if (result.mergeStatus.isSuccessful) {
                    Toast.makeText(this@BranchActivity, "✅ Merged successfully: ${result.mergeStatus}", Toast.LENGTH_LONG).show()
                    loadBranches()
                } else if (result.mergeStatus == org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING) {
                    Toast.makeText(this@BranchActivity, "🚨 MERGE CONFLICT! Return to repo to resolve.", Toast.LENGTH_LONG).show()
                    finish() // Close to show conflict in RepoDetail
                } else {
                    Toast.makeText(this@BranchActivity, "⚠️ Merge status: ${result.mergeStatus}", Toast.LENGTH_LONG).show()
                    loadBranches()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BranchActivity, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRebase(branch: BranchInfo) {
        if (branch.isCurrent) return
        val intent = android.content.Intent(this, RebaseActivity::class.java).apply {
            putExtra(RebaseActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
            putExtra(RebaseActivity.EXTRA_UPSTREAM, branch.name)
        }
        startActivity(intent)
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class BranchAdapter(
    private val onCheckout: (BranchInfo) -> Unit,
    private val onDelete:   (BranchInfo) -> Unit,
    private val onRename:   (BranchInfo) -> Unit,
    private val onMerge:    (BranchInfo) -> Unit,
    private val onRebase:   (BranchInfo) -> Unit
) : RecyclerView.Adapter<BranchAdapter.VH>() {

    private var items = listOf<BranchInfo>()

    fun submitList(list: List<BranchInfo>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName:     TextView = v.findViewById(R.id.tvBranchName)
        val tvStatus:   TextView = v.findViewById(R.id.tvBranchStatus)
        val btnCheck:   Button   = v.findViewById(R.id.btnCheckout)
        val btnRename:  Button   = v.findViewById(R.id.btnRenameBranch)
        val btnMerge:   Button   = v.findViewById(R.id.btnMergeBranch)
        val btnRebase:  Button   = v.findViewById(R.id.btnRebaseBranch)
        val btnDelete:  Button   = v.findViewById(R.id.btnDeleteBranch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_branch, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        holder.tvName.text   = if (b.isCurrent) "★ ${b.name}  (current)" else b.name
        holder.tvStatus.text = when {
            b.upstream != null -> "↑${b.ahead} ↓${b.behind}  tracks ${b.upstream}"
            else               -> "local only"
        }
        holder.btnCheck.setOnClickListener  { onCheckout(b) }
        holder.btnRename.setOnClickListener { onRename(b) }
        holder.btnMerge.setOnClickListener  { onMerge(b) }
        holder.btnRebase.setOnClickListener { onRebase(b) }
        holder.btnDelete.setOnClickListener { onDelete(b) }
        
        holder.btnMerge.visibility = if (b.isCurrent) View.GONE else View.VISIBLE
        holder.btnRebase.visibility = if (b.isCurrent) View.GONE else View.VISIBLE
        
        // Disable if no new commits to merge/rebase
        val hasNew = b.aheadOfCurrent > 0
        holder.btnMerge.isEnabled = hasNew
        holder.btnRebase.isEnabled = hasNew
        holder.btnMerge.alpha = if (hasNew) 1f else 0.4f
        holder.btnRebase.alpha = if (hasNew) 1f else 0.4f
        
        if (!hasNew && !b.isCurrent) {
            holder.tvStatus.text = "Up to date with current"
        }
    }
}
