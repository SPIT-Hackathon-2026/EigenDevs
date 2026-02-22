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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            binding.tvFileName.text = file.name
            binding.tvFileIcon.text = if (file.isDirectory) "📁" else "📄"

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
