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
        conn.setRequestProperty("User-Agent", "GitLane-Android")
        conn.doOutput = true

        val jsonBody = JSONObject().apply {
            put("name", name)
            put("private", isPrivate)
            put("auto_init", false) // Since we are pushing an existing local repo
        }

        conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

        if (conn.responseCode != 201) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("GitHub Error (${conn.responseCode}): $error")
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
        conn.setRequestProperty("User-Agent", "GitLane-Android")

        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("GitHub Error (${conn.responseCode}): $error")
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
     * Lists contributors for a public repository. 
     * Unlike collaborators, this is usually public.
     */
    suspend fun listContributors(repoFullName: String): List<String> = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$repoFullName/contributors")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestMethod("GET")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "token $token")
        }
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "GitLane-Android")

        if (conn.responseCode != 200) {
            return@withContext emptyList<String>() // Silent fail for contributors
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
        conn.setRequestProperty("User-Agent", "GitLane-Android")
        conn.setRequestProperty("Content-Length", "0")

        if (conn.responseCode !in 200..204) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("GitHub Error (${conn.responseCode}): $error")
        }

        // 201 (Created invitation) or 204 (Already a collaborator)
        conn.responseCode == 201 || conn.responseCode == 204
    }

    /**
     * Lists current repository invitations for the authenticated user.
     */
    suspend fun listInvitations(): List<InvitationInfo> = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/user/repository_invitations")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestMethod("GET")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "GitLane-Android")

        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("GitHub Error (${conn.responseCode}): $error")
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(response)
        val list = mutableListOf<InvitationInfo>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val repo = obj.optJSONObject("repository") ?: continue
            list.add(InvitationInfo(
                id = obj.optLong("id", -1L),
                repoName = repo.optString("full_name", "Unknown repository"),
                inviter = obj.optJSONObject("inviter")?.optString("login", "Unknown") ?: "Unknown",
                cloneUrl = repo.optString("clone_url", repo.optString("html_url", "")),
                date = obj.optString("created_at", "")
            ))
        }
        list
    }

    /**
     * Accepts a repository invitation.
     */
    suspend fun acceptInvitation(invitationId: Long): Boolean = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/user/repository_invitations/$invitationId")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PATCH"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "GitLane-Android")
        conn.setRequestProperty("Content-Length", "0")

        if (conn.responseCode != 204) {
             val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
             throw Exception("GitHub Error (${conn.responseCode}): $error")
        }

        true
    }

    /**
     * Searches for a public repository by its full name (owner/repo).
     */
    suspend fun getPublicRepo(fullName: String): RepoSearchInfo = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$fullName")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        // Authorization is optional for public repos but helps with rate limits
        if (token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "token $token")
        }
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "GitLane-Android")

        if (conn.responseCode != 200) {
            val error = if (conn.responseCode == 404) "Repository not found" else "HTTP ${conn.responseCode}"
            throw Exception(error)
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        RepoSearchInfo(
            fullName = json.getString("full_name"),
            description = json.optString("description", "No description"),
            stars = json.optInt("stargazers_count", 0),
            cloneUrl = json.getString("clone_url"),
            avatarUrl = json.getJSONObject("owner").getString("avatar_url")
        )
    }

    data class RepoSearchInfo(
        val fullName: String,
        val description: String,
        val stars: Int,
        val cloneUrl: String,
        val avatarUrl: String
    )

    data class InvitationInfo(
        val id: Long,
        val repoName: String,
        val inviter: String,
        val cloneUrl: String,
        val date: String
    )

    data class UserInfo(
        val login: String,
        val avatarUrl: String
    )

    /**
     * Gets profile info for the authenticated user.
     */
    suspend fun getUserProfile(): UserInfo = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/user")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "GitLane-Android")

        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw Exception("GitHub Error (${conn.responseCode}): $error")
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        UserInfo(
            login = json.getString("login"),
            avatarUrl = json.getString("avatar_url")
        )
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
