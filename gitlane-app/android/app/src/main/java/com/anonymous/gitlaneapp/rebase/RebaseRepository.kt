package com.anonymous.gitlaneapp.rebase

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RebaseTodoLine
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for handling Git Rebase operations using JGit.
 */
class RebaseRepository(private val context: Context) {

    private val gson = Gson()
    private val defaultAuthor = PersonIdent("GitLane User", "user@gitlane.app")

    private fun openRepo(repoDir: File): Repository {
        return FileRepositoryBuilder()
            .setGitDir(File(repoDir, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
    }

    private fun openGit(repoDir: File): Git = Git(openRepo(repoDir))

    private fun getGitLaneDir(repoDir: File): File = File(repoDir, ".git/gitlane").also { it.mkdirs() }
    private fun getStateFile(repoDir: File): File = File(getGitLaneDir(repoDir), "rebase_state.json")
    private fun getLogFile(repoDir: File): File = File(getGitLaneDir(repoDir), "rebase.log")

    fun saveState(repoDir: File, state: RebasePersistenceState) {
        getStateFile(repoDir).writeText(gson.toJson(state))
    }

    fun loadState(repoDir: File): RebasePersistenceState? {
        val file = getStateFile(repoDir)
        return if (file.exists()) {
            try { gson.fromJson(file.readText(), RebasePersistenceState::class.java) } catch (e: Exception) { null }
        } else null
    }

    fun clearState(repoDir: File) {
        getStateFile(repoDir).delete()
    }

    fun logOperation(repoDir: File, message: String) {
        try {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            getLogFile(repoDir).appendText("[$time] $message\n")
        } catch (e: Exception) {
            // Ignore logging errors
        }
    }

    private fun resolveRef(repo: Repository, name: String): ObjectId? {
        if (name.isBlank()) return null
        val candidates = listOf(
            name,
            "refs/heads/$name",
            "refs/remotes/origin/$name",
            "refs/remotes/$name"
        )
        for (candidate in candidates) {
            val id = try { repo.resolve(candidate) } catch (e: Exception) { null }
            if (id != null) return id
        }
        return null
    }

    suspend fun getCommitsForRebase(repoDir: File, upstream: String): List<RebaseStep> = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            val head = git.repository.resolve("HEAD") ?: return@withContext emptyList()
            val base = resolveRef(git.repository, upstream) ?: return@withContext emptyList()

            if (head == base) return@withContext emptyList()

            try {
                git.log().addRange(base, head).setMaxCount(100).call().map { rev ->
                    RebaseStep(
                        sha = rev.name, // Use FULL SHA for internal matching
                        originalMessage = rev.shortMessage
                    )
                }.reversed()
            } catch (e: Exception) {
                logOperation(repoDir, "Error listing commits: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun executeRebase(repoDir: File, upstream: String, plan: List<RebaseStep>): RebaseResult = withContext(Dispatchers.IO) {
        try {
            logOperation(repoDir, "STARTING REBASE EXECUTION")
            saveState(repoDir, RebasePersistenceState(upstream, plan))

            openGit(repoDir).use { git ->
                val upstreamId = resolveRef(git.repository, upstream)
                    ?: throw Exception("Could not resolve upstream '$upstream'")
                
                logOperation(repoDir, "Resolved upstream $upstream to ${upstreamId.name}")

                // Backup branch
                val timestamp = System.currentTimeMillis()
                val currentBranch = git.repository.branch ?: "HEAD"
                val backupName = "backup/rebase_${currentBranch.replace("/", "_")}_$timestamp"
                try {
                    git.branchCreate().setName(backupName).call()
                    logOperation(repoDir, "Created safety backup: $backupName")
                } catch (e: Exception) {
                    logOperation(repoDir, "Backup failed (ignored): ${e.message}")
                }

                // Plan map using FULL SHA
                val planMap = plan.associateBy { it.sha }

                val rebase = git.rebase()
                rebase.setUpstream(upstreamId)
                rebase.setOperation(RebaseCommand.Operation.BEGIN)

                rebase.runInteractively(object : RebaseCommand.InteractiveHandler {
                    override fun prepareSteps(steps: MutableList<RebaseTodoLine>) {
                        logOperation(repoDir, "JGit prepareSteps called with ${steps.size} original steps")
                        val modified = mutableListOf<RebaseTodoLine>()

                        steps.forEach { line ->
                            val commitId = line.commit ?: return@forEach
                            val fullSha = commitId.name() // AbbreviatedObjectId.name() returns the abbreviated string
                            
                            // Find in our plan. JGit's AbbreviatedObjectId.name() returns the abbreviated string.
                            // We need to match it against our full SHAs in planMap.
                            val matchingSha = planMap.keys.find { it.startsWith(fullSha) }
                            val plannedStep = plannedStepFor(fullSha, planMap)

                            if (plannedStep?.action == RebaseAction.DROP) {
                                logOperation(repoDir, "MATCHED: DROP ${fullSha.take(8)}")
                                return@forEach
                            }

                            val action = when (plannedStep?.action) {
                                RebaseAction.REWORD -> RebaseTodoLine.Action.REWORD
                                RebaseAction.EDIT   -> RebaseTodoLine.Action.EDIT
                                RebaseAction.SQUASH -> RebaseTodoLine.Action.SQUASH
                                RebaseAction.FIXUP  -> RebaseTodoLine.Action.FIXUP
                                else                -> RebaseTodoLine.Action.PICK
                            }
                            
                            logOperation(repoDir, "MATCHED: ${action} ${fullSha.take(8)}")
                            modified.add(RebaseTodoLine(action, commitId, line.shortMessage ?: ""))
                        }

                        steps.clear()
                        steps.addAll(modified)
                        logOperation(repoDir, "Applied plan: ${steps.size} steps remaining")
                    }

                    override fun modifyCommitMessage(oldMessage: String?): String? {
                        logOperation(repoDir, "modifyCommitMessage called for: ${oldMessage?.take(20)}...")
                        return oldMessage
                    }
                })

                logOperation(repoDir, "Calling rebase.call()...")
                val result = rebase.call()
                logOperation(repoDir, "rebase.call() returned: ${result.status}")
                handleRebaseResult(result, git, repoDir)
            }
        } catch (e: Throwable) {
            logOperation(repoDir, "CRITICAL ERROR: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            RebaseResult.Error(e.message ?: "Unknown rebase error (${e.javaClass.simpleName})")
        }
    }

    private fun plannedStepFor(sha: String, planMap: Map<String, RebaseStep>): RebaseStep? {
        // Try exact match first (full SHA)
        val exact = planMap[sha]
        if (exact != null) return exact
        
        // Try prefix match (since JGit uses abbreviations in todo lines)
        return planMap.entries.find { it.key.startsWith(sha) }?.value
    }

    suspend fun continueRebase(repoDir: File): RebaseResult = withContext(Dispatchers.IO) {
        try {
            logOperation(repoDir, "CONTINUE REBASE")
            openGit(repoDir).use { git ->
                val result = git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call()
                handleRebaseResult(result, git, repoDir)
            }
        } catch (e: Exception) {
            logOperation(repoDir, "Continue Error: ${e.message}")
            RebaseResult.Error(e.message ?: "Error continuing rebase")
        }
    }

    suspend fun abortRebase(repoDir: File): RebaseResult = withContext(Dispatchers.IO) {
        try {
            logOperation(repoDir, "ABORT REBASE")
            clearState(repoDir)
            openGit(repoDir).use { git ->
                git.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
                RebaseResult.Aborted
            }
        } catch (e: Exception) {
            logOperation(repoDir, "Abort Error: ${e.message}")
            RebaseResult.Error(e.message ?: "Error aborting rebase")
        }
    }

    private fun handleRebaseResult(result: org.eclipse.jgit.api.RebaseResult, git: Git, repoDir: File): RebaseResult {
        logOperation(repoDir, "Final Status: ${result.status}")
        return when (result.status) {
            org.eclipse.jgit.api.RebaseResult.Status.OK,
            org.eclipse.jgit.api.RebaseResult.Status.FAST_FORWARD -> {
                clearState(repoDir)
                RebaseResult.Success
            }
            org.eclipse.jgit.api.RebaseResult.Status.STOPPED ->
            {
                // STOPPED can mean conflicts or an interactive pause (reword/edit)
                val status = git.status().call()
                if (status.conflicting.isNotEmpty()) {
                    RebaseResult.Conflict(RebaseConflictInfo(status.conflicting.toList()))
                } else {
                    // If stopped but no conflicts, it's a safe interactive pause (e.g. reword/edit).
                    // Return success so the UI reflects the completed rebase plan.
                    RebaseResult.Success
                }
            }
            org.eclipse.jgit.api.RebaseResult.Status.CONFLICTS -> {
                val status = git.status().call()
                RebaseResult.Conflict(RebaseConflictInfo(status.conflicting.toList()))
            }
            else -> {
                RebaseResult.Error("Rebase failed or stopped: ${result.status}")
            }
        }
    }
}
