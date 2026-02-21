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
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_branch)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: "Branches"
        repoDir = File(repoPath)
        git = GitManager(this)

        supportActionBar?.title = "🌿 $repoName — Branches"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = BranchAdapter(
            onCheckout = { branch -> checkoutBranch(branch) },
            onDelete   = { branch -> confirmDelete(branch) },
            onRename   = { branch -> showRenameDialog(branch) }
        )

        findViewById<RecyclerView>(R.id.rvBranches).apply {
            layoutManager = LinearLayoutManager(this@BranchActivity)
            adapter = this@BranchActivity.adapter
        }

        findViewById<Button>(R.id.btnNewBranch).setOnClickListener { showCreateBranchDialog() }
        findViewById<Button>(R.id.btnCompareBranches).setOnClickListener { showCompareDialog() }

        loadBranches()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadBranches() {
        lifecycleScope.launch {
            try {
                val branches = git.listBranches(repoDir)
                adapter.submitList(branches)
                findViewById<TextView>(R.id.tvBranchCount).text =
                    "${branches.size} branch${if (branches.size != 1) "es" else ""}"
            } catch (e: Exception) {
                Toast.makeText(this@BranchActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
            .setTitle("Delete branch '${branch.name}'?")
            .setMessage("This cannot be undone. Force-delete will remove even unmerged commits.")
            .setPositiveButton("Delete") { _, _ -> deleteBranch(branch.name, false) }
            .setNeutralButton("Force Delete") { _, _ -> deleteBranch(branch.name, true) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteBranch(name: String, force: Boolean) {
        lifecycleScope.launch {
            try {
                git.deleteBranch(repoDir, name, force)
                Toast.makeText(this@BranchActivity, "🗑️ Branch '$name' deleted", Toast.LENGTH_SHORT).show()
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

            val adapter = ArrayAdapter(this@BranchActivity, android.R.layout.simple_spinner_item, branches)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spBase.adapter   = adapter
            spTarget.adapter = adapter
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
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class BranchAdapter(
    private val onCheckout: (BranchInfo) -> Unit,
    private val onDelete:   (BranchInfo) -> Unit,
    private val onRename:   (BranchInfo) -> Unit
) : RecyclerView.Adapter<BranchAdapter.VH>() {

    private var items = listOf<BranchInfo>()

    fun submitList(list: List<BranchInfo>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName:     TextView = v.findViewById(R.id.tvBranchName)
        val tvStatus:   TextView = v.findViewById(R.id.tvBranchStatus)
        val btnCheck:   Button   = v.findViewById(R.id.btnCheckout)
        val btnRename:  Button   = v.findViewById(R.id.btnRenameBranch)
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
        holder.btnDelete.setOnClickListener { onDelete(b) }
    }
}
