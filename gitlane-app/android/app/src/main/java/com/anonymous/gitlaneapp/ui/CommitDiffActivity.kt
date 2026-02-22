package com.anonymous.gitlaneapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ViewColumn
import com.anonymous.gitlaneapp.engine.DiffEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

/**
 * CommitDiffActivity
 *
 * Shows the exact code changes for a specific commit.
 * Features a ScrollableTabRow for file selection and a Toggle for Split vs Unified view.
 */
class CommitDiffActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REPO_PATH = "extra_repo_path"
        const val EXTRA_COMMIT_SHA = "extra_commit_sha"
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: run { finish(); return }
        val sha = intent.getStringExtra(EXTRA_COMMIT_SHA) ?: run { finish(); return }
        val initialFile = intent.getStringExtra(EXTRA_FILE_PATH)
        val repoDir = File(repoPath)

        setContent {
            CommitDiffScreen(repoDir, sha, initialFile, onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDiffScreen(repoDir: File, sha: String, initialFile: String?, onBack: () -> Unit) {
    var diffs by remember { mutableStateOf<List<DiffEngine.FileDiff>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFileIndex by remember { mutableStateOf(0) }
    var isSplitView by remember { mutableStateOf(false) }

    LaunchedEffect(sha) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val repo = FileRepositoryBuilder()
                    .setGitDir(File(repoDir, ".git"))
                    .build()
                val diffEngine = DiffEngine(repo)
                val commitId = repo.resolve(sha)
                val walk = RevWalk(repo)
                val commit = walk.parseCommit(commitId)
                val results = diffEngine.getCommitChanges(commit)
                diffs = results
                
                if (initialFile != null) {
                    val idx = results.indexOfFirst { it.path == initialFile }
                    if (idx >= 0) selectedFileIndex = idx
                }
                repo.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoading = false
    }

    MaterialTheme(colorScheme = darkColorScheme(
        surface = Color(0xFF1E293B),
        background = Color(0xFF0F172A),
        primary = Color(0xFF38BDF8)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Commit Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(sha.take(8), fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSplitView = !isSplitView }) {
                            Icon(
                                if (isSplitView) Icons.Filled.ViewStream else Icons.Filled.ViewColumn,
                                contentDescription = "Toggle View",
                                tint = if (isSplitView) Color(0xFF38BDF8) else Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E293B),
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else if (diffs.isEmpty()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
                    Text("No changes found", color = Color.Gray)
                }
            } else {
                Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFF0F172A))) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedFileIndex,
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF38BDF8),
                        edgePadding = 16.dp,
                        divider = {}
                    ) {
                        diffs.forEachIndexed { index, diff ->
                            Tab(
                                selected = selectedFileIndex == index,
                                onClick = { selectedFileIndex = index },
                                text = {
                                    Text(
                                        diff.path.substringAfterLast("/"),
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedFileIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            )
                        }
                    }

                    val currentDiff = diffs[selectedFileIndex]
                    
                    Column(Modifier.fillMaxSize()) {
                        Surface(
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(currentDiff.path, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                Text("+${currentDiff.linesAdded}", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text("-${currentDiff.linesRemoved}", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isSplitView) {
                            SplitDiffView(currentDiff.diffText)
                        } else {
                            UnifiedDiffView(currentDiff.diffText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedDiffView(diffText: String) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        items(diffText.lines()) { line ->
            DiffLine(line)
        }
    }
}

@Composable
fun SplitDiffView(diffText: String) {
    // Process diff text to align lines
    val leftLines = mutableListOf<String?>()
    val rightLines = mutableListOf<String?>()
    
    val lines = diffText.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("-") && !line.startsWith("---") -> {
                leftLines.add(line)
                // Check if next is a + to align
                if (i + 1 < lines.size && lines[i+1].startsWith("+") && !lines[i+1].startsWith("+++")) {
                    rightLines.add(lines[i+1])
                    i += 2
                } else {
                    rightLines.add(null)
                    i++
                }
            }
            line.startsWith("+") && !line.startsWith("+++") -> {
                leftLines.add(null)
                rightLines.add(line)
                i++
            }
            else -> {
                leftLines.add(line)
                rightLines.add(line)
                i++
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        items(leftLines.size) { index ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.weight(1f)) {
                    val line = leftLines[index]
                    if (line != null) DiffLine(line, isSplit = true) else Spacer(Modifier.fillMaxSize().background(Color(0xFF161616)))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.DarkGray))
                Box(Modifier.weight(1f)) {
                    val line = rightLines[index]
                    if (line != null) DiffLine(line, isSplit = true) else Spacer(Modifier.fillMaxSize().background(Color(0xFF161616)))
                }
            }
        }
    }
}

@Composable
fun DiffLine(line: String, isSplit: Boolean = false) {
    val isAdded = line.startsWith("+") && !line.startsWith("+++")
    val isRemoved = line.startsWith("-") && !line.startsWith("---")
    val isHeader = line.startsWith("@@")

    val backgroundColor = when {
        isAdded -> Color(0xFF238636).copy(alpha = 0.15f)
        isRemoved -> Color(0xFFDA3633).copy(alpha = 0.15f)
        isHeader -> Color(0xFF388BFD).copy(alpha = 0.1f)
        else -> Color.Transparent
    }

    val textColor = when {
        isAdded -> Color(0xFF4ADE80)
        isRemoved -> Color(0xFFF87171)
        isHeader -> Color(0xFF7D8590)
        else -> Color(0xFFE6EDF3)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = if (isSplit) 8.dp else 16.dp, vertical = 2.dp)
    ) {
        Text(
            text = line,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
