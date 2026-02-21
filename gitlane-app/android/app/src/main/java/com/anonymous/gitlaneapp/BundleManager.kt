package com.anonymous.gitlaneapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.transport.BundleWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Handles git bundle creation (for sharing) and import (for receiving).
 * Uses JGit's BundleWriter which produces a format identical to
 * `git bundle create repo.bundle --all`.
 */
class BundleManager {

    /**
     * Creates a .bundle file from a local repository.
     * The bundle contains ALL refs (all branches, all commits).
     * Equivalent to: git bundle create <outFile> --all
     *
     * @param repoDir  The repository working directory (contains .git/)
     * @param outFile  Where to write the bundle (e.g. cacheDir/reponame.bundle)
     */
    suspend fun createBundle(repoDir: File, outFile: File) = withContext(Dispatchers.IO) {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val writer = BundleWriter(repo)

            // Include every ref (branches, HEAD)
            repo.refDatabase.refs.forEach { ref ->
                val obj = repo.resolve(ref.name) ?: return@forEach
                writer.include(ref.name, obj)
            }

            FileOutputStream(outFile).use { fos ->
                writer.writeBundle(NullProgressMonitor.INSTANCE, fos)
            }
        }
    }

    /**
     * Imports a bundle into a NEW local repository.
     * Equivalent to: git clone repo.bundle <targetDir>
     *
     * @param bundleFile  The downloaded .bundle file
     * @param targetDir   Where to create the new repo (must NOT exist yet)
     * @return            Name of the primary branch after clone
     */
    suspend fun importBundle(bundleFile: File, targetDir: File): String =
        withContext(Dispatchers.IO) {
            // JGit's CloneCommand natively understands bundle file URIs
            Git.cloneRepository()
                .setURI(bundleFile.toURI().toString())
                .setDirectory(targetDir)
                .call()
                .use { git ->
                    git.repository.branch ?: "main"
                }
        }
}
