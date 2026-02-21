package com.anonymous.gitlaneapp.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anonymous.gitlaneapp.CommitInfo
import com.anonymous.gitlaneapp.databinding.ItemCommitBinding

class CommitAdapter : ListAdapter<CommitInfo, CommitAdapter.CommitViewHolder>(CommitDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommitViewHolder {
        val binding = ItemCommitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommitViewHolder(private val binding: ItemCommitBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(commit: CommitInfo) {
            binding.tvCommitMsg.text  = commit.message
            binding.tvCommitSha.text  = commit.sha
            binding.tvCommitMeta.text = "${commit.author} · ${commit.date}"
        }
    }

    private class CommitDiffCallback : DiffUtil.ItemCallback<CommitInfo>() {
        override fun areItemsTheSame(oldItem: CommitInfo, newItem: CommitInfo) = oldItem.sha == newItem.sha
        override fun areContentsTheSame(oldItem: CommitInfo, newItem: CommitInfo) = oldItem == newItem
    }
}
