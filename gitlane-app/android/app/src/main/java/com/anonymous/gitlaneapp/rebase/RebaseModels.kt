package com.anonymous.gitlaneapp.rebase

import com.google.gson.annotations.SerializedName

/**
 * Supported actions for a single commit during an interactive rebase.
 */
enum class RebaseAction(val description: String, val helper: String) {
    @SerializedName("PICK") 
    PICK("Keep", "Use the commit as-is."),
    
    @SerializedName("REWORD") 
    REWORD("Reword", "Use commit, but edit the commit message."),
    
    @SerializedName("EDIT") 
    EDIT("Edit", "Stop for amending (not yet fully supported in UI)."),
    
    @SerializedName("SQUASH") 
    SQUASH("Squash", "Meld into previous commit and combine messages."),
    
    @SerializedName("FIXUP") 
    FIXUP("Fixup", "Meld into previous commit, discarding this message."),
    
    @SerializedName("DROP") 
    DROP("Drop", "Remove this commit entirely.")
}

/**
 * Represents a single step in an interactive rebase plan.
 */
data class RebaseStep(
    val sha: String,
    val originalMessage: String,
    var action: RebaseAction = RebaseAction.PICK,
    var newMessage: String? = null
)

/**
 * Information about a conflict encountered during rebase.
 */
data class RebaseConflictInfo(
    val conflictedFiles: List<String>
)

/**
 * Result of a rebase operation.
 */
sealed class RebaseResult {
    object Success : RebaseResult()
    data class Conflict(val info: RebaseConflictInfo) : RebaseResult()
    data class Error(val message: String) : RebaseResult()
    object Aborted : RebaseResult()
}

/**
 * Persistence model for rebase state.
 */
data class RebasePersistenceState(
    val upstream: String,
    val plan: List<RebaseStep>,
    val timestamp: Long = System.currentTimeMillis()
)
