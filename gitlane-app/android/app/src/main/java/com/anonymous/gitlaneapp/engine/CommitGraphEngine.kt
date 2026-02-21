package com.anonymous.gitlaneapp.engine

import com.anonymous.gitlaneapp.CommitInfo
import android.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * CommitGraphEngine
 * 
 * Responsible for calculating the topological layout of a Git repository.
 * Assigns lanes to branches, identifies merge/branch points, and calculates
 * visual metadata for the graph.
 */
class CommitGraphEngine {

    data class GraphNode(
        val commit: CommitInfo,
        val lane: Int,
        val children: List<String>, // Commits that have this commit as parent
        val isMerge: Boolean,
        val color: Int,
        val tags: List<String> = emptyList(),
        val branches: List<String> = emptyList()
    )

    data class LayoutResult(
        val nodes: List<GraphNode>,
        val maxLanes: Int
    )

    private val branchColors = listOf(
        0xFF64FFDA.toInt(), // GitLane Teal
        0xFF3B82F6.toInt(), // Royal Blue
        0xFFF59E0B.toInt(), // Amber
        0xFFEC4899.toInt(), // Hot Pink
        0xFF8B5CF6.toInt(), // Vibrant Violet
        0xFF10B981.toInt(), // Emerald
        0xFFF43F5E.toInt()  // Rose
    )

    /**
     * Calculates the topological layout for a list of commits.
     * Expects commits to be in reverse-chronological order (Git Log order).
     */
    fun calculateLayout(commits: List<CommitInfo>, branchData: Map<String, List<String>>): LayoutResult {
        if (commits.isEmpty()) return LayoutResult(emptyList(), 0)

        val nodes = mutableListOf<GraphNode>()
        val shaToNode = mutableMapOf<String, GraphNode>()
        val activeLanes = mutableListOf<String?>() // Stores the SHA currently occupying a lane
        
        // Map SHAs to children (for forward-traversal info)
        val childrenMap = mutableMapOf<String, MutableList<String>>()
        commits.forEach { commit ->
            commit.parents.forEach { pSha ->
                childrenMap.getOrPut(pSha) { mutableListOf() }.add(commit.sha)
            }
        }

        commits.forEach { commit ->
            // 1. Find lane for this commit
            var lane = activeLanes.indexOf(commit.sha)
            if (lane == -1) {
                // Not already tracked by a child, find an empty slot or add new lane
                lane = activeLanes.indexOf(null)
                if (lane == -1) {
                    lane = activeLanes.size
                    activeLanes.add(commit.sha)
                } else {
                    activeLanes[lane] = commit.sha
                }
            }

            // 2. Identify Metadata (Branches/Tags)
            // Note: In real JGit, we'd pass this data in.
            val currentBranches = branchData[commit.sha] ?: emptyList()

            // 3. Create Node
            val node = GraphNode(
                commit = commit,
                lane = lane,
                children = childrenMap[commit.sha] ?: emptyList(),
                isMerge = commit.parents.size > 1,
                color = branchColors[lane % branchColors.size],
                branches = currentBranches
            )
            nodes.add(node)
            shaToNode[commit.sha] = node

            // 4. Update lanes for parent commits
            activeLanes[lane] = null // Current commit is "finished" in this row
            commit.parents.forEachIndexed { idx, pSha ->
                val existingLane = activeLanes.indexOf(pSha)
                if (existingLane == -1) {
                    // Parent not tracked, take the current lane if it's first parent, else find new
                    val targetLane = if (idx == 0) {
                        lane
                    } else {
                        val empty = activeLanes.indexOf(null)
                        if (empty != -1) empty else activeLanes.size
                    }
                    
                    if (targetLane < activeLanes.size) {
                        activeLanes[targetLane] = pSha
                    } else {
                        activeLanes.add(pSha)
                    }
                }
            }
        }

        return LayoutResult(nodes, activeLanes.size)
    }
}
