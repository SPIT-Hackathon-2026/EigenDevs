package com.anonymous.gitlaneapp.copilot

/**
 * LocalIntentClassifier
 *
 * Pattern-matches user input into a [LocalIntent] without ANY network call.
 * Simple, deterministic, instant.
 */
object LocalIntentClassifier {

    sealed class LocalIntent {
        object RepoHealth    : LocalIntent()
        object ListBranches  : LocalIntent()
        object ListCommits   : LocalIntent()
        object ListTags      : LocalIntent()
        object CurrentBranch : LocalIntent()
        object GitStatus     : LocalIntent()

        data class CreateBranch(val name: String, val checkout: Boolean = true) : LocalIntent()
        data class CheckoutBranch(val name: String) : LocalIntent()
        data class DeleteBranch(val name: String) : LocalIntent()
        data class MergeBranch(val source: String) : LocalIntent()
        data class Rebase(val upstream: String) : LocalIntent()

        data class SearchCommits(val query: String) : LocalIntent()
        data class StageAndCommit(val message: String) : LocalIntent()
        object ExplainLatestCommit : LocalIntent()

        data class CreateTag(val name: String, val message: String? = null) : LocalIntent()
        data class CreateFile(val filename: String, val content: String = "") : LocalIntent()
        
        object GenerateReadme    : LocalIntent()
        object GenerateCommitMsg : LocalIntent()
        object ExplainConflict   : LocalIntent()
        object Unknown           : LocalIntent()
    }

    fun classify(input: String): LocalIntent {
        val q = input.lowercase().trim()

        // 1. SPECIFIC REGEX ACTIONS (High Priority)
        
        // Create Branch
        val createBranchPatterns = listOf(
            Regex("create (?:a )?(?:new )?branch (?:called |named |for )?[\"']?([a-zA-Z0-9/_\\-]+)[\"']?"),
            Regex("(?:new|make) branch [\"']?([a-zA-Z0-9/_\\-]+)[\"']?"),
            Regex("branch (?:for|called|named) [\"']?([a-zA-Z0-9/_\\-]+)[\"']?"),
            Regex("(?:start|begin) (?:working on |feature )?[\"']?([a-zA-Z0-9/_\\-]+)[\"']? (?:branch|feature)")
        )
        for (pat in createBranchPatterns) {
            pat.find(q)?.let { m ->
                val name = m.groupValues[1].trim().replace(" ", "-")
                if (name.isNotBlank()) return LocalIntent.CreateBranch(name, checkout = true)
            }
        }

        // Checkout Branch
        Regex("(?:checkout|switch to|go to|change to) (?:branch )?[\"']?([a-zA-Z0-9/_\\-]+)[\"']?").find(q)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank()) return LocalIntent.CheckoutBranch(name)
        }

        // Delete Branch
        Regex("(?:delete|remove|drop) (?:branch )?[\"']?([a-zA-Z0-9/_\\-]+)[\"']?").find(q)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank() && name != "branch") return LocalIntent.DeleteBranch(name)
        }

        // Merge Branch
        Regex("merge (?:branch )?[\"']?([a-zA-Z0-9/_\\-]+)[\"']?").find(q)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank() && name !in listOf("branch", "into", "with")) return LocalIntent.MergeBranch(name)
        }

        // Rebase
        Regex("(?:rebase|interactive rebase)(?: onto| with)? [\"']?([a-zA-Z0-9/_\\-]+)[\"']?").find(q)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank() && name !in listOf("branch", "onto", "with")) return LocalIntent.Rebase(name)
        }
        if (q.contains("rebase")) return LocalIntent.Rebase("main")

        // 2. KEYWORD-BASED ACTIONS
        
        if (q.matchesAny("repo health", "health", "repo summary", "how am i doing")) return LocalIntent.RepoHealth
        
        if (q.matchesAny("current branch", "which branch", "what branch", "active branch", "branch name")) return LocalIntent.CurrentBranch
        
        if (q.matchesAny("list branches", "show branches", "all branches", "what branches")) return LocalIntent.ListBranches

        if (q.matchesAny("git status", "status", "what changed", "what's changed", "modified files", "pending changes")) return LocalIntent.GitStatus

        if (q.matchesAny("explain commit", "what changed in the last commit", "latest commit", "explain last commit", "last commit")) return LocalIntent.ExplainLatestCommit

        if (q.matchesAny("list commits", "show commits", "recent commits", "history", "git log", "commits")) return LocalIntent.ListCommits

        if (q.matchesAny("list tags", "show tags", "tags", "releases")) return LocalIntent.ListTags

        if (q.matchesAny("generate readme", "create readme", "readme")) return LocalIntent.GenerateReadme

        if (q.matchesAny("explain conflict", "merge conflict", "resolve conflict")) return LocalIntent.ExplainConflict

        if (q.matchesAny("generate commit message", "suggest commit message", "commit message")) return LocalIntent.GenerateCommitMsg

        // 3. REMAINING FALLBACKS
        
        // Commit Search
        Regex("(?:find|search|get) commits? (?:about|containing|mentioning)? ?(.+)").find(q)?.let { m ->
            val term = m.groupValues[1].trim()
            if (term.isNotBlank()) return LocalIntent.SearchCommits(term)
        }

        // Stage & Commit
        val commitMsgPat = Regex("(?:commit|stage and commit)(?: with message| message)? ?[\":']?(.*)")
        commitMsgPat.find(q)?.let { m ->
            val msg = m.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
            if (msg.isNotBlank()) return LocalIntent.StageAndCommit(msg)
        }

        return LocalIntent.Unknown
    }

    private fun String.matchesAny(vararg patterns: String): Boolean {
        return patterns.any { p ->
            this == p || this.contains(p)
        }
    }
}
