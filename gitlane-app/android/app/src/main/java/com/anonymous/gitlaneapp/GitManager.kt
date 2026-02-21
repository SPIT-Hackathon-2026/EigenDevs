package com.anonymous.gitlaneapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GitManager
 *
 * Single-responsibility class that wraps JGit operations for GitLane.
 * All operations are suspend functions — call them from a coroutine scope.
 *
 * Repos are stored at:  <filesDir>/GitLane/<repoName>/
 */
class GitManager(context: Context) {

    val gitLaneRoot: File = File(context.filesDir, "GitLane").also { it.mkdirs() }

    private val defaultAuthor = PersonIdent("GitLane User", "user@gitlane.app")

    // ── Repo management ──────────────────────────────────────────────────────

    /**
     * Returns a list of all repo directories.
     */
    suspend fun listRepos(): List<File> = withContext(Dispatchers.IO) {
        gitLaneRoot.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Create a new repo: mkdir + git init with default branch "main".
     * Returns the repo directory.
     */
    suspend fun initRepo(repoName: String): File = withContext(Dispatchers.IO) {
        val repoDir = File(gitLaneRoot, repoName)
        repoDir.mkdirs()

        Git.init()
            .setDirectory(repoDir)
            .setInitialBranch("main")
            .call()
            .close()

        repoDir
    }

    // ── File operations ──────────────────────────────────────────────────────

    /**
     * List all files in the repo's working tree (excludes .git).
     */
    suspend fun listFiles(repoDir: File): List<File> = withContext(Dispatchers.IO) {
        repoDir.listFiles { f -> f.name != ".git" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: emptyList()
    }

    /**
     * Write (create or overwrite) a file in the repo.
     */
    suspend fun writeFile(repoDir: File, filename: String, content: String) =
        withContext(Dispatchers.IO) {
            File(repoDir, filename).writeText(content, Charsets.UTF_8)
        }

    /**
     * Read a file's content.
     */
    suspend fun readFile(repoDir: File, filename: String): String =
        withContext(Dispatchers.IO) {
            File(repoDir, filename).readText(Charsets.UTF_8)
        }

    // ── Git operations ───────────────────────────────────────────────────────

    /**
     * Stage all modified/new files in the working tree (git add -A).
     */
    suspend fun stageAll(repoDir: File) = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            git.add().addFilepattern(".").call()
        }
    }

    /**
     * Commit staged changes.
     * Returns the short SHA of the new commit.
     */
    suspend fun commit(repoDir: File, message: String): String = withContext(Dispatchers.IO) {
        openGit(repoDir).use { git ->
            val commit: RevCommit = git.commit()
                .setMessage(message)
                .setAuthor(defaultAuthor)
                .setCommitter(defaultAuthor)
                .call()
            commit.abbreviate(8).name()
        }
    }

    /**
     * Returns the list of commits in reverse-chronological order.
     */
    suspend fun getLog(repoDir: File): List<CommitInfo> = withContext(Dispatchers.IO) {
        try {
            openGit(repoDir).use { git ->
                git.log().call().map { rev ->
                    CommitInfo(
                        sha = rev.abbreviate(8).name(),
                        message = rev.shortMessage,
                        author = rev.authorIdent.name,
                        date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                            .format(Date(rev.commitTime * 1000L))
                    )
                }
            }
        } catch (e: Exception) {
            // Repo has no commits yet
            emptyList()
        }
    }

    /**
     * Returns the current branch name (e.g. "main").
     */
    suspend fun currentBranch(repoDir: File): String = withContext(Dispatchers.IO) {
        try {
            openRepo(repoDir).use { repo ->
                repo.branch ?: "main"
            }
        } catch (e: Exception) {
            "main"
        }
    }

    /**
     * Returns the number of commits in the repo.
     */
    suspend fun commitCount(repoDir: File): Int = withContext(Dispatchers.IO) {
        try {
            openGit(repoDir).use { git ->
                git.log().call().count()
            }
        } catch (e: Exception) {
            0
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun openRepo(dir: File): Repository =
        FileRepositoryBuilder()
            .setGitDir(File(dir, ".git"))
            .readEnvironment()
            .build()

    private fun openGit(dir: File): Git = Git(openRepo(dir))
}

/**
 * Data class representing a single commit for display.
 */
data class CommitInfo(
    val sha: String,
    val message: String,
    val author: String,
    val date: String
)
