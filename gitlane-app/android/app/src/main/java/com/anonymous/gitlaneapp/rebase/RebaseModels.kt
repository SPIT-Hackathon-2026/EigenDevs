package com.anonymous.gitlaneapp.rebase

import com.google.gson.annotations.SerializedName

/**
 * Supported actions for a single commit during an interactive rebase.
 */
enum class RebaseAction {
    @SerializedName("PICK") PICK,
    @SerializedName("REWORD") REWORD,
    @SerializedName("EDIT") EDIT,
    @SerializedName("SQUASH") SQUASH,
    @SerializedName("FIXUP") FIXUP,
    @SerializedName("DROP") DROP
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
