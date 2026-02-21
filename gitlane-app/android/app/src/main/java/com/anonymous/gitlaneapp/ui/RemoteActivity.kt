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
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.R
import com.anonymous.gitlaneapp.RemoteInfo
import kotlinx.coroutines.launch
import java.io.File

/**
 * RemoteActivity — Full remote management UI.
 *
 * Features:
 *  - List all remotes (name + URL)
 *  - Add remote
 *  - Edit remote URL
 *  - Remove remote
 *  - Fetch from remote
 *  - Pull (fetch + merge)
 *  - Push (normal + force)
 *  - Push specific branch
 *  - View remote refs
 */
class RemoteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
        const val EXTRA_REPO_NAME = "extra_repo_name"
    }

    private lateinit var git: GitManager
    private lateinit var creds: CredentialsManager
    private lateinit var repoDir: File
    private lateinit var adapter: RemoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: "Remotes"
        repoDir = File(repoPath)
        git   = GitManager(this)
        creds = CredentialsManager(this)

        supportActionBar?.title = "🔁 $repoName — Remotes"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = RemoteAdapter(
            onEdit   = { remote -> showEditDialog(remote) },
            onDelete = { remote -> confirmDelete(remote) },
            onFetch  = { remote -> doFetch(remote.name) },
            onPull   = { remote -> doPull(remote.name) },
            onPush   = { remote -> showPushDialog(remote.name) }
        )

        findViewById<RecyclerView>(R.id.rvRemotes).apply {
            layoutManager = LinearLayoutManager(this@RemoteActivity)
            adapter = this@RemoteActivity.adapter
        }

        findViewById<Button>(R.id.btnAddRemote).setOnClickListener { showAddRemoteDialog() }

        loadRemotes()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadRemotes() {
        lifecycleScope.launch {
            try {
                val remotes = git.listRemotes(repoDir)
                adapter.submitList(remotes)
                val tv = findViewById<TextView>(R.id.tvRemoteCount)
                tv.text = "${remotes.size} remote${if (remotes.size != 1) "s" else ""} configured"
            } catch (e: Exception) {
                setStatus("Error: ${e.message}")
            }
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { findViewById<TextView>(R.id.tvRemoteStatus).text = msg }
    }

    private fun getPat(url: String): String? = creds.getPatForUrl(url)

    // ── Operations ───────────────────────────────────────────────────────────

    private fun doFetch(remote: String) {
        setStatus("⏳ Fetching from $remote…")
        lifecycleScope.launch {
            try {
                val remotes = git.listRemotes(repoDir)
                val url = remotes.firstOrNull { it.name == remote }?.fetchUrl ?: ""
                git.fetch(repoDir, remote, getPat(url))
                setStatus("✅ Fetch complete from $remote")
                Toast.makeText(this@RemoteActivity, "Fetched from $remote", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                setStatus("❌ Fetch failed: ${e.message}")
            }
        }
    }

    private fun doPull(remote: String) {
        setStatus("⏳ Pulling from $remote…")
        lifecycleScope.launch {
            try {
                val remotes = git.listRemotes(repoDir)
                val url = remotes.firstOrNull { it.name == remote }?.fetchUrl ?: ""
                val result = git.pull(repoDir, remote, getPat(url))
                setStatus(result)
                Toast.makeText(this@RemoteActivity, result, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                setStatus("❌ Pull failed: ${e.message}")
            }
        }
    }

    private fun showPushDialog(remote: String) {
        lifecycleScope.launch {
            val branches     = git.listBranches(repoDir).map { it.name }.toTypedArray()
            val currentIdx   = branches.indexOfFirst { b ->
                git.listBranches(repoDir).firstOrNull { it.isCurrent }?.name == b
            }.coerceAtLeast(0)

            val view = layoutInflater.inflate(R.layout.dialog_push, null)
            val spBranch = view.findViewById<Spinner>(R.id.spPushBranch)
            val cbForce  = view.findViewById<CheckBox>(R.id.cbForcePush)

            val adp = ArrayAdapter(this@RemoteActivity, android.R.layout.simple_spinner_item, branches)
            adp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spBranch.adapter = adp
            spBranch.setSelection(currentIdx)

            AlertDialog.Builder(this@RemoteActivity)
                .setTitle("Push to $remote")
                .setView(view)
                .setPositiveButton("Push") { _, _ ->
                    val branch = spBranch.selectedItem as String
                    val force  = cbForce.isChecked
                    doPush(remote, branch, force)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun doPush(remote: String, branch: String, force: Boolean) {
        val label = if (force) "Force-pushing" else "Pushing"
        setStatus("⏳ $label '$branch' to $remote…")
        lifecycleScope.launch {
            try {
                val remotes = git.listRemotes(repoDir)
                val url = remotes.firstOrNull { it.name == remote }?.pushUrl ?: ""
                git.push(repoDir, remote, getPat(url), force, branch)
                setStatus("✅ Pushed '$branch' to $remote${if (force) " (force)" else ""}")
                Toast.makeText(this@RemoteActivity, "Pushed to $remote", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                setStatus("❌ Push failed: ${e.message}")
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private fun showAddRemoteDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etName = EditText(this).apply { hint = "Remote name (e.g. origin)" }
        val etUrl  = EditText(this).apply { hint = "Remote URL (https://…)" }
        container.addView(etName)
        container.addView(etUrl)

        AlertDialog.Builder(this)
            .setTitle("Add Remote")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val url  = etUrl.text.toString().trim()
                if (name.isBlank() || url.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        git.addRemote(repoDir, name, url)
                        Toast.makeText(this@RemoteActivity, "✅ Remote '$name' added", Toast.LENGTH_SHORT).show()
                        loadRemotes()
                    } catch (e: Exception) {
                        Toast.makeText(this@RemoteActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(remote: RemoteInfo) {
        val input = EditText(this).apply { setText(remote.fetchUrl) }
        AlertDialog.Builder(this)
            .setTitle("Edit URL for '${remote.name}'")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        git.editRemoteUrl(repoDir, remote.name, newUrl)
                        Toast.makeText(this@RemoteActivity, "✅ URL updated", Toast.LENGTH_SHORT).show()
                        loadRemotes()
                    } catch (e: Exception) {
                        Toast.makeText(this@RemoteActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(remote: RemoteInfo) {
        AlertDialog.Builder(this)
            .setTitle("Remove remote '${remote.name}'?")
            .setMessage("URL: ${remote.fetchUrl}")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    try {
                        git.removeRemote(repoDir, remote.name)
                        Toast.makeText(this@RemoteActivity, "🗑️ Remote '${remote.name}' removed", Toast.LENGTH_SHORT).show()
                        loadRemotes()
                    } catch (e: Exception) {
                        Toast.makeText(this@RemoteActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class RemoteAdapter(
    private val onEdit:   (RemoteInfo) -> Unit,
    private val onDelete: (RemoteInfo) -> Unit,
    private val onFetch:  (RemoteInfo) -> Unit,
    private val onPull:   (RemoteInfo) -> Unit,
    private val onPush:   (RemoteInfo) -> Unit
) : RecyclerView.Adapter<RemoteAdapter.VH>() {

    private var items = listOf<RemoteInfo>()
    fun submitList(list: List<RemoteInfo>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName:   TextView = v.findViewById(R.id.tvRemoteName)
        val tvUrl:    TextView = v.findViewById(R.id.tvRemoteUrl)
        val btnEdit:  Button   = v.findViewById(R.id.btnEditRemote)
        val btnDel:   Button   = v.findViewById(R.id.btnDeleteRemote)
        val btnFetch: Button   = v.findViewById(R.id.btnFetch)
        val btnPull:  Button   = v.findViewById(R.id.btnPull)
        val btnPush:  Button   = v.findViewById(R.id.btnPush)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_remote, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.tvName.text = r.name
        holder.tvUrl.text  = r.fetchUrl
        holder.btnEdit.setOnClickListener  { onEdit(r) }
        holder.btnDel.setOnClickListener   { onDelete(r) }
        holder.btnFetch.setOnClickListener { onFetch(r) }
        holder.btnPull.setOnClickListener  { onPull(r) }
        holder.btnPush.setOnClickListener  { onPush(r) }
    }
}
