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

        supportActionBar?.title = fileToEdit.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadFileContent()

        binding.btnSave.setOnClickListener {
            saveFile()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadFileContent() {
        lifecycleScope.launch {
            try {
                val extension = fileToEdit.extension.lowercase()
                val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

                if (extension in imageExtensions) {
                    // Show Image
                    binding.tilFileContent.visibility = View.GONE
                    binding.ivFileImage.visibility = View.VISIBLE
                    binding.btnSave.visibility = View.GONE // Can't edit images yet
                    
                    val bitmap = withContext(Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeFile(fileToEdit.absolutePath)
                    }
                    if (bitmap != null) {
                        binding.ivFileImage.setImageBitmap(bitmap)
                    } else {
                        Toast.makeText(this@FileEditorActivity, "❌ Could not load image", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Show Text
                    binding.tilFileContent.visibility = View.VISIBLE
                    binding.ivFileImage.visibility = View.GONE
                    binding.btnSave.visibility = View.VISIBLE
                    
                    val relativePath = fileToEdit.absolutePath.removePrefix(repoDir.absolutePath).removePrefix("/")
                    val content = withContext(Dispatchers.IO) { git.readFile(repoDir, relativePath) }
                    binding.etFileContent.setText(content)
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
