package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.R
import kotlinx.coroutines.launch
import java.io.File

/**
 * ConflictResolutionActivity
 *
 * Lists all files with merge conflicts and lets user open them in the editor.
 * Once a file is edited (markers removed) and saved, it should be staged.
 */
class ConflictResolutionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
    }

    private lateinit var git: GitManager
    private lateinit var repoDir: File
    private lateinit var adapter: ConflictAdapter

    private val editLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        loadConflicts()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conflict_resolution)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        repoDir = File(repoPath)
        git = GitManager(this)

        supportActionBar?.title = "Resolve Merge Conflicts"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = ConflictAdapter { file ->
            val intent = Intent(this, MergeConflictActivity::class.java).apply {
                putExtra(MergeConflictActivity.EXTRA_FILE_PATH, file.absolutePath)
                putExtra(MergeConflictActivity.EXTRA_REPO_PATH, repoDir.absolutePath)
            }
            editLauncher.launch(intent)
        }

        findViewById<RecyclerView>(R.id.rvConflicts).apply {
            layoutManager = LinearLayoutManager(this@ConflictResolutionActivity)
            adapter = this@ConflictResolutionActivity.adapter
        }

        findViewById<Button>(R.id.btnAbortMerge).setOnClickListener {
            abortMerge()
        }

        loadConflicts()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            checkIfDone()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadConflicts() {
        lifecycleScope.launch {
            val status = git.getStatus(repoDir)
            val conflictFiles = status.conflicting.map { File(repoDir, it) }
            adapter.submitList(conflictFiles)

            if (conflictFiles.isEmpty()) {
                Toast.makeText(this@ConflictResolutionActivity, "✅ All conflicts resolved! Stage and commit now.", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun abortMerge() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Abort Merge?")
            .setMessage("This will undo the merge and reset all changes. Are you sure?")
            .setPositiveButton("Abort") { _, _ ->
                lifecycleScope.launch {
                    git.abortMerge(repoDir)
                    setResult(RESULT_OK)
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkIfDone() {
        lifecycleScope.launch {
            val status = git.getStatus(repoDir)
            if (status.conflicting.isEmpty()) {
                setResult(RESULT_OK)
                finish()
            } else {
                finish()
            }
        }
    }
}

class ConflictAdapter(private val onResolve: (File) -> Unit) : RecyclerView.Adapter<ConflictAdapter.VH>() {
    private var items = listOf<File>()
    fun submitList(list: List<File>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvFileName)
        val btnResolve: Button = v.findViewById(R.id.btnResolveFile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_conflict, parent, false))

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = items[position]
        holder.tvName.text = f.name
        holder.btnResolve.setOnClickListener { onResolve(f) }
    }
}
