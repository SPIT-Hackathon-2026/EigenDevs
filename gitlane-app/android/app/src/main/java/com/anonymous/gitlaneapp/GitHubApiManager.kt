package com.anonymous.gitlaneapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles direct GitHub REST API calls (Repositories, Collaborators).
 * All methods should be called from a background thread.
 */
class GitHubApiManager(private val token: String) {

    /** 
     * Creates a new repository on GitHub for the authenticated user.
     * @return The HTTPS clone URL of the new repo.
     */
    suspend fun createRepo(name: String, isPrivate: Boolean): String = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/user/repos")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val jsonBody = JSONObject().apply {
            put("name", name)
            put("private", isPrivate)
            put("auto_init", false) // Since we are pushing an existing local repo
        }

        conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

        if (conn.responseCode != 201) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("Failed to create GitHub repo: $error")
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(response).getString("clone_url")
    }

    /**
     * Lists collaborators for a repository.
     */
    suspend fun listCollaborators(repoFullName: String): List<String> = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$repoFullName/collaborators")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestMethod("GET")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("GitHub API Error: $error")
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(response)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i).getString("login"))
        }
        list
    }

    /**
     * Adds a collaborator to a repository.
     * @param repoFullName The "owner/repo" formatted name.
     */
    suspend fun addCollaborator(repoFullName: String, username: String): Boolean = withContext(Dispatchers.IO) {
        // PUT /repos/{owner}/{repo}/collaborators/{username}
        val url = URL("https://api.github.com/repos/$repoFullName/collaborators/$username")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("Content-Length", "0")

        if (conn.responseCode !in 200..204) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("Failed to add: $error")
        }

        // 201 (Created invitation) or 204 (Already a collaborator)
        conn.responseCode == 201 || conn.responseCode == 204
    }

    /**
     * Helper to extract "owner/repo" from a GitHub clone URL.
     */
    companion object {
        fun extractFullName(cloneUrl: String): String? {
            // Handles:
            // https://github.com/user/repo.git
            // https://token@github.com/user/repo
            // git@github.com:user/repo.git
            return try {
                val clean = cloneUrl.removeSuffix(".git").removeSuffix("/")
                if (clean.contains("github.com")) {
                    val parts = clean.split("github.com")
                    val path = if (parts.size > 1) {
                        parts[1].removePrefix("/").removePrefix(":")
                    } else {
                        clean.substringAfterLast(":")
                    }
                    path.trim('/')
                } else null
            } catch (e: Exception) { null }
        }
    }
}
