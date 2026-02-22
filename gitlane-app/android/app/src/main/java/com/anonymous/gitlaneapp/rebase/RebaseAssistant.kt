package com.anonymous.gitlaneapp.rebase

/**
 * AI Assistant for suggesting optimizations during an interactive rebase.
 */
class RebaseAssistant {

    /**
     * Analyzes a rebase plan and suggests actions to clean up history.
     */
    fun suggestOptimizations(commits: List<RebaseStep>): List<RebaseStep> {
        return commits.mapIndexed { index, step ->
            val msg = step.originalMessage.lowercase()
            
            val suggestedAction = when {
                msg.contains("fixup!") || msg.startsWith("fixup!") -> RebaseAction.FIXUP
                msg.contains("squash!") || msg.startsWith("squash!") -> RebaseAction.SQUASH
                msg.contains("temp") || msg.contains("debug") || msg.contains("test commit") -> RebaseAction.DROP
                step.originalMessage.length < 5 || step.originalMessage.split(" ").size < 2 -> RebaseAction.REWORD
                else -> step.action
            }
            step.copy(action = suggestedAction)
        }
    }

    fun explainSuggestions(plan: List<RebaseStep>): String {
        val explanations = mutableListOf<String>()
        val fixups = plan.count { it.action == RebaseAction.FIXUP || it.action == RebaseAction.SQUASH }
        val drops = plan.count { it.action == RebaseAction.DROP }
        val rewords = plan.count { it.action == RebaseAction.REWORD }

        if (fixups > 0) explanations.add("• Combined $fixups fixup/squash commits to keep history concise.")
        if (drops > 0) explanations.add("• Removed $drops temporary or debug commits to keep the main branch clean.")
        if (rewords > 0) explanations.add("• Suggested rewording $rewords commits with cryptic or short messages.")
        
        return if (explanations.isEmpty()) "No optimizations suggested. Your history looks clean!" 
               else "AI Suggestions:\n" + explanations.joinToString("\n")
    }
}
