package com.anonymous.gitlaneapp.copilot

import android.content.Context
import com.anonymous.gitlaneapp.AheadBehind
import com.anonymous.gitlaneapp.BranchInfo
import com.anonymous.gitlaneapp.CommitInfo
import com.anonymous.gitlaneapp.CredentialsManager
import com.anonymous.gitlaneapp.GitHubApiManager
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.GitStatus
import com.anonymous.gitlaneapp.TagInfo
import com.anonymous.gitlaneapp.data.SettingsRepository
import com.anonymous.gitlaneapp.data.api.GroqApiService
import com.anonymous.gitlaneapp.data.api.GroqMessage
import com.anonymous.gitlaneapp.data.api.GroqRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

// ─── Structured result types ─────────────────────────────────────────────────

sealed class CopilotResult {
    /** Plain markdown-style text from the AI */
    data class Text(val message: String) : CopilotResult()

    /** A list of commits to render as tappable cards */
    data class CommitList(
        val intro: String,
        val commits: List<CommitInfo>,
        val repoDir: File
    ) : CopilotResult()

    /** Branches list with ahead/behind indicators */
    data class BranchList(
        val intro: String,
        val branches: List<BranchInfo>,
        val currentBranch: String,
        val repoDir: File
    ) : CopilotResult()

    /** Repo health summary — ahead/behind, status, branch */
    data class RepoHealth(
        val repoName: String,
        val branch: String,
        val aheadBehind: AheadBehind?,
        val status: GitStatus,
        val totalCommits: Int,
        val latestCommit: CommitInfo?
    ) : CopilotResult()

    /** Commit explanation with diff summary */
    data class CommitExplain(
        val commit: CommitInfo,
        val diff: String,
        val aiExplanation: String,
        val repoDir: File
    ) : CopilotResult()

    /** AI-generated commit message suggestion */
    data class CommitMsgSuggestion(
        val repoDir: File,
        val suggestedMessage: String,
        val stagedFiles: List<String>
    ) : CopilotResult()

    /** Merge conflict explanation */
    data class ConflictExplain(
        val repoDir: File,
        val conflictingFiles: List<String>,
        val aiExplanation: String,
        val currentBranch: String
    ) : CopilotResult()

    /** Tag list */
    data class TagList(val tags: List<TagInfo>, val repoName: String) : CopilotResult()

    /** Action completed — success/failure */
    data class ActionDone(val summary: String, val success: Boolean) : CopilotResult()

    /** AI needs a clarifying input — pending conversation turn */
    data class NeedInput(
        val question: String,
        val pendingAction: String,
        val collected: Map<String, String>
    ) : CopilotResult()
}

// ─── Engine ──────────────────────────────────────────────────────────────────

class CopilotEngine(private val context: Context) {

    private val git      = GitManager(context)
    private val settings = SettingsRepository(context)
    private val creds    = CredentialsManager(context)

    private fun createGroqService(): GroqApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }

    private val groq by lazy { createGroqService() }

    // ── Public entry point ────────────────────────────────────────────────────

    suspend fun process(
        userMessage: String,
        chatHistory: List<GroqMessage>,
        currentRepoPath: String? = null,
        pendingAction: String? = null,
        pendingParams: Map<String, String> = emptyMap()
    ): CopilotResult = withContext(Dispatchers.IO) {

        val currentRepoDir = currentRepoPath?.let { File(it) }

        // ── FAST PATH: local classifier (no API call) ─────────────────────
        // Skip fast-path if we're in a multi-turn pending conversation
        if (pendingAction == null) {
            val localResult = handleLocalIntent(userMessage, currentRepoDir)
            if (localResult != null) return@withContext localResult
        }

        // ── SLOW PATH: Groq API for complex / ambiguous requests ──────────
        val apiKey = settings.getGroqApiKey()
            ?: return@withContext CopilotResult.ActionDone(
                "⚠️ No Groq API key configured. Go to **Settings → Groq API Key**.", false
            )

        val repoContext  = buildContextBlock(currentRepoDir)
        val systemPrompt = buildSystemPrompt(repoContext, currentRepoDir)

        val messages = mutableListOf(GroqMessage("system", systemPrompt))
        chatHistory.filter { it.role != "system" }.takeLast(12).forEach { messages.add(it) }

        if (pendingAction != null) {
            messages.add(
                GroqMessage(
                    "system",
                    "RESUMING ACTION: '$pendingAction'. Params collected so far: $pendingParams. User answered: $userMessage"
                )
            )
        }
        messages.add(GroqMessage("user", userMessage))

        val raw = try {
            groq.getCompletion(
                auth    = "Bearer $apiKey",
                request = GroqRequest(
                    model       = settings.getGroqModel(),
                    messages    = messages,
                    temperature = 0.2
                )
            ).choices.firstOrNull()?.message?.content
                ?: return@withContext CopilotResult.Text("No response received from Groq.")
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            return@withContext CopilotResult.ActionDone(
                "❌ Groq Error: $msg\n\n" +
                "Note: If DNS failed, try toggling Airplane mode or checking your internet connection. " +
                "Merge conflict resolution uses the same API, so there is likely a temporary network issue.", 
                false
            )
        }

        parseAndDispatch(raw, currentRepoDir, pendingAction, pendingParams)
    }

    // ── Local fast-path handler ───────────────────────────────────────────────
    //  Returns null if intent is Unknown (fall through to Groq)

    private suspend fun handleLocalIntent(
        userMessage: String,
        currentRepoDir: File?
    ): CopilotResult? {
        val intent = LocalIntentClassifier.classify(userMessage)

        // Unknown → let Groq handle it
        if (intent is LocalIntentClassifier.LocalIntent.Unknown) return null

        // All intents below need to know the repo
        val dir = currentRepoDir

        return when (intent) {

            // ── No-network simple reads ─────────────────────────────────────

            is LocalIntentClassifier.LocalIntent.ListBranches -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "list_branches", emptyMap())
                val branches = try { git.listBranches(d) } catch (e: Exception) { emptyList() }
                val current  = try { git.currentBranch(d) } catch (e: Exception) { "main" }
                CopilotResult.BranchList(
                    "🌿 **${branches.size}** branch${if (branches.size != 1) "es" else ""} in **${d.name}**",
                    branches, current, d
                )
            }

            is LocalIntentClassifier.LocalIntent.CurrentBranch -> {
                val d = dir ?: return null // let Groq ask
                val branch = try { git.currentBranch(d) } catch (e: Exception) { "unknown" }
                CopilotResult.Text("🌿 You're on branch **$branch** in **${d.name}**.")
            }

            is LocalIntentClassifier.LocalIntent.RepoHealth -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "repo_health", emptyMap())
                val branch  = try { git.currentBranch(d) } catch (e: Exception) { "main" }
                val status  = try { git.getStatus(d) } catch (e: Exception) { com.anonymous.gitlaneapp.GitStatus() }
                val count   = try { git.commitCount(d) } catch (e: Exception) { 0 }
                val commits = try { git.getLog(d) } catch (e: Exception) { emptyList() }
                val ab = if (branch != "main") {
                    try { git.compareBranches(d, "main", branch) } catch (e: Exception) { null }
                } else null
                CopilotResult.RepoHealth(d.name, branch, ab, status, count, commits.firstOrNull())
            }

            is LocalIntentClassifier.LocalIntent.GitStatus -> {
                val d = dir ?: return null
                val status = try { git.getStatus(d) } catch (e: Exception) { com.anonymous.gitlaneapp.GitStatus() }
                val sb = StringBuilder("📋 **Status of ${d.name}**\n\n")
                if (!status.hasChanges()) {
                    sb.append("✅ Working tree clean — nothing to commit.")
                } else {
                    if (status.modified.isNotEmpty()) sb.append("📝 Modified: ${status.modified.joinToString(", ")}\n")
                    if (status.added.isNotEmpty()) sb.append("➕ Added: ${status.added.joinToString(", ")}\n")
                    if (status.removed.isNotEmpty()) sb.append("🗑️ Removed: ${status.removed.joinToString(", ")}\n")
                    if (status.untracked.isNotEmpty()) sb.append("❓ Untracked: ${status.untracked.joinToString(", ")}\n")
                    if (status.conflicting.isNotEmpty()) sb.append("⚠️ Conflicting: ${status.conflicting.joinToString(", ")}\n")
                }
                CopilotResult.Text(sb.toString())
            }

            is LocalIntentClassifier.LocalIntent.ListCommits -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "list_commits", emptyMap())
                val commits = try { git.getLog(d).take(20) } catch (e: Exception) { emptyList() }
                CopilotResult.CommitList("📦 Last **${commits.size}** commits in **${d.name}**", commits, d)
            }

            is LocalIntentClassifier.LocalIntent.SearchCommits -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "list_commits", emptyMap())
                val all      = try { git.getLog(d) } catch (e: Exception) { emptyList() }
                val filtered = semanticFilter(all, intent.query)
                CopilotResult.CommitList(
                    "🔍 Found **${filtered.size}** commit${if (filtered.size != 1) "s" else ""} matching *\"${intent.query}\"*",
                    filtered.take(20), d
                )
            }

            is LocalIntentClassifier.LocalIntent.ListTags -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "list_tags", emptyMap())
                val tags = try { git.listTags(d) } catch (e: Exception) { emptyList() }
                CopilotResult.TagList(tags, d.name)
            }

            // ── Branch mutations ────────────────────────────────────────────

            is LocalIntentClassifier.LocalIntent.CreateBranch -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "create_branch", emptyMap())
                try {
                    if (intent.checkout) {
                        git.createAndCheckoutBranch(d, intent.name)
                        CopilotResult.ActionDone("✅ Created **${intent.name}** and switched to it in **${d.name}**.", true)
                    } else {
                        git.createBranch(d, intent.name)
                        CopilotResult.ActionDone("✅ Branch **${intent.name}** created in **${d.name}**.", true)
                    }
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Branch creation failed: ${e.message}", false)
                }
            }

            is LocalIntentClassifier.LocalIntent.CheckoutBranch -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "checkout_branch", emptyMap())
                try {
                    git.checkoutBranch(d, intent.name)
                    CopilotResult.ActionDone("✅ Switched to **${intent.name}** in **${d.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Checkout failed: ${e.message}", false)
                }
            }

            is LocalIntentClassifier.LocalIntent.DeleteBranch -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "delete_branch", emptyMap())
                try {
                    git.deleteBranch(d, intent.name, force = false)
                    CopilotResult.ActionDone("🗑️ Branch **${intent.name}** deleted from **${d.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Delete failed: ${e.message}", false)
                }
            }

            is LocalIntentClassifier.LocalIntent.MergeBranch -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "merge_branch", emptyMap())
                try {
                    val result  = git.merge(d, intent.source)
                    val current = git.currentBranch(d)
                    when {
                        result.mergeStatus.isSuccessful ->
                            CopilotResult.ActionDone("✅ Merged **${intent.source}** into **$current**.", true)
                        result.mergeStatus.toString().contains("CONFLICTING", ignoreCase = true) -> {
                            val files = result.conflicts?.keys?.toList() ?: emptyList()
                            CopilotResult.ActionDone(
                                "⚠️ Conflicts in ${files.size} file(s):\n${files.joinToString("\n") { "  • $it" }}\n\nAsk me to explain the conflicts!", false
                            )
                        }
                        else -> CopilotResult.ActionDone("Merge: ${result.mergeStatus}", result.mergeStatus.isSuccessful)
                    }
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Merge failed: ${e.message}", false)
                }
            }

            is LocalIntentClassifier.LocalIntent.CreateTag -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "create_tag", emptyMap())
                try {
                    git.createTag(d, intent.name, intent.message)
                    CopilotResult.ActionDone("🏷️ Tag **${intent.name}** created in **${d.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Tag creation failed: ${e.message}", false)
                }
            }

            is LocalIntentClassifier.LocalIntent.CreateFile -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "create_file", emptyMap())
                try {
                    git.writeFile(d, intent.filename, intent.content)
                    CopilotResult.ActionDone("✅ `${intent.filename}` created in **${d.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ ${e.message}", false)
                }
            }

            is LocalIntentClassifier.LocalIntent.StageAndCommit -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "stage_and_commit", emptyMap())
                // If we have a message, commit instantly; otherwise fall to Groq for msg generation
                val rawMsg = intent.message
                if (rawMsg.isBlank()) return null // → Groq will generate the message
                try {
                    git.stageAll(d)
                    val sha = git.commit(d, rawMsg)
                    CopilotResult.ActionDone("✅ Committed [`${sha.take(7)}`] in **${d.name}**\n\n> $rawMsg", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Commit failed: ${e.message}", false)
                }
            }

            is LocalIntentClassifier.LocalIntent.Rebase -> {
                val d = dir ?: return CopilotResult.NeedInput("Which repo?", "rebase", emptyMap())
                CopilotResult.ActionDone("__REBASE__:${d.absolutePath}|${intent.upstream}", true)
            }

            // ── AI-required (still needs Groq) — return null to fall through ─

            is LocalIntentClassifier.LocalIntent.ExplainLatestCommit,
            is LocalIntentClassifier.LocalIntent.GenerateCommitMsg,
            is LocalIntentClassifier.LocalIntent.GenerateReadme,
            is LocalIntentClassifier.LocalIntent.ExplainConflict -> null

            is LocalIntentClassifier.LocalIntent.Unknown -> null
        }
    }


    // ── System Prompt ─────────────────────────────────────────────────────────

    private fun buildSystemPrompt(repoContext: String, currentRepo: File?): String = """
You are GitLane Copilot — an elite Git Intelligence Assistant embedded in a mobile Git client.
You EXECUTE real Git operations, EXPLAIN code changes, ANALYSE repository health, and GUIDE developers.

You are NOT a generic chatbot. You are a conversational Git engine.

## RESPONSE FORMAT
Always respond with ONLY a JSON object. No markdown wrapper, no explanation outside JSON.

{
  "action": "<ACTION_ID>",
  "params": { ... },
  "reply": "<friendly reply with emojis, shown to user>",
  "needs": "<field_name_if_missing | null>"
}

## AVAILABLE ACTIONS

### Repository Actions
- "text"              → Just reply (no git op). Use for greetings, explanations, general Q&A.
- "create_repo"       → params: { "name": "repoName" }
- "clone_repo"        → params: { "url": "https://github.com/..." }
- "repo_health"       → params: { "repo": "repoName", "compare_branch": "main" }

### Branch Actions
- "list_branches"     → params: { "repo": "repoName" }
- "create_branch"     → params: { "repo": "repoName", "branch": "feature/x", "checkout": true }
- "checkout_branch"   → params: { "repo": "repoName", "branch": "branchName" }
- "delete_branch"     → params: { "repo": "repoName", "branch": "branchName" }
- "merge_branch"      → params: { "repo": "repoName", "source": "featureBranch" }
- "rebase"            → params: { "repo": "repoName", "upstream": "branchName" }

### Commit Actions
- "list_commits"      → params: { "repo": "repoName", "query": "search text", "limit": 20 }
- "explain_commit"    → params: { "repo": "repoName", "sha": "abc1234" }
- "generate_commit_msg" → params: { "repo": "repoName" }
- "stage_and_commit"  → params: { "repo": "repoName", "message": "commit message" }

### File Actions
- "create_file"       → params: { "repo": "repoName", "filename": "path/to/file.ext", "content": "..." }
- "generate_readme"   → params: { "repo": "repoName" }

### Tag Actions
- "list_tags"         → params: { "repo": "repoName" }
- "create_tag"        → params: { "repo": "repoName", "tag": "v1.0.0", "message": "optional" }

### Conflict Actions
- "explain_conflict"  → params: { "repo": "repoName" }

### GitHub Actions
- "add_collaborator"  → params: { "repo": "repoName", "username": "githubUser" }
- "open_repo"         → params: { "repo": "repoName" }

## CRITICAL RULES — READ CAREFULLY

1. 🔴 CURRENT REPO IS ALWAYS THE DEFAULT: The currently open repo is "${currentRepo?.name ?: "NONE"}". 
   - If currentRepo is set, ALWAYS use it for any action that needs a repo, UNLESS the user explicitly names a different repo.
   - NEVER ask "which repo?" if the current repo is already set.
   - Set params.repo = "${currentRepo?.name ?: ""}" automatically.
2. REPO MATCHING: Match repo names fuzzily (case-insensitive, partial match).
3. MISSING PARAMS: Only set "needs" for NON-REPO params (like branch name, tag name, username). NEVER set needs=repo when a current repo is available.
4. SEMANTIC SEARCH: For list_commits, interpret the query semantically:
   - "last week" → filter by date range
   - "auth module" → commits touching auth/login files
   - "release" → look for tags/version commits
   - "John" → filter by author
5. NEVER fabricate data. Only use the repo context provided below.
6. BRANCH HEALTH: For repo_health, check ahead/behind vs main branch.
7. CONFLICT DETECTION: If repo is in MERGING state, proactively suggest explain_conflict.
8. Keep "reply" short, informative, action-confirming, with emojis.

## LIVE REPOSITORY CONTEXT
$repoContext
    """.trimIndent()

    // ── Parse + dispatch ──────────────────────────────────────────────────────

    private suspend fun parseAndDispatch(
        raw: String,
        currentRepoDir: File?,
        pendingAction: String?,
        pendingParams: Map<String, String>
    ): CopilotResult {

        val json = try {
            val cleaned = raw
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
                // Sometimes Groq adds trailing text after the JSON
                .substringBefore("\n}").let { it + if (it.trimEnd().endsWith("}")) "" else "\n}" }
            JSONObject(cleaned)
        } catch (e: Exception) {
            // If not JSON, return as plain text
            return CopilotResult.Text(raw.take(2000))
        }

        val action = json.optString("action", "text")
        val params = json.optJSONObject("params")
        val reply  = json.optString("reply", "Done!")
        // Suppress "needs=repo" when we already have currentRepoDir
        val rawNeeds = json.optString("needs").takeIf { it.isNotBlank() && it != "null" }
        val needs = if (rawNeeds == "repo" && currentRepoDir != null) null else rawNeeds

        if (needs != null) {
            return CopilotResult.NeedInput(
                question      = reply,
                pendingAction = action,
                collected     = pendingParams + (params?.let { p ->
                    (0 until p.length()).associate { i ->
                        val k = p.names()?.getString(i) ?: return@associate "" to ""
                        k to p.optString(k)
                    }
                } ?: emptyMap())
            )
        }

        // Resolve repo — current repo always wins unless user names a different one
        val repoName = params?.optString("repo")?.trim() ?: ""
        val repoDir = when {
            // If Groq gave us a name, try to resolve it, but only switch if it's a real different repo
            repoName.isNotBlank() && !repoName.equals(currentRepoDir?.name, ignoreCase = true) ->
                resolveRepo(repoName) ?: currentRepoDir
            // Otherwise always use the current repo
            else -> currentRepoDir ?: resolveRepo(repoName)
        }

        return when (action) {

            // ── Text / General ──────────────────────────────────────────────
            "text" -> CopilotResult.Text(reply)

            // ── Repo creation ───────────────────────────────────────────────
            "create_repo" -> {
                val name = params?.optString("name") ?: ""
                if (name.isBlank()) return CopilotResult.NeedInput(
                    "What should I name the new repository?", action, emptyMap()
                )
                try {
                    git.initRepo(name)
                    CopilotResult.ActionDone("✅ Repository **$name** created on branch `main`.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Failed to create repo: ${e.message}", false)
                }
            }

            // ── Repo health ─────────────────────────────────────────────────
            "repo_health" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput(
                    "Which repository should I check?", action, emptyMap()
                )
                val branch  = try { git.currentBranch(dir) } catch (e: Exception) { "main" }
                val status  = try { git.getStatus(dir) } catch (e: Exception) { com.anonymous.gitlaneapp.GitStatus() }
                val count   = try { git.commitCount(dir) } catch (e: Exception) { 0 }
                val commits = try { git.getLog(dir) } catch (e: Exception) { emptyList() }
                val compareTo = params?.optString("compare_branch") ?: "main"
                val ab = if (branch != compareTo) {
                    try { git.compareBranches(dir, compareTo, branch) } catch (e: Exception) { null }
                } else null
                CopilotResult.RepoHealth(
                    repoName     = dir.name,
                    branch       = branch,
                    aheadBehind  = ab,
                    status       = status,
                    totalCommits = count,
                    latestCommit = commits.firstOrNull()
                )
            }

            // ── Branch list ─────────────────────────────────────────────────
            "list_branches" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput(
                    "Which repo's branches should I list?", action, emptyMap()
                )
                val branches = try { git.listBranches(dir) } catch (e: Exception) { emptyList() }
                val current  = try { git.currentBranch(dir) } catch (e: Exception) { "main" }
                CopilotResult.BranchList(reply, branches, current, dir)
            }

            // ── Create branch ───────────────────────────────────────────────
            "create_branch" -> {
                val dir    = repoDir ?: return CopilotResult.NeedInput(
                    "Which repo should I create the branch in?", action, emptyMap()
                )
                val branch   = params?.optString("branch") ?: ""
                val checkout = params?.optBoolean("checkout", true) ?: true
                if (branch.isBlank()) return CopilotResult.NeedInput(
                    "What should I name the new branch?", action, mapOf("repo" to dir.name)
                )
                try {
                    if (checkout) {
                        git.createAndCheckoutBranch(dir, branch)
                        CopilotResult.ActionDone(
                            "✅ Created **$branch** and switched to it in **${dir.name}**.", true
                        )
                    } else {
                        git.createBranch(dir, branch)
                        CopilotResult.ActionDone(
                            "✅ Branch **$branch** created in **${dir.name}**.", true
                        )
                    }
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Branch creation failed: ${e.message}", false)
                }
            }

            // ── Checkout branch ─────────────────────────────────────────────
            "checkout_branch" -> {
                val dir    = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val branch = params?.optString("branch") ?: ""
                if (branch.isBlank()) return CopilotResult.NeedInput(
                    "Which branch should I switch to?", action, mapOf("repo" to dir.name)
                )
                try {
                    git.checkoutBranch(dir, branch)
                    CopilotResult.ActionDone("✅ Switched to **$branch** in **${dir.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Checkout failed: ${e.message}", false)
                }
            }

            // ── Delete branch ───────────────────────────────────────────────
            "delete_branch" -> {
                val dir    = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val branch = params?.optString("branch") ?: ""
                if (branch.isBlank()) return CopilotResult.NeedInput(
                    "Which branch should I delete?", action, mapOf("repo" to dir.name)
                )
                try {
                    git.deleteBranch(dir, branch, force = false)
                    CopilotResult.ActionDone("🗑️ Branch **$branch** deleted from **${dir.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Delete failed: ${e.message}", false)
                }
            }

            // ── Merge branch ────────────────────────────────────────────────
            "merge_branch" -> {
                val dir    = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val source = params?.optString("source") ?: ""
                if (source.isBlank()) return CopilotResult.NeedInput(
                    "Which branch should I merge into the current one?",
                    action, mapOf("repo" to dir.name)
                )
                try {
                    val result = git.merge(dir, source)
                    val current = git.currentBranch(dir)
                    when {
                        result.mergeStatus.isSuccessful ->
                            CopilotResult.ActionDone(
                                "✅ Merged **$source** into **$current** successfully.", true
                            )
                        result.mergeStatus.toString().contains("CONFLICTING", ignoreCase = true) -> {
                            val files = result.conflicts?.keys?.toList() ?: emptyList()
                            CopilotResult.ActionDone(
                                "⚠️ Merge resulted in conflicts in ${files.size} file(s):\n${files.joinToString("\n") { "  • $it" }}\n\nAsk me to explain the conflicts!", false
                            )
                        }
                        else ->
                            CopilotResult.ActionDone(
                                "Merge status: ${result.mergeStatus}", result.mergeStatus.isSuccessful
                            )
                    }
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Merge failed: ${e.message}", false)
                }
            }

            // ── List commits ────────────────────────────────────────────────
            "list_commits" -> {
                val dir   = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val query = params?.optString("query") ?: ""
                val limit = params?.optInt("limit", 20) ?: 20
                val all   = try { git.getLog(dir).take(2000) } catch (e: Exception) { emptyList() }
                val filtered = if (query.isNotBlank()) semanticFilter(all, query) else all
                CopilotResult.CommitList(reply, filtered.take(limit), dir)
            }

            // ── Explain commit ──────────────────────────────────────────────
            "explain_commit" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val sha = params?.optString("sha") ?: ""
                if (sha.isBlank()) {
                    // Explain the latest commit
                    val latest = try { git.getLog(dir).firstOrNull() } catch (e: Exception) { null }
                        ?: return CopilotResult.ActionDone("❌ No commits found in this repo.", false)
                    val diff    = getCommitDiff(dir, latest.sha)
                    val explain = explainDiffWithAI(latest, diff)
                    CopilotResult.CommitExplain(latest, diff, explain, dir)
                } else {
                    val commit = try { git.getLog(dir).find { it.sha.startsWith(sha, ignoreCase = true) } } catch (e: Exception) { null }
                        ?: return CopilotResult.ActionDone("❌ Commit `$sha` not found.", false)
                    val diff    = getCommitDiff(dir, commit.sha)
                    val explain = explainDiffWithAI(commit, diff)
                    CopilotResult.CommitExplain(commit, diff, explain, dir)
                }
            }

            // ── Generate commit message ─────────────────────────────────────
            "generate_commit_msg" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val status = try { git.getStatus(dir) } catch (e: Exception) {
                    return CopilotResult.ActionDone("❌ Could not read repo status: ${e.message}", false)
                }
                val changed = status.allChanges()
                if (changed.isEmpty()) {
                    return CopilotResult.ActionDone(
                        "📭 No staged or modified files found in **${dir.name}**. Make some changes first.", false
                    )
                }
                val msg = generateCommitMessage(dir, changed)
                CopilotResult.CommitMsgSuggestion(dir, msg, changed)
            }

            // ── Stage & commit ──────────────────────────────────────────────
            "stage_and_commit" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                var message = params?.optString("message") ?: ""
                if (message.isBlank()) {
                    // Auto-generate
                    val status  = try { git.getStatus(dir) } catch (e: Exception) { com.anonymous.gitlaneapp.GitStatus() }
                    val changed = status.allChanges()
                    message = if (changed.isNotEmpty()) generateCommitMessage(dir, changed)
                              else "Update via GitLane Copilot"
                }
                try {
                    git.stageAll(dir)
                    val sha = git.commit(dir, message)
                    CopilotResult.ActionDone(
                        "✅ Committed [`${sha.take(7)}`] in **${dir.name}**\n\n> $message", true
                    )
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Commit failed: ${e.message}", false)
                }
            }

            // ── Create file ─────────────────────────────────────────────────
            "create_file" -> {
                val dir      = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val filename = params?.optString("filename") ?: ""
                val content  = params?.optString("content") ?: ""
                if (filename.isBlank()) return CopilotResult.NeedInput(
                    "What filename should I create?", action, mapOf("repo" to dir.name)
                )
                try {
                    git.writeFile(dir, filename, content)
                    CopilotResult.ActionDone("✅ `$filename` created in **${dir.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ ${e.message}", false)
                }
            }

            // ── Generate README ─────────────────────────────────────────────
            "generate_readme" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                try {
                    val readme = generateReadme(dir.name, dir)
                    git.writeFile(dir, "README.md", readme)
                    CopilotResult.ActionDone(
                        "✅ README.md generated and saved to **${dir.name}**.\n\nOpen the file to see it!", true
                    )
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ README generation failed: ${e.message}", false)
                }
            }

            // ── List tags ───────────────────────────────────────────────────
            "list_tags" -> {
                val dir  = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val tags = try { git.listTags(dir) } catch (e: Exception) { emptyList() }
                CopilotResult.TagList(tags, dir.name)
            }

            // ── Create tag ──────────────────────────────────────────────────
            "create_tag" -> {
                val dir  = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val tag  = params?.optString("tag") ?: ""
                val msg  = params?.optString("message")
                if (tag.isBlank()) return CopilotResult.NeedInput(
                    "What tag name should I create? (e.g. v1.0.0)", action, mapOf("repo" to dir.name)
                )
                try {
                    git.createTag(dir, tag, msg)
                    CopilotResult.ActionDone("🏷️ Tag **$tag** created in **${dir.name}**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ Tag creation failed: ${e.message}", false)
                }
            }

            // ── Explain conflict ────────────────────────────────────────────
            "explain_conflict" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val status = try { git.getStatus(dir) } catch (e: Exception) { com.anonymous.gitlaneapp.GitStatus() }
                val conflicts = status.conflicting
                if (conflicts.isEmpty()) {
                    return CopilotResult.ActionDone(
                        "✅ No merge conflicts detected in **${dir.name}**.", true
                    )
                }
                val branch  = try { git.currentBranch(dir) } catch (e: Exception) { "current" }
                val explain = explainConflictsWithAI(conflicts, branch, dir)
                CopilotResult.ConflictExplain(dir, conflicts, explain, branch)
            }

            // ── Add GitHub collaborator ─────────────────────────────────────
            "add_collaborator" -> {
                val dir      = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val username = params?.optString("username") ?: ""
                if (username.isBlank()) return CopilotResult.NeedInput(
                    "What's the GitHub username of the person to invite?",
                    action, mapOf("repo" to dir.name)
                )
                val remotes  = try { git.listRemotes(dir) } catch (e: Exception) { emptyList() }
                val url      = remotes.find { it.fetchUrl.contains("github.com") }?.fetchUrl
                    ?: return CopilotResult.ActionDone("❌ No GitHub remote found for **${dir.name}**.", false)
                val fullName = GitHubApiManager.extractFullName(url)
                    ?: return CopilotResult.ActionDone("❌ Could not extract repo name from GitHub URL.", false)
                val token    = creds.getPat("github.com")
                    ?: return CopilotResult.ActionDone("❌ No GitHub token set. Add one in Settings.", false)
                try {
                    GitHubApiManager(token).addCollaborator(fullName, username)
                    CopilotResult.ActionDone("✅ Invitation sent to **@$username** for **$fullName**.", true)
                } catch (e: Exception) {
                    CopilotResult.ActionDone("❌ ${e.message}", false)
                }
            }

            // ── Open repo ───────────────────────────────────────────────────
            "open_repo" -> {
                val dir = repoDir
                    ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                CopilotResult.ActionDone("__OPEN_REPO__:${dir.absolutePath}", true)
            }

            // ── Rebase ──────────────────────────────────────────────────────
            "rebase" -> {
                val dir = repoDir ?: return CopilotResult.NeedInput("Which repo?", action, emptyMap())
                val upstream = params?.optString("upstream") ?: "main"
                CopilotResult.ActionDone("__REBASE__:${dir.absolutePath}|$upstream", true)
            }

            else -> CopilotResult.Text(reply)
        }
    }

    // ── Diff reading ──────────────────────────────────────────────────────────

    private suspend fun getCommitDiff(repoDir: File, sha: String): String {
        return git.getDiff(repoDir, sha)
    }

    // ── AI Helpers ────────────────────────────────────────────────────────────

    private suspend fun explainDiffWithAI(commit: CommitInfo, diff: String): String {
        val apiKey = settings.getGroqApiKey() ?: return "⚠️ No Groq key set."
        val prompt = """
Explain this git commit concisely for a developer:

Commit: ${commit.sha}
Message: ${commit.message}
Author: ${commit.author}
Date: ${commit.date}

Diff/Changes:
$diff

In bullet points, explain:
• What changed?
• Why it likely changed (based on message + diff)?
• Any potential impact?
Keep it under 5 bullets. Use technical language but be clear.
        """.trimIndent()

        return try {
            groq.getCompletion(
                auth    = "Bearer $apiKey",
                request = GroqRequest(
                    model       = settings.getGroqModel(),
                    messages    = listOf(
                        GroqMessage("system", "You are a senior developer explaining git commits. Be concise, technical, and insightful. Use bullet points."),
                        GroqMessage("user", prompt)
                    ),
                    temperature = 0.3
                )
            ).choices.firstOrNull()?.message?.content ?: "Could not generate explanation."
        } catch (e: Exception) {
            "❌ Groq error: ${e.message}"
        }
    }

    private suspend fun explainConflictsWithAI(
        files: List<String>,
        branch: String,
        repoDir: File
    ): String {
        val apiKey = settings.getGroqApiKey() ?: return "⚠️ No Groq key set."
        val recentCommits = try { git.getLog(repoDir).take(5) } catch (e: Exception) { emptyList() }

        val prompt = """
A git merge conflict has occurred during a merge operation.

Current branch: $branch
Conflicting files:
${files.joinToString("\n") { "  • $it" }}

Recent commit history:
${recentCommits.joinToString("\n") { "  [${it.sha}] ${it.message} by ${it.author}" }}

Explain:
1. Why merge conflicts typically happen in these files
2. What each conflicting file likely contains (based on filenames)
3. How to resolve them (Accept ours / Accept theirs / Manual edit)
4. Which approach is usually safest

Be specific, practical, and reassuring. Use emojis.
        """.trimIndent()

        return try {
            groq.getCompletion(
                auth    = "Bearer $apiKey",
                request = GroqRequest(
                    model       = settings.getGroqModel(),
                    messages    = listOf(
                        GroqMessage("system", "You are a Git expert explaining merge conflicts. Be helpful, clear, and actionable."),
                        GroqMessage("user", prompt)
                    ),
                    temperature = 0.4
                )
            ).choices.firstOrNull()?.message?.content ?: "Could not explain conflicts."
        } catch (e: Exception) {
            "❌ Groq error: ${e.message}"
        }
    }

    private suspend fun generateCommitMessage(repoDir: File, changedFiles: List<String>): String {
        val apiKey = settings.getGroqApiKey() ?: return "Update files"
        val prompt = """
Generate a concise, conventional git commit message for these changes:

Changed files:
${changedFiles.joinToString("\n") { "  • $it" }}

Rules:
- Use conventional commits format: type(scope): description
- Types: feat, fix, refactor, docs, style, test, chore
- Keep under 72 characters
- Be specific about what changed based on the filenames
- Return ONLY the commit message, nothing else

Example: feat(auth): add JWT token refresh mechanism
        """.trimIndent()

        return try {
            groq.getCompletion(
                auth    = "Bearer $apiKey",
                request = GroqRequest(
                    model       = settings.getGroqModel(),
                    messages    = listOf(
                        GroqMessage("system", "You generate concise conventional git commit messages. Return ONLY the commit message text."),
                        GroqMessage("user", prompt)
                    ),
                    temperature = 0.5
                )
            ).choices.firstOrNull()?.message?.content?.trim()?.lines()?.firstOrNull()
                ?: "chore: update files"
        } catch (e: Exception) {
            "chore: update files via GitLane"
        }
    }

    private suspend fun generateReadme(repoName: String, repoDir: File): String {
        val apiKey  = settings.getGroqApiKey() ?: throw Exception("No Groq API key")
        val commits = try { git.getLog(repoDir).take(5) } catch (e: Exception) { emptyList() }
        val files   = try { git.listFiles(repoDir).map { it.name } } catch (e: Exception) { emptyList() }
        val branch  = try { git.currentBranch(repoDir) } catch (e: Exception) { "main" }

        return groq.getCompletion(
            auth    = "Bearer $apiKey",
            request = GroqRequest(
                model       = settings.getGroqModel(),
                messages    = listOf(
                    GroqMessage("system", "You generate professional README.md files in Markdown. Include emoji headers."),
                    GroqMessage("user", """
Generate a professional README.md for:
Repo: $repoName | Branch: $branch
Files: ${files.joinToString(", ")}
Recent commits: ${commits.joinToString(" | ") { it.message }}

Include: title with emoji, description, features list, getting started, tech stack, contributing guide.
                    """.trimIndent())
                ),
                temperature = 0.6
            )
        ).choices.firstOrNull()?.message?.content ?: "# $repoName\n\nGenerated by GitLane Copilot."
    }

    // ── Context building ──────────────────────────────────────────────────────

    private suspend fun buildContextBlock(currentRepo: File?): String = buildString {
        try {
            val repos = git.listRepos()
            if (repos.isEmpty()) {
                appendLine("No local repositories found.")
                return@buildString
            }

            // Prioritize current repo first
            val sorted = if (currentRepo != null)
                listOf(currentRepo) + repos.filter { it.absolutePath != currentRepo.absolutePath }
            else repos

            sorted.take(6).forEach { repo ->
                val branch  = try { git.currentBranch(repo) } catch (e: Exception) { "?" }
                val count   = try { git.commitCount(repo) } catch (e: Exception) { 0 }
                val status  = try { git.getStatus(repo) } catch (e: Exception) { com.anonymous.gitlaneapp.GitStatus() }
                val state   = try { git.getRepositoryState(repo) } catch (e: Exception) { null }
                val commits = try { git.getLog(repo).take(5) } catch (e: Exception) { emptyList() }

                appendLine("## REPO: ${repo.name}${if (repo == currentRepo) " ← CURRENT" else ""}")
                appendLine("   Branch: $branch | Commits: $count | State: ${state?.description ?: "SAFE"}")
                if (status.conflicting.isNotEmpty()) appendLine("   ⚠️ CONFLICTS: ${status.conflicting.joinToString(", ")}")
                if (status.modified.isNotEmpty()) appendLine("   Modified: ${status.modified.take(5).joinToString(", ")}")
                commits.forEach { c ->
                    appendLine("   • [${c.sha}] ${c.message} — ${c.author} (${c.date})")
                }
                appendLine()
            }
        } catch (e: Exception) {
            appendLine("Error loading context: ${e.message}")
        }
    }

    // ── Semantic commit filtering ─────────────────────────────────────────────

    private fun semanticFilter(commits: List<CommitInfo>, query: String): List<CommitInfo> {
        if (query.isBlank()) return commits
        val q = query.lowercase()

        // Date-based shortcuts
        val now = System.currentTimeMillis()
        val dateFilter: ((CommitInfo) -> Boolean)? = when {
            q.contains("today") || q.contains("24 hours") -> { c ->
                parseCommitDate(c.date)?.let { (now - it) < 86_400_000L } ?: false
            }
            q.contains("last week") || q.contains("this week") -> { c ->
                parseCommitDate(c.date)?.let { (now - it) < 7 * 86_400_000L } ?: false
            }
            q.contains("last month") || q.contains("this month") -> { c ->
                parseCommitDate(c.date)?.let { (now - it) < 30 * 86_400_000L } ?: false
            }
            q.contains("yesterday") -> { c ->
                parseCommitDate(c.date)?.let { t ->
                    (now - t) in 86_400_000L..172_800_000L
                } ?: false
            }
            else -> null
        }

        // Keyword tokens
        val stopWords = setOf("show", "find", "get", "list", "commits", "commit", "from", "the", "in", "on", "at", "my", "all")
        val tokens = q.split(" ", ",", "-", "_", "/", ":")
            .map { it.trim() }
            .filter { it.length > 2 && it !in stopWords }

        data class Scored(val c: CommitInfo, val score: Int)

        return commits.mapNotNull { c ->
            var score = 0

            // Apply date filter first
            if (dateFilter != null) {
                if (!dateFilter(c)) return@mapNotNull null
                score += 10
            }

            // Keyword scoring
            val text = "${c.message} ${c.author}".lowercase()
            tokens.forEach { token ->
                when {
                    c.message.lowercase().contains(token) -> score += 5
                    c.author.lowercase().contains(token)  -> score += 3
                    text.contains(token)                  -> score += 1
                }
            }

            // Release/tag detection
            if ((q.contains("release") || q.contains("version")) &&
                (c.message.matches(Regex(".*v\\d+\\..*")) || c.message.lowercase().contains("release"))) {
                score += 8
            }

            if (dateFilter == null && tokens.isEmpty()) score = 1 // No filter = show all
            if (score > 0) Scored(c, score) else null
        }
            .sortedByDescending { it.score }
            .map { it.c }
            .ifEmpty { commits.take(10) }
    }

    private fun parseCommitDate(dateStr: String): Long? {
        return try {
            java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                .parse(dateStr)?.time
        } catch (e: Exception) { null }
    }

    // ── Repo resolver ─────────────────────────────────────────────────────────

    private suspend fun resolveRepo(name: String): File? {
        if (name.isBlank()) return null
        val repos = try { git.listRepos() } catch (e: Exception) { return null }
        return repos.find { it.name.equals(name, ignoreCase = true) }
            ?: repos.find { it.name.contains(name, ignoreCase = true) }
    }
}
