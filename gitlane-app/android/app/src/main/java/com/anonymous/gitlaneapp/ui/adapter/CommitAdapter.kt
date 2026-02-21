package com.anonymous.gitlaneapp.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anonymous.gitlaneapp.CommitInfo
import com.anonymous.gitlaneapp.databinding.ItemCommitBinding
import com.anonymous.gitlaneapp.ui.view.VisualGraphView

class CommitAdapter : ListAdapter<CommitInfo, CommitAdapter.CommitViewHolder>(CommitDiffCallback()) {

    private val shaToLane = mutableMapOf<String, Int>()
    private val branchColors = listOf(
        0xFF64FFDA.toInt(), // Teal
        0xFF3B82F6.toInt(), // Blue
        0xFFF59E0B.toInt(), // Amber
        0xFFEC4899.toInt(), // Pink
        0xFF10B981.toInt(), // Emerald
        0xFF8B5CF6.toInt()  // Violet
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommitViewHolder {
        val binding = ItemCommitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommitViewHolder, position: Int) {
        holder.bind(getItem(position), position == 0)
    }

    private val commitDataMap = mutableMapOf<String, CommitLayoutData>()
    
    data class CommitLayoutData(
        val lane: Int,
        val activeLanes: List<Int>,
        val parentLanes: List<Int>
    )

    override fun submitList(list: List<CommitInfo>?) {
        super.submitList(list)
        if (list == null) return
        
        commitDataMap.clear()
        val currentActiveLanes = mutableListOf<String?>()

        list.forEach { commit ->
            // 1. Find or create lane for this commit
            var lane = currentActiveLanes.indexOf(commit.sha)
            if (lane == -1) {
                lane = currentActiveLanes.indexOf(null)
                if (lane == -1) {
                    lane = currentActiveLanes.size
                    currentActiveLanes.add(commit.sha)
                } else {
                    currentActiveLanes[lane] = commit.sha
                }
            }

            // 2. Identify active lanes (all non-null indices)
            val activeIndices = currentActiveLanes.indices.filter { currentActiveLanes[it] != null }
            
            // 3. Prepare for next commits: remove current, add parents
            currentActiveLanes[lane] = null
            val pLanes = mutableListOf<Int>()
            commit.parents.forEach { pSha ->
                var pLane = currentActiveLanes.indexOf(pSha)
                if (pLane == -1) {
                    pLane = currentActiveLanes.indexOf(null)
                    if (pLane == -1) {
                        pLane = currentActiveLanes.size
                        currentActiveLanes.add(pSha)
                    } else {
                        currentActiveLanes[pLane] = pSha
                    }
                }
                pLanes.add(pLane)
            }

            commitDataMap[commit.sha] = CommitLayoutData(lane, activeIndices, pLanes.distinct())
        }
    }

    inner class CommitViewHolder(private val binding: ItemCommitBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(commit: CommitInfo, isFirst: Boolean) {
            binding.tvCommitMsg.text  = commit.message
            binding.tvCommitSha.text  = commit.sha
            binding.tvCommitMeta.text = "${commit.author} · ${commit.date}"
            
            val layout = commitDataMap[commit.sha] ?: CommitLayoutData(0, listOf(0), emptyList())
            
            binding.graphView.setData(
                lane = layout.lane,
                activeLanes = layout.activeLanes,
                parents = layout.parentLanes,
                color = branchColors[layout.lane % branchColors.size],
                isConflict = commit.isConflict,
                isFirst = isFirst
            )
        }
    }

    private class CommitDiffCallback : DiffUtil.ItemCallback<CommitInfo>() {
        override fun areItemsTheSame(oldItem: CommitInfo, newItem: CommitInfo) = oldItem.sha == newItem.sha
        override fun areContentsTheSame(oldItem: CommitInfo, newItem: CommitInfo) = oldItem == newItem
    }
}
