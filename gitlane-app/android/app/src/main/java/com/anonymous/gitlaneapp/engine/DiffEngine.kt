package com.anonymous.gitlaneapp.engine

import java.io.File
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.ByteArrayOutputStream

/**
 * DiffEngine
 * 
 * Handles calculating differences between commits and files.
 */
class DiffEngine(private val repo: Repository) {

    data class FileDiff(
        val path: String,
        val diffText: String,
        val linesAdded: Int,
        val linesRemoved: Int
    )

    /**
     * Get the list of files changed in a specific commit compared to its parent.
     */
    fun getCommitChanges(commit: RevCommit): List<FileDiff> {
        val out = ByteArrayOutputStream()
        val df = DiffFormatter(out)
        df.setRepository(repo)
        
        val parent = if (commit.parentCount > 0) commit.getParent(0) else null
        val entries = if (parent != null) {
            df.scan(parent.tree, commit.tree)
        } else {
            // Initial commit - diff against empty tree
            df.scan(null, commit.tree)
        }

        return entries.map { entry ->
            val path = entry.newPath ?: entry.oldPath
            out.reset()
            df.format(entry)
            val fullDiff = out.toString()
            
            // Basic metric parsing
            val added = fullDiff.lines().count { it.startsWith("+") && !it.startsWith("+++") }
            val removed = fullDiff.lines().count { it.startsWith("-") && !it.startsWith("---") }
            
            FileDiff(path, fullDiff, added, removed)
        }
    }
}
