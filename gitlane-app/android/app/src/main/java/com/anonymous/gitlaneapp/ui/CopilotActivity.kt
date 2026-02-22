package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.gitlaneapp.BranchInfo
import com.anonymous.gitlaneapp.CommitInfo
import com.anonymous.gitlaneapp.GitStatus
import com.anonymous.gitlaneapp.TagInfo
import com.anonymous.gitlaneapp.copilot.CopilotEngine
import com.anonymous.gitlaneapp.copilot.CopilotResult
import com.anonymous.gitlaneapp.data.api.GroqMessage
import kotlinx.coroutines.launch
import java.io.File

// ─── Chat message model ───────────────────────────────────────────────────────

sealed class ChatMsg {
    data class User(val text: String) : ChatMsg()
    data class Bot(val text: String) : ChatMsg()
    object Thinking : ChatMsg()
    data class Commits(val intro: String, val commits: List<CommitInfo>, val repoDir: File) : ChatMsg()
    data class Branches(val intro: String, val branches: List<BranchInfo>, val current: String, val repoDir: File) : ChatMsg()
    data class Health(val result: CopilotResult.RepoHealth) : ChatMsg()
    data class CommitExplain(val result: CopilotResult.CommitExplain) : ChatMsg()
    data class CommitMsg(val result: CopilotResult.CommitMsgSuggestion, var used: Boolean = false) : ChatMsg()
    data class Conflict(val result: CopilotResult.ConflictExplain) : ChatMsg()
    data class Tags(val result: CopilotResult.TagList) : ChatMsg()
    data class Action(val summary: String, val success: Boolean) : ChatMsg()
}

// ─── Activity ────────────────────────────────────────────────────────────────

class CopilotActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
    }

    private val engine by lazy { CopilotEngine(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH)

        setContent {
            CopilotScreen(
                currentRepoPath = repoPath,
                onProcess = { msg, history, repoP, pAction, pParams ->
                    engine.process(msg, history, repoP, pAction, pParams)
                },
                onOpenRepo = { path ->
                    startActivity(Intent(this, RepoDetailActivity::class.java).apply {
                        putExtra(RepoDetailActivity.EXTRA_REPO_PATH, path)
                        putExtra(RepoDetailActivity.EXTRA_REPO_NAME, File(path).name)
                    })
                },
                onClose = { finish() }
            )
        }
    }
}

// ─── Colour palette (shared) ──────────────────────────────────────────────────

private val BG       = Color(0xFF080B14)
private val Surface1 = Color(0xFF111422)
private val Surface2 = Color(0xFF181C2E)
private val Accent   = Color(0xFF7B6EF6)   // purple
private val AccentB  = Color(0xFF00D4FF)   // cyan
private val Green    = Color(0xFF2ECC71)
private val Red      = Color(0xFFE74C3C)
private val Yellow   = Color(0xFFF39C12)
private val TextP    = Color(0xFFE8EAF6)
private val TextS    = Color(0xFF7880A8)
private val AccentGrad = listOf(Accent, AccentB)

// ─── Main screen ─────────────────────────────────────────────────────────────

@Composable
fun CopilotScreen(
    currentRepoPath: String?,
    onProcess: suspend (String, List<GroqMessage>, String?, String?, Map<String, String>) -> CopilotResult,
    onOpenRepo: (String) -> Unit,
    onClose: () -> Unit
) {
    val messages      = remember { mutableStateListOf<ChatMsg>() }
    val groqHistory   = remember { mutableStateListOf<GroqMessage>() }
    var input         by remember { mutableStateOf("") }
    var isThinking    by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var pendingParams by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (currentRepoPath != null) {
            val repoName = File(currentRepoPath).name
            messages.add(ChatMsg.Bot(
                "✦ **GitLane Copilot** — wired into **$repoName**\n\n" +
                "I know everything about this repo. Just ask:\n\n" +
                "• *\"Create a branch for the login feature\"*\n" +
                "• *\"Show commits from last week\"*\n" +
                "• *\"What did the latest commit do?\"*\n" +
                "• *\"Is this branch behind main?\"*\n" +
                "• *\"Generate a commit message\"*\n" +
                "• *\"Why is there a conflict?\"*\n\n" +
                "No need to mention the repo name — I'm already in **$repoName**. 🎯"
            ))
        } else {
            messages.add(ChatMsg.Bot(
                "👋 I'm **GitLane Copilot** — your AI Git Intelligence Assistant.\n\n" +
                "💡 **Tip:** Open Copilot from inside a repo for the best experience — I'll automatically know which repo to work with.\n\n" +
                "I can:\n" +
                "• 🌿 **Branch** — create, switch, merge, delete\n" +
                "• 🔍 **Search** commits semantically (*\"last week's auth fixes\"*)\n" +
                "• 💡 **Explain** commits & merge conflicts\n" +
                "• 📝 **Generate** commit messages from staged files\n" +
                "• 🏥 **Analyse** repo health (ahead/behind)\n" +
                "• 📋 **Generate** README automatically"
            ))
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send(text: String) {
        if (text.isBlank() || isThinking) return
        val t = text.trim()
        messages.add(ChatMsg.User(t))
        groqHistory.add(GroqMessage("user", t))
        input = ""
        isThinking = true
        messages.add(ChatMsg.Thinking)

        scope.launch {
            val result = onProcess(t, groqHistory.toList(), currentRepoPath, pendingAction, pendingParams)
            val idx = messages.indexOfLast { it is ChatMsg.Thinking }
            if (idx >= 0) messages.removeAt(idx)
            isThinking = false
            val savedPending = pendingAction
            pendingAction = null
            pendingParams = emptyMap()

            when (result) {
                is CopilotResult.Text -> {
                    messages.add(ChatMsg.Bot(result.message))
                    groqHistory.add(GroqMessage("assistant", result.message))
                }
                is CopilotResult.CommitList -> {
                    messages.add(ChatMsg.Commits(result.intro, result.commits, result.repoDir))
                    groqHistory.add(GroqMessage("assistant", "${result.intro} (${result.commits.size} commits)"))
                }
                is CopilotResult.BranchList -> {
                    messages.add(ChatMsg.Branches(result.intro, result.branches, result.currentBranch, result.repoDir))
                    groqHistory.add(GroqMessage("assistant", "${result.intro} (${result.branches.size} branches)"))
                }
                is CopilotResult.RepoHealth -> {
                    messages.add(ChatMsg.Health(result))
                    groqHistory.add(GroqMessage("assistant", "Repo health for ${result.repoName} on ${result.branch}"))
                }
                is CopilotResult.CommitExplain -> {
                    messages.add(ChatMsg.CommitExplain(result))
                    groqHistory.add(GroqMessage("assistant", "Explained commit ${result.commit.sha}"))
                }
                is CopilotResult.CommitMsgSuggestion -> {
                    messages.add(ChatMsg.CommitMsg(result))
                    groqHistory.add(GroqMessage("assistant", "Generated commit message: ${result.suggestedMessage}"))
                }
                is CopilotResult.ConflictExplain -> {
                    messages.add(ChatMsg.Conflict(result))
                    groqHistory.add(GroqMessage("assistant", "Explained ${result.conflictingFiles.size} conflicts in ${result.repoDir.name}"))
                }
                is CopilotResult.TagList -> {
                    messages.add(ChatMsg.Tags(result))
                    groqHistory.add(GroqMessage("assistant", "${result.tags.size} tags in ${result.repoName}"))
                }
                is CopilotResult.ActionDone -> {
                    if (result.summary.startsWith("__OPEN_REPO__:")) {
                        onOpenRepo(result.summary.removePrefix("__OPEN_REPO__:"))
                    } else {
                        messages.add(ChatMsg.Action(result.summary, result.success))
                        groqHistory.add(GroqMessage("assistant", result.summary))
                    }
                }
                is CopilotResult.NeedInput -> {
                    pendingAction = result.pendingAction
                    pendingParams = result.collected
                    messages.add(ChatMsg.Bot(result.question))
                    groqHistory.add(GroqMessage("assistant", result.question))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────
            Header(currentRepoPath, onClose)

            // ── Chat messages ─────────────────────────────────────────────
            LazyColumn(
                state            = listState,
                modifier         = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding   = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { messages.indexOf(it) }) { msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                    ) {
                        when (msg) {
                            is ChatMsg.User        -> UserBubble(msg.text)
                            is ChatMsg.Bot         -> BotBubble(msg.text)
                            is ChatMsg.Thinking    -> ThinkingBubble()
                            is ChatMsg.Commits     -> CommitsCard(msg)
                            is ChatMsg.Branches    -> BranchesCard(msg)
                            is ChatMsg.Health      -> HealthCard(msg.result)
                            is ChatMsg.CommitExplain -> CommitExplainCard(msg.result)
                            is ChatMsg.CommitMsg   -> CommitMsgCard(msg) { send("stage and commit: ${msg.result.suggestedMessage}") }
                            is ChatMsg.Conflict    -> ConflictCard(msg.result) { openPath ->
                                // Could navigate to conflict resolution screen
                            }
                            is ChatMsg.Tags        -> TagsCard(msg.result)
                            is ChatMsg.Action      -> ActionCard(msg.summary, msg.success)
                        }
                    }
                }
            }

            // ── Quick prompts (only at start) ─────────────────────────────
            if (messages.size <= 1 && !isThinking) {
                QuickPrompts { send(it) }
            }

            // ── Input bar ─────────────────────────────────────────────────
            InputBar(
                value     = input,
                onChange  = { input = it },
                onSend    = { send(input) },
                enabled   = !isThinking,
                pending   = pendingAction
            )
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun Header(repoPath: String?, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1040), Color(0xFF0D1528))))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pulsing avatar
            val inf = rememberInfiniteTransition(label = "pulse")
            val scale by inf.animateFloat(
                initialValue = 0.95f, targetValue = 1.05f, label = "s",
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse)
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(AccentGrad))
            ) { Text("✦", fontSize = 18.sp) }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GitLane Copilot", color = TextP, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    repoPath?.let { "📂 ${File(it).name}" } ?: "Git Intelligence Assistant",
                    color = TextS, fontSize = 11.sp
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextS)
            }
        }
    }
}

// ─── Bubbles ──────────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                .background(Brush.linearGradient(AccentGrad))
                .padding(12.dp, 10.dp)
        ) { Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 21.sp) }
    }
}

@Composable
private fun BotBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
        BotAvatar()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(Surface1)
                .padding(14.dp, 10.dp)
        ) { Text(text, color = TextP, fontSize = 14.sp, lineHeight = 21.sp) }
    }
}

@Composable
private fun ThinkingBubble() {
    val inf = rememberInfiniteTransition(label = "dots")
    val alpha by inf.animateFloat(0.3f, 1f, label = "a",
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BotAvatar()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(Surface1).padding(16.dp, 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    val dotAlpha by rememberInfiniteTransition(label = "d$i").animateFloat(
                        0.2f, 1f, label = "d",
                        animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse)
                    )
                    Box(Modifier.size(7.dp).clip(CircleShape).background(AccentB.copy(alpha = dotAlpha)))
                }
                Spacer(Modifier.width(6.dp))
                Text("Thinking…", color = AccentB.copy(alpha = alpha), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BotAvatar() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(28.dp).clip(CircleShape).background(Accent.copy(alpha = 0.2f))
    ) { Text("✦", fontSize = 12.sp, color = Accent) }
}

// ─── Commits Card ─────────────────────────────────────────────────────────────

@Composable
private fun CommitsCard(msg: ChatMsg.Commits) {
    Column {
        BotBubble(msg.intro)
        Spacer(Modifier.height(6.dp))
        if (msg.commits.isEmpty()) {
            BotBubble("📭 No commits matched that query.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // CAP to 15 commits to prevent ANR on large repos
                msg.commits.take(15).forEach { c -> CommitRow(c) }
            }
            Text(
                if (msg.commits.size > 15) "🔢 Showing top 15 of ${msg.commits.size} commits"
                else "🔢 ${msg.commits.size} commit${if (msg.commits.size != 1) "s" else ""}",
                color = TextS, fontSize = 11.sp, modifier = Modifier.padding(start = 36.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun CommitRow(c: CommitInfo) {
    Row(
        Modifier.fillMaxWidth().padding(start = 36.dp)
            .clip(RoundedCornerShape(10.dp)).background(Surface2)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Accent.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(c.sha.take(7), color = Accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(c.message, color = TextP, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("👤 ${c.author}  •  📅 ${c.date}", color = TextS, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (c.isConflict) Text("⚡", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

// ─── Branches Card ────────────────────────────────────────────────────────────

@Composable
private fun BranchesCard(msg: ChatMsg.Branches) {
    Column {
        BotBubble(msg.intro)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // CAP to 12 branches to prevent ANR on repos with 1000s of branches
            msg.branches.take(12).forEach { b ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (b.isCurrent) Accent.copy(alpha = 0.12f) else Surface2)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (b.isCurrent) "● " else "○ ",
                        color = if (b.isCurrent) Accent else TextS, fontSize = 14.sp
                    )
                    Text(b.name, color = if (b.isCurrent) Accent else TextP, fontSize = 13.sp,
                        fontWeight = if (b.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f))
                    if (b.ahead > 0 || b.behind > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (b.ahead > 0)  Badge("↑${b.ahead}", Green)
                            if (b.behind > 0) Badge("↓${b.behind}", Yellow)
                        }
                    }
                    b.upstream?.let { Text(it, color = TextS, fontSize = 10.sp,
                        modifier = Modifier.padding(start = 6.dp), maxLines = 1) }
                }
            }
            if (msg.branches.size > 12) {
                Text(
                    "and ${msg.branches.size - 12} more branches...",
                    color = TextS, fontSize = 12.sp, modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ─── Repo Health Card ─────────────────────────────────────────────────────────

@Composable
private fun HealthCard(r: CopilotResult.RepoHealth) {
    Column(Modifier.padding(start = 36.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Surface2).padding(14.dp)
        ) {
            Column {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.linearGradient(AccentGrad))
                    ) { Text("🏥", fontSize = 18.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(r.repoName, color = TextP, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("🌿 ${r.branch}", color = Accent, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.06f))

                // Stats row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("📦 Commits", r.totalCommits.toString(), AccentB)
                    r.aheadBehind?.let {
                        StatChip("↑ Ahead",  it.ahead.toString(),  Green)
                        StatChip("↓ Behind", it.behind.toString(), Yellow)
                    }
                }

                // Status
                if (r.status.hasChanges()) {
                    Spacer(Modifier.height(10.dp))
                    Text("📝 Uncommitted Changes", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    if (r.status.modified.isNotEmpty())
                        Text("  Modified: ${r.status.modified.take(3).joinToString(", ")}", color = TextS, fontSize = 11.sp)
                    if (r.status.untracked.isNotEmpty())
                        Text("  Untracked: ${r.status.untracked.take(3).joinToString(", ")}", color = TextS, fontSize = 11.sp)
                    if (r.status.conflicting.isNotEmpty())
                        Text("  ⚠️ Conflicts: ${r.status.conflicting.joinToString(", ")}", color = Red, fontSize = 11.sp)
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("✅ Working tree clean", color = Green, fontSize = 12.sp)
                }

                // Latest commit
                r.latestCommit?.let { c ->
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Spacer(Modifier.height(8.dp))
                    Text("📌 Latest Commit", color = TextS, fontSize = 11.sp)
                    Text(c.message, color = TextP, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${c.sha.take(7)} • ${c.author} • ${c.date}", color = TextS, fontSize = 10.sp)
                }

                // Health advice
                if (r.aheadBehind != null) {
                    Spacer(Modifier.height(10.dp))
                    val advice = when {
                        r.aheadBehind.behind > 5 -> "⚠️ You're ${r.aheadBehind.behind} commits behind. Consider rebasing onto main."
                        r.aheadBehind.ahead  > 10 -> "📤 ${r.aheadBehind.ahead} commits ahead — time to push or open a PR!"
                        r.aheadBehind.behind > 0  -> "💡 ${r.aheadBehind.behind} commits behind. A quick rebase keeps history clean."
                        r.aheadBehind.ahead  > 0  -> "✅ Ahead of base. Ready to push when you like."
                        else                       -> "✅ In sync with base branch."
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Accent.copy(alpha = 0.1f)).padding(10.dp)
                    ) {
                        Text(advice, color = TextP, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextS, fontSize = 10.sp)
    }
}

// ─── Commit Explain Card ──────────────────────────────────────────────────────

@Composable
private fun CommitExplainCard(r: CopilotResult.CommitExplain) {
    Column(Modifier.padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Commit header
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Surface2).padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(r.commit.sha.take(7), color = Accent, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(r.commit.message, color = TextP, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Text("👤 ${r.commit.author}  •  📅 ${r.commit.date}", color = TextS, fontSize = 11.sp)
            }
        }

        // AI explanation
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D1F0D)).padding(12.dp)
        ) {
            Column {
                Text("🤖 AI Analysis", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(r.aiExplanation, color = TextP, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        // Diff snippet
        if (r.diff.isNotBlank() && r.diff.length > 10) {
            var showDiff by remember { mutableStateOf(false) }
            TextButton(onClick = { showDiff = !showDiff }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    if (showDiff) "▼ Hide diff" else "▶ Show diff",
                    color = AccentB, fontSize = 12.sp
                )
            }
            if (showDiff) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A0A12)).padding(10.dp)
                ) {
                    Text(
                        r.diff.take(1000) + if (r.diff.length > 1000) "\n… (truncated)" else "",
                        color = Color(0xFF98C379), fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ─── Commit Message Suggestion Card ──────────────────────────────────────────

@Composable
private fun CommitMsgCard(msg: ChatMsg.CommitMsg, onUse: () -> Unit) {
    Column(Modifier.padding(start = 36.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Surface2).padding(14.dp)
        ) {
            Column {
                Text("📝 Commit Message Suggestion", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                // Message preview
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A0A18)).padding(10.dp)
                ) {
                    Text(
                        msg.result.suggestedMessage,
                        color = Color(0xFFF8F8F2), fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(8.dp))
                // Changed files
                Text("Files staged (${msg.result.stagedFiles.size}):", color = TextS, fontSize = 11.sp)
                msg.result.stagedFiles.take(4).forEach {
                    Text("  • $it", color = TextS, fontSize = 11.sp)
                }
                if (msg.result.stagedFiles.size > 4) {
                    Text("  … and ${msg.result.stagedFiles.size - 4} more", color = TextS, fontSize = 11.sp)
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!msg.used) {
                        Button(
                            onClick = { msg.used = true; onUse() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Accent),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("✅ Use & Commit", fontSize = 12.sp, color = Color.White)
                        }
                    } else {
                        Text("✅ Committed!", color = Green, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─── Conflict Card ────────────────────────────────────────────────────────────

@Composable
private fun ConflictCard(r: CopilotResult.ConflictExplain, onResolve: (String) -> Unit) {
    Column(Modifier.padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Warning header
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Red.copy(alpha = 0.1f))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Merge Conflict Detected", color = Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${r.conflictingFiles.size} file(s) in **${r.repoDir.name}** on `${r.currentBranch}`",
                            color = TextS, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                r.conflictingFiles.forEach { file ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Red))
                        Spacer(Modifier.width(8.dp))
                        Text(file, color = TextP, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // AI explanation
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF110B0B)).padding(12.dp)
        ) {
            Column {
                Text("🤖 AI Conflict Analysis", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(r.aiExplanation, color = TextP, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onResolve(r.repoDir.absolutePath) },
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                border  = ButtonDefaults.outlinedButtonBorder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Open Conflict Resolver", fontSize = 11.sp)
            }
        }
    }
}

// ─── Tags Card ────────────────────────────────────────────────────────────────

@Composable
private fun TagsCard(r: CopilotResult.TagList) {
    Column(Modifier.padding(start = 36.dp)) {
        BotBubble("🏷️ Found **${r.tags.size}** tag${if (r.tags.size != 1) "s" else ""} in **${r.repoName}**")
        Spacer(Modifier.height(6.dp))
        if (r.tags.isEmpty()) {
            BotBubble("No tags yet. Ask me to create one — e.g. *\"create tag v1.0.0\"*")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.tags.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Surface2).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏷️", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, color = Accent, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${t.sha.take(7)}  •  ${t.date}", color = TextS, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ─── Action Card ─────────────────────────────────────────────────────────────

@Composable
private fun ActionCard(summary: String, success: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 310.dp).padding(start = 36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (success) Green.copy(alpha = 0.08f) else Red.copy(alpha = 0.08f))
                .padding(12.dp)
        ) {
            Text(summary, color = TextP, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

// ─── Quick prompts ────────────────────────────────────────────────────────────

@Composable
private fun QuickPrompts(onTap: (String) -> Unit) {
    val chips = listOf(
        "🏥 Repo health",
        "🌿 List branches",
        "📂 List files",
        "📖 Read README.md",
        "🔍 Recent commits",
        "💡 Explain latest commit",
        "📝 Generate commit msg",
        "📋 Generate README"
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("💬 Try asking:", color = TextS, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(chips) { chip ->
                SuggestionChip(
                    onClick = { onTap(chip.drop(2)) },
                    label   = { Text(chip, fontSize = 12.sp, color = Accent) },
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Accent.copy(alpha = 0.08f)
                    ),
                    border  = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true, borderColor = Accent.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

// ─── Input bar ────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    pending: String?
) {
    Surface(color = Surface1, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (pending != null) {
                Box(
                    Modifier.fillMaxWidth().background(Accent.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("💬 Answering: $pending", color = Accent, fontSize = 11.sp)
                }
            }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value          = value,
                    onValueChange  = onChange,
                    modifier       = Modifier.weight(1f),
                    placeholder    = {
                        Text(
                            if (pending != null) "Type your answer…"
                            else "Ask anything about your repos…",
                            color = TextS, fontSize = 13.sp
                        )
                    },
                    colors         = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Accent,
                        unfocusedBorderColor = Color(0xFF2A2D45),
                        focusedTextColor     = TextP,
                        unfocusedTextColor   = TextP,
                        cursorColor          = Accent
                    ),
                    shape          = RoundedCornerShape(24.dp),
                    singleLine     = false,
                    maxLines       = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    enabled        = enabled
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = onSend,
                    enabled  = value.isNotBlank() && enabled,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(
                            if (value.isNotBlank() && enabled)
                                Brush.linearGradient(AccentGrad)
                            else
                                Brush.linearGradient(listOf(Color(0xFF2A2D45), Color(0xFF2A2D45)))
                        )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}
