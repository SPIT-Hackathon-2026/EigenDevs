package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.databinding.ActivityFileEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FileEditorActivity
 *
 * simple editor for viewing and editing files in a repository.
 */
class FileEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_REPO_PATH = "extra_repo_path"
    }

    private lateinit var binding: ActivityFileEditorBinding
    private lateinit var git: GitManager
    private lateinit var fileToEdit: File
    private lateinit var repoDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run { finish(); return }
        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        
        fileToEdit = File(filePath)
        repoDir = File(repoPath)
        git = GitManager(this)

        // Custom header
        supportActionBar?.hide()
        binding.tvEditorFileName.text = fileToEdit.name
        binding.btnBackEditor.setOnClickListener { finish() }

        loadFileContent()

        binding.btnSave.setOnClickListener {
            saveFile()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnAcceptCurrent.setOnClickListener  { resolveConflicts("CURRENT") }
        binding.btnAcceptIncoming.setOnClickListener { resolveConflicts("INCOMING") }
        binding.btnAcceptBoth.setOnClickListener     { resolveConflicts("BOTH") }
    }

    private fun checkConflicts() {
        val content = binding.etFileContent.text.toString()
        val hasMarkers = content.contains("<<<<<<<") && content.contains("=======") && content.contains(">>>>>>>")
        binding.llConflictActions.visibility = if (hasMarkers) View.VISIBLE else View.GONE
    }

    private fun resolveConflicts(type: String) {
        val currentContent = binding.etFileContent.text.toString()
        val resolved = resolveGitConflict(currentContent, type)
        binding.etFileContent.setText(resolved)
        binding.llConflictActions.visibility = View.GONE
        Toast.makeText(this, "✅ Conflict resolved ($type)", Toast.LENGTH_SHORT).show()
    }

    private fun resolveGitConflict(content: String, type: String): String {
        val lines = content.lines()
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("<<<<<<<")) {
                val current = mutableListOf<String>()
                val incoming = mutableListOf<String>()
                
                i++
                // Read current (HEAD)
                while (i < lines.size && !lines[i].startsWith("=======")) {
                    current.add(lines[i])
                    i++
                }
                
                i++
                // Read incoming
                while (i < lines.size && !lines[i].startsWith(">>>>>>>")) {
                    incoming.add(lines[i])
                    i++
                }
                
                // Decide what to add
                when (type) {
                    "CURRENT"  -> result.addAll(current)
                    "INCOMING" -> result.addAll(incoming)
                    "BOTH"     -> {
                        result.addAll(current)
                        result.addAll(incoming)
                    }
                }
            } else {
                result.add(line)
            }
            i++
        }
        return result.joinToString("\n")
    }

    private fun loadFileContent() {
        lifecycleScope.launch {
            try {
                val extension = fileToEdit.extension.lowercase()
                val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

                if (extension in imageExtensions) {
                    // ... (image logic) ...
                    binding.tilFileContent.visibility = View.GONE
                    binding.ivFileImage.visibility = View.VISIBLE
                    binding.btnSave.visibility = View.GONE 
                    
                    val bitmap = withContext(Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeFile(fileToEdit.absolutePath)
                    }
                    if (bitmap != null) {
                        binding.ivFileImage.setImageBitmap(bitmap)
                    }
                } else {
                    binding.tilFileContent.visibility = View.VISIBLE
                    binding.ivFileImage.visibility = View.GONE
                    binding.btnSave.visibility = View.VISIBLE
                    
                    val relativePath = fileToEdit.absolutePath.removePrefix(repoDir.absolutePath).removePrefix("/")
                    val content = withContext(Dispatchers.IO) { git.readFile(repoDir, relativePath) }
                    binding.etFileContent.setText(content)
                    
                    checkConflicts()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FileEditorActivity, "❌ Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun saveFile() {
        val content = binding.etFileContent.text.toString()
        lifecycleScope.launch {
            try {
                val relativePath = fileToEdit.absolutePath.removePrefix(repoDir.absolutePath).removePrefix("/")
                git.writeFile(repoDir, relativePath, content)
                Toast.makeText(this@FileEditorActivity, "✅ Saved", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@FileEditorActivity, "❌ Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
