package com.anonymous.gitlaneapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GitManager
 *
 * Central JGit wrapper for GitLane. Handles:
 *  - Local repo lifecycle (init, rename, delete, duplicate)
 *  - File I/O
 *  - Commits & log
 *  - Branch management (create, delete, checkout, rename, ahead/behind)
 *  - Remote management (add, remove, edit, fetch, pull, push, force-push)
 *
 * All operations are suspend functions — call them from a coroutine scope.
 * Repos are stored at: <filesDir>/GitLane/<repoName>/
 */
class GitManager(context: Context) {

    val gitLaneRoot: File = File(context.filesDir, "GitLane").also { it.mkdirs() }
    private val defaultAuthor = PersonIdent("GitLane User", "user@gitlane.app")

    // ═══════════════════════════════════════════════════════════════════════════
    // REPO MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun listRepos(): List<File> = withContext(Dispatchers.IO) {
        gitLaneRoot.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /** Create + git init a new local repo (default branch = main). */
    suspend fun initRepo(repoName: String): File = withContext(Dispatchers.IO) {
        val repoDir = File(gitLaneRoot, repoName)
        repoDir.mkdirs()
        Git.init().setDirectory(repoDir).setInitialBranch("main").call().close()
        repoDir
    }

    /** Delete a repo directory entirely. */
    suspend fun deleteRepo(repoDir: File) = withContext(Dispatchers.IO) {
        repoDir.deleteRecursively()
    }

    /** Rename a repo (moves the directory). Returns new File. */
    suspend fun renameRepo(repoDir: File, newName: String): File = withContext(Dispatchers.IO) {
        val target = File(gitLaneRoot, newName)
        if (target.exists()) throw IllegalArgumentException("A repo named '$newName' already exists")
        repoDir.renameTo(target)
        target
    }

    /** Duplicate a repo (full copy of the directory tree). */
    suspend fun duplicateRepo(repoDir: File): File = withContext(Dispatchers.IO) {
        var targetName = "${repoDir.name}_copy"
        var target = File(gitLaneRoot, targetName)
        var n = 1
        while (target.exists()) {
            targetName = "${repoDir.name}_copy$n"
            target = File(gitLaneRoot, targetName)
            n++
        }
        repoDir.copyRecursively(target)
        target
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun listFiles(repoDir: File): List<File> = withContext(Dispatchers.IO) {
        repoDir.listFiles { f -> f.name != ".git" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: emptyList()
    }

    suspend fun writeFile(repoDir: File, filename: String, content: String) =
        withContext(Dispatchers.IO) { File(repoDir, filename).writeText(content, Charsets.UTF_8) }

    suspend fun readFile(repoDir: File, filename: String): String =
        withContext(Dispatchers.IO) { File(repoDir, filename).readText(Charsets.UTF_8) }

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE GIT OPS
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun stageAll(repoDir: File) = withContext(Dispatchers.IO) {
        openGit(repoDir).use { it.add().addFilepattern(".").call() }
    }

    suspend fun commit(repoDir: File, message: String): String = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            val commit = git.commit()
                .setMessage(message)
                .setAuthor(defaultAuthor)
                .setCommitter(defaultAuthor)
                .call()
            commit.abbreviate(8).name()
        }
    }

    suspend fun getLog(repoDir: File): List<CommitInfo> = withContext(Dispatchers.IO) {
        try {
            openGit(repoDir).use { git ->
                git.log().call().map { rev ->
                    CommitInfo(
                        sha     = rev.abbreviate(8).name(),
                        message = rev.shortMessage,
                        author  = rev.authorIdent.name,
                        date    = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                            .format(Date(rev.commitTime * 1000L))
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun currentBranch(repoDir: File): String = withContext(Dispatchers.IO) {
        try { openRepo(repoDir).use { it.branch ?: "main" } } catch (e: Exception) { "main" }
    }

    suspend fun commitCount(repoDir: File): Int = withContext(Dispatchers.IO) {
        try { openGit(repoDir).use { it.log().call().count() } } catch (e: Exception) { 0 }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BRANCH MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /** List all local branches. Returns branch names (without refs/heads/ prefix). */
    suspend fun listBranches(repoDir: File): List<BranchInfo> = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            val current = git.repository.branch ?: "main"
            git.branchList().call().map { ref ->
                val name = ref.name.removePrefix("refs/heads/")
                val tracking = try {
                    BranchTrackingStatus.of(git.repository, name)
                } catch (e: Exception) { null }
                BranchInfo(
                    name      = name,
                    isCurrent = name == current,
                    ahead     = tracking?.aheadCount ?: 0,
                    behind    = tracking?.behindCount ?: 0,
                    upstream  = tracking?.remoteTrackingBranch?.removePrefix("refs/remotes/")
                )
            }
        }
    }

    /** List all remote-tracking branches. */
    suspend fun listRemoteBranches(repoDir: File): List<String> = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            git.branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call()
                .map { it.name.removePrefix("refs/remotes/") }
        }
    }

    /** Create a new local branch (does NOT checkout). */
    suspend fun createBranch(repoDir: File, branchName: String) = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            git.branchCreate().setName(branchName).call()
        }
    }

    /** Checkout an existing branch. */
    suspend fun checkoutBranch(repoDir: File, branchName: String) = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            git.checkout().setName(branchName).call()
        }
    }

    /** Create AND checkout a new branch in one step. */
    suspend fun createAndCheckoutBranch(repoDir: File, branchName: String) =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                git.checkout().setCreateBranch(true).setName(branchName).call()
            }
        }

    /** Delete a local branch. Pass force=true to delete even if unmerged. */
    suspend fun deleteBranch(repoDir: File, branchName: String, force: Boolean = false) =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                git.branchDelete()
                    .setBranchNames(branchName)
                    .setForce(force)
                    .call()
            }
        }

    /** Rename a local branch. */
    suspend fun renameBranch(repoDir: File, oldName: String, newName: String) =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                git.branchRename().setOldName(oldName).setNewName(newName).call()
            }
        }

    /**
     * Compare two branches: returns the number of commits in [target] that are
     * not in [base] (ahead) and vice-versa (behind).
     */
    suspend fun compareBranches(
        repoDir: File,
        base: String,
        target: String
    ): AheadBehind = withContext(Dispatchers.IO) {
        openRepo(repoDir).use { repo ->
            val baseId   = repo.resolve("refs/heads/$base")   ?: return@withContext AheadBehind(0, 0)
            val targetId = repo.resolve("refs/heads/$target") ?: return@withContext AheadBehind(0, 0)
            RevWalk(repo).use { walk ->
                val baseCommit   = walk.parseCommit(baseId)
                val targetCommit = walk.parseCommit(targetId)
                walk.reset()
                var ahead  = 0
                var behind = 0
                walk.markStart(targetCommit)
                walk.markUninteresting(baseCommit)
                walk.forEach { ahead++ }
                walk.reset()
                walk.markStart(baseCommit)
                walk.markUninteresting(targetCommit)
                walk.forEach { behind++ }
                AheadBehind(ahead, behind)
            }
        }
    }

    /** Set the upstream tracking branch for a local branch. */
    suspend fun setUpstream(repoDir: File, localBranch: String, remote: String, remoteBranch: String) =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                git.branchCreate()
                    .setName(localBranch)
                    .setUpstreamMode(
                        org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM
                    )
                    .setStartPoint("$remote/$remoteBranch")
                    .setForce(true)
                    .call()
            }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOTE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /** List all configured remotes. */
    suspend fun listRemotes(repoDir: File): List<RemoteInfo> = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            RemoteConfig.getAllRemoteConfigs(git.repository.config).map { rc ->
                RemoteInfo(
                    name      = rc.name,
                    fetchUrl  = rc.getURIs().firstOrNull()?.toString() ?: "",
                    pushUrl   = rc.getPushURIs().firstOrNull()?.toString()
                        ?: rc.getURIs().firstOrNull()?.toString() ?: ""
                )
            }
        }
    }

    /** Add a new remote. */
    suspend fun addRemote(repoDir: File, name: String, url: String) =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                git.remoteAdd().setName(name).setUri(URIish(url)).call()
            }
        }

    /** Remove a remote. */
    suspend fun removeRemote(repoDir: File, name: String) = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            git.remoteRemove().setRemoteName(name).call()
        }
    }

    /** Change the URL of an existing remote. */
    suspend fun editRemoteUrl(repoDir: File, remoteName: String, newUrl: String) =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                git.remoteSetUrl()
                    .setRemoteName(remoteName)
                    .setRemoteUri(URIish(newUrl))
                    .call()
            }
        }

    /** Fetch all refs from a remote. */
    suspend fun fetch(repoDir: File, remote: String = "origin", pat: String? = null) =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                val cmd = git.fetch().setRemote(remote)
                if (!pat.isNullOrBlank()) {
                    cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(pat, ""))
                }
                cmd.call()
            }
        }

    /**
     * Pull (fetch + merge) current branch from remote.
     * Returns a human-readable result message.
     */
    suspend fun pull(repoDir: File, remote: String = "origin", pat: String? = null): String =
        withContext(Dispatchers.IO) {
            openGit(repoDir).use { git ->
                val cmd = git.pull().setRemote(remote)
                if (!pat.isNullOrBlank()) {
                    cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(pat, ""))
                }
                val result = cmd.call()
                when {
                    result.isSuccessful -> "✅ Pull successful (${result.mergeResult?.mergeStatus ?: "up-to-date"})"
                    else                -> "⚠️ Pull: ${result.mergeResult?.mergeStatus}"
                }
            }
        }

    /**
     * Push current branch to remote.
     * @param force  If true, performs a force-push (--force).
     */
    suspend fun push(
        repoDir: File,
        remote: String = "origin",
        pat: String? = null,
        force: Boolean = false,
        branch: String? = null
    ) = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            val cmd = git.push()
                .setRemote(remote)
                .setForce(force)
            if (!pat.isNullOrBlank()) {
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(pat, ""))
            }
            if (branch != null) {
                cmd.setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
            }
            cmd.call()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun openRepo(dir: File): Repository =
        FileRepositoryBuilder()
            .setGitDir(File(dir, ".git"))
            .readEnvironment()
            .build()

    private fun openGit(dir: File): Git = Git(openRepo(dir))
}

// ── Data classes ─────────────────────────────────────────────────────────────

data class CommitInfo(
    val sha: String,
    val message: String,
    val author: String,
    val date: String
)

data class BranchInfo(
    val name: String,
    val isCurrent: Boolean,
    val ahead: Int = 0,
    val behind: Int = 0,
    val upstream: String? = null
)

data class RemoteInfo(
    val name: String,
    val fetchUrl: String,
    val pushUrl: String
)

data class AheadBehind(val ahead: Int, val behind: Int)
