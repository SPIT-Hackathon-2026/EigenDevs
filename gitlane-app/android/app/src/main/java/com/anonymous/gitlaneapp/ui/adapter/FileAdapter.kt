package com.anonymous.gitlaneapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anonymous.gitlaneapp.databinding.ItemFileBinding
import java.io.File

class FileAdapter(
    private val onClick: (File) -> Unit
) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {
 
    private var modifiedPaths: Set<String> = emptySet()
    private var repoPath: String = ""

    fun submitData(list: List<File>, modified: Set<String>, repo: String) {
        modifiedPaths = modified
        repoPath = repo
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        val relativePath = file.absolutePath.removePrefix(repoPath).removePrefix(File.separator).replace(File.separator, "/")
        val isModified = modifiedPaths.contains(relativePath)
        holder.bind(file, isModified)
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File, isModified: Boolean) {
            binding.tvFileName.text = file.name
            binding.tvFileIcon.text = if (file.isDirectory) "📁" else "📄"

            // Yellow color for modified files/folders
            val color = if (isModified) "#FACC15" else "#F8FAFC"
            binding.tvFileName.setTextColor(android.graphics.Color.parseColor(color))

            // Show chevron (›) for directories so user knows it's navigable
            binding.tvChevron.visibility = if (file.isDirectory) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onClick(file) }
        }
    }

    private class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.absolutePath == newItem.absolutePath
        override fun areContentsTheSame(oldItem: File, newItem: File) = oldItem == newItem
    }
}
