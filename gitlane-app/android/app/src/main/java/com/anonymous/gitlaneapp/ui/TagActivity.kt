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
import com.anonymous.gitlaneapp.TagInfo
import kotlinx.coroutines.launch
import java.io.File

/**
 * TagActivity — Full Tag management UI (CRUD + Push).
 */
class TagActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
        const val EXTRA_REPO_NAME = "extra_repo_name"
    }

    private lateinit var git: GitManager
    private lateinit var creds: CredentialsManager
    private lateinit var repoDir: File
    private lateinit var adapter: TagAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: "Tags"
        repoDir = File(repoPath)
        git = GitManager(this)
        creds = CredentialsManager(this)

        supportActionBar?.title = "🏷️ $repoName — Tags"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = TagAdapter(
            onPush   = { tag -> showPushDialog(tag) },
            onDelete = { tag -> confirmDelete(tag) }
        )

        findViewById<RecyclerView>(R.id.rvTags).apply {
            layoutManager = LinearLayoutManager(this@TagActivity)
            adapter = this@TagActivity.adapter
        }

        findViewById<Button>(R.id.btnNewTag).setOnClickListener { showCreateTagDialog() }

        loadTags()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadTags() {
        lifecycleScope.launch {
            try {
                val tags = git.listTags(repoDir)
                adapter.submitList(tags)
                findViewById<TextView>(R.id.tvTagCount).text =
                    "${tags.size} tag${if (tags.size != 1) "s" else ""}"
            } catch (e: Exception) {
                Toast.makeText(this@TagActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCreateTagDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etName = EditText(this).apply { hint = "Tag name (e.g. v1.0)" }
        val etMsg  = EditText(this).apply { hint = "Message (optional, makes it an annotated tag)" }
        container.addView(etName)
        container.addView(etMsg)

        AlertDialog.Builder(this)
            .setTitle("Create New Tag")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                val msg  = etMsg.text.toString().trim().ifBlank { null }
                if (name.isBlank()) return@setPositiveButton
                
                lifecycleScope.launch {
                    try {
                        git.createTag(repoDir, name, msg)
                        Toast.makeText(this@TagActivity, "✅ Tag '$name' created", Toast.LENGTH_SHORT).show()
                        loadTags()
                    } catch (e: Exception) {
                        Toast.makeText(this@TagActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(tag: TagInfo) {
        AlertDialog.Builder(this)
            .setTitle("Delete tag '${tag.name}'?")
            .setMessage("This will remove the tag locally.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        git.deleteTag(repoDir, tag.name)
                        Toast.makeText(this@TagActivity, "🗑️ Tag '${tag.name}' deleted", Toast.LENGTH_SHORT).show()
                        loadTags()
                    } catch (e: Exception) {
                        Toast.makeText(this@TagActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPushDialog(tag: TagInfo) {
        lifecycleScope.launch {
            val remotes = git.listRemotes(repoDir)
            if (remotes.isEmpty()) {
                Toast.makeText(this@TagActivity, "No remotes configured", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val remoteNames = remotes.map { it.name }.toTypedArray()
            var selectedRemote = remoteNames[0]

            AlertDialog.Builder(this@TagActivity)
                .setTitle("Push tag '${tag.name}'")
                .setSingleChoiceItems(remoteNames, 0) { _, which -> selectedRemote = remoteNames[which] }
                .setPositiveButton("Push") { _, _ ->
                    pushTag(tag.name, selectedRemote)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun pushTag(tagName: String, remoteName: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@TagActivity, "⏳ Pushing tag...", Toast.LENGTH_SHORT).show()
                val remoteUrl = git.listRemotes(repoDir).find { it.name == remoteName }?.pushUrl ?: ""
                val pat = creds.getPatForUrl(remoteUrl)
                
                git.pushTag(repoDir, remoteName, tagName, pat)
                Toast.makeText(this@TagActivity, "✅ Tag '$tagName' pushed to $remoteName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@TagActivity, "❌ Push failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class TagAdapter(
    private val onPush:   (TagInfo) -> Unit,
    private val onDelete: (TagInfo) -> Unit
) : RecyclerView.Adapter<TagAdapter.VH>() {

    private var items = listOf<TagInfo>()

    fun submitList(list: List<TagInfo>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName:   TextView = v.findViewById(R.id.tvTagName)
        val tvSha:    TextView = v.findViewById(R.id.tvTagSha)
        val tvDate:   TextView = v.findViewById(R.id.tvTagDate)
        val btnPush:   Button   = v.findViewById(R.id.btnPushTag)
        val btnDelete: Button   = v.findViewById(R.id.btnDeleteTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tag, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.tvName.text = t.name
        holder.tvSha.text  = t.sha
        holder.tvDate.text = "Created on ${t.date}"
        
        holder.btnPush.setOnClickListener   { onPush(t) }
        holder.btnDelete.setOnClickListener { onDelete(t) }
    }
}
