package com.anonymous.gitlaneapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * Handles remote-origin operations that require network access:
 *  - Clone from GitHub / GitLab / Bitbucket / any HTTPS Git server
 *
 * Authentication is via Personal Access Token (PAT).
 * For GitHub: PAT is used as the username with an empty password.
 * For GitLab / Bitbucket: same convention works for HTTPS PATs.
 */
class RemoteGitManager(context: Context) {

    private val gitLaneRoot: File = File(context.filesDir, "GitLane").also { it.mkdirs() }

    /**
     * Clone a remote repository into <gitLaneRoot>/<repoName>/.
     *
     * @param remoteUrl  Full HTTPS URL, e.g. https://github.com/owner/repo.git
     * @param repoName   Local folder name (defaults to last path segment of URL)
     * @param pat        Personal Access Token for private repos (optional for public)
     * @param progress   Optional callback receiving (taskName, percentComplete)
     * @return           The cloned repo directory
     */
    suspend fun cloneRepo(
        remoteUrl: String,
        repoName: String? = null,
        pat: String? = null,
        progress: ((String, Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {

        val folderName = repoName?.ifBlank { null }
            ?: remoteUrl.trimEnd('/').substringAfterLast('/').removeSuffix(".git")

        val targetDir = File(gitLaneRoot, folderName)
        if (targetDir.exists()) {
            throw IllegalArgumentException("Repo '$folderName' already exists locally. Use a different name.")
        }

        val cloneCmd = Git.cloneRepository()
            .setURI(remoteUrl)
            .setDirectory(targetDir)
            .setCloneAllBranches(true)

        if (!pat.isNullOrBlank()) {
            cloneCmd.setCredentialsProvider(
                UsernamePasswordCredentialsProvider(pat, "")
            )
        }

        if (progress != null) {
            cloneCmd.setProgressMonitor(object : ProgressMonitor {
                override fun start(totalTasks: Int) {}
                override fun beginTask(title: String?, totalWork: Int) {
                    progress(title ?: "Cloning…", 0)
                }
                override fun update(completed: Int) {}
                override fun endTask() {}
                override fun isCancelled() = false
                override fun showDuration(enabled: Boolean) {}
            })
        }

        cloneCmd.call().use { /* close the returned Git instance */ }
        targetDir
    }
}
