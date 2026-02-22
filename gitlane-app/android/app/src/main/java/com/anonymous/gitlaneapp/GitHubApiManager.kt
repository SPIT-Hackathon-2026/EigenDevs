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
     * Removes a collaborator from a repository.
     * DELETE /repos/{owner}/{repo}/collaborators/{username}
     */
    suspend fun removeCollaborator(repoFullName: String, username: String): Boolean = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$repoFullName/collaborators/$username")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "GitLane-Android")

        // 204 = success, 403 = no permission
        if (conn.responseCode == 403) {
            throw Exception("You don't have permission to remove collaborators from this repository.")
        }
        conn.responseCode in 200..204
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
        val name: String,
        val bio: String,
        val avatarUrl: String,
        val publicRepos: Int,
        val privateRepos: Int,
        val followers: Int,
        val following: Int
    )

    /**
     * Gets full profile info for the authenticated user.
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
            name = json.optString("name", ""),
            bio = json.optString("bio", ""),
            avatarUrl = json.getString("avatar_url"),
            publicRepos = json.optInt("public_repos", 0),
            privateRepos = json.optInt("total_private_repos", 0),
            followers = json.optInt("followers", 0),
            following = json.optInt("following", 0)
        )
    }

    /**
     * Fetches the contribution heatmap for [username] using the GitHub Events API.
     * Returns a 364-element IntArray (week-major order, Mon→Sun),
     * where each value is 0..4 mapped from the raw event count per day.
     *
     * GitHub's REST API provides up to 300 public events (last ~90 days for active users).
     * For the remaining days we simply leave them as 0.
     */
    suspend fun getUserContributions(username: String): IntArray = withContext(Dispatchers.IO) {
        val counts = HashMap<String, Int>()          // "yyyy-MM-dd" → event count
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")

        // Fetch up to 10 pages × 30 events = 300 events
        for (page in 1..10) {
            val url = URL("https://api.github.com/users/$username/events?per_page=30&page=$page")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "GitLane-Android")

            if (conn.responseCode != 200) break
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() == 0) break

            for (i in 0 until arr.length()) {
                val evt = arr.getJSONObject(i)
                val createdAt = evt.optString("created_at", "") ?: continue
                try {
                    val date = sdf.parse(createdAt) ?: continue
                    val day = dayFmt.format(date)
                    counts[day] = (counts[day] ?: 0) + 1
                } catch (_: Exception) {}
            }
        }

        // Build a 364-cell array (52 weeks × 7 days) ending on today
        val today = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val result = IntArray(364)
        // Find the day-of-week of the earliest cell so col 0 starts on Monday
        val startCal = today.clone() as java.util.Calendar
        startCal.add(java.util.Calendar.DAY_OF_YEAR, -363)

        for (i in 0 until 364) {
            val cell = startCal.clone() as java.util.Calendar
            cell.add(java.util.Calendar.DAY_OF_YEAR, i)
            val key = dayFmt.format(cell.time)
            val raw = counts[key] ?: 0
            // Map raw count → 0..4 levels
            result[i] = when {
                raw == 0 -> 0
                raw <= 2 -> 1
                raw <= 5 -> 2
                raw <= 9 -> 3
                else     -> 4
            }
        }
        result
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
