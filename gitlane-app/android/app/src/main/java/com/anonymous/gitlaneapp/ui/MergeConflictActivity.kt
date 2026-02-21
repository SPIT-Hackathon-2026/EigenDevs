package com.anonymous.gitlaneapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.data.SettingsRepository
import com.anonymous.gitlaneapp.ui.viewmodel.ConflictSegment
import com.anonymous.gitlaneapp.ui.viewmodel.MergeConflictViewModel
import com.anonymous.gitlaneapp.ui.viewmodel.ResolutionType
import kotlinx.coroutines.launch
import java.io.File

class MergeConflictActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_REPO_PATH = "extra_repo_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: return finish()
        val repoPath = intent.getStringExtra(EXTRA_REPO_PATH) ?: return finish()

        val repoDir = File(repoPath)
        val relativePath = File(filePath).absolutePath.removePrefix(repoDir.absolutePath).removePrefix("/")
        
        val settings = SettingsRepository(this)
        val viewModel = MergeConflictViewModel(GitManager(this), repoDir, relativePath, settings)

        setContent {
            MergeConflictScreen(viewModel) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeConflictScreen(viewModel: MergeConflictViewModel, onSaved: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredSegments = remember(state.segments, searchQuery) {
        if (searchQuery.isBlank()) state.segments
        else state.segments.filter { segment ->
            when (segment) {
                is ConflictSegment.Normal -> segment.content.contains(searchQuery, ignoreCase = true)
                is ConflictSegment.Conflict -> 
                    segment.headContent.contains(searchQuery, ignoreCase = true) || 
                    segment.incomingContent.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val totalConflicts = state.segments.count { it is ConflictSegment.Conflict }
    val resolvedConflicts = state.segments.count { it is ConflictSegment.Conflict && it.resolution != ResolutionType.NONE }
    val progress = if (totalConflicts > 0) resolvedConflicts.toFloat() / totalConflicts else 1f

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF64FFDA), // Teal/Seafoam
            surface = Color(0xFF1A1A1A),
            background = Color(0xFF0D1117), // GitHub Dark
            onSurface = Color(0xFFC9D1D9),
            secondary = Color(0xFF3B82F6)
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                    drawerContainerColor = Color(0xFF161B22)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            "REPOSITORIES",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        NavigationDrawerItem(
                            label = { Text(state.fileName, fontWeight = FontWeight.Bold) },
                            selected = true,
                            onClick = { },
                            icon = { Icon(Icons.Default.Description, null) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFF64FFDA).copy(alpha = 0.1f),
                                selectedIconColor = Color(0xFF64FFDA),
                                selectedTextColor = Color(0xFF64FFDA)
                            )
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    Column {
                        if (isSearchActive) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                color = Color(0xFF161B22),
                                shadowElevation = 8.dp
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxSize(),
                                    placeholder = { Text("Search logic...", color = Color.Gray) },
                                    leadingIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                        } else {
                            TopAppBar(
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, "Menu")
                                    }
                                },
                                title = {
                                    Column {
                                        Text(state.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                        Text("$resolvedConflicts of $totalConflicts conflicts resolved", style = MaterialTheme.typography.labelSmall, color = if (progress == 1f) Color(0xFF22C55E) else Color.Gray)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, null) }
                                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, null) }
                                    IconButton(onClick = { viewModel.redo() }, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, null) }
                                    IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) { Icon(Icons.Default.Settings, null) }
                                    Button(
                                        onClick = { viewModel.save(onSaved) },
                                        enabled = !state.isSaving,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                        else Text("Save", fontWeight = FontWeight.Bold)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
                            )
                        }
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF64FFDA),
                            trackColor = Color.Transparent
                        )
                    }
                },
                containerColor = Color(0xFF0D1117)
            ) { padding ->
                Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                    // Modern Conflict Heatmap
                    Column(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF0D1117))
                            .padding(vertical = 2.dp)
                    ) {
                        state.segments.forEach { segment ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(
                                        when {
                                            segment is ConflictSegment.Conflict && segment.resolution == ResolutionType.NONE -> Color(0xFFF85149).copy(alpha = 0.5f)
                                            segment is ConflictSegment.Conflict -> Color(0xFF238636).copy(alpha = 0.3f)
                                            else -> Color.Transparent
                                        }
                                    )
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        state = rememberLazyListState()
                    ) {
                        itemsIndexed(filteredSegments) { index, segment ->
                            when (segment) {
                                is ConflictSegment.Normal -> CodeBlock(segment.content)
                                is ConflictSegment.Conflict -> GodTierConflictBlock(
                                    index = index,
                                    segment = segment,
                                    onResolve = { viewModel.resolve(index, it) },
                                    onExplain = { viewModel.explainConflictWithAi(index) },
                                    onApplyAi = { suggestion -> viewModel.applyAiSuggestion(index, suggestion) }
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(120.dp)) }
                    }
                }

                if (state.error != null) {
                    Toast.makeText(context, state.error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun CodeBlock(content: String) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color(0xFFC9D1D9),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun GodTierConflictBlock(
    index: Int,
    segment: ConflictSegment.Conflict,
    onResolve: (ResolutionType) -> Unit,
    onExplain: () -> Unit,
    onApplyAi: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        // Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (segment.resolution == ResolutionType.NONE) Color(0xFF3B82F6).copy(alpha = 0.1f) else Color(0xFF238636).copy(alpha = 0.1f))
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(if (segment.resolution == ResolutionType.NONE) Color(0xFFF85149) else Color(0xFF238636), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            Text(
                "CONFLICT #${index + 1}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            if (segment.resolution != ResolutionType.NONE) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF238636), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                // Variations with Labels
                CodeVariant(
                    label = "CURRENT (HEAD)",
                    content = segment.headContent,
                    color = Color(0xFF3B82F6),
                    onAccept = { onResolve(ResolutionType.HEAD) }
                )
                
                if (segment.baseContent != null) {
                    CodeVariant(
                        label = "ORIGINAL (BASE)",
                        content = segment.baseContent,
                        color = Color(0xFF8B949E),
                        onAccept = {}
                    )
                }

                CodeVariant(
                    label = "INCOMING",
                    content = segment.incomingContent,
                    color = Color(0xFF22C55E),
                    onAccept = { onResolve(ResolutionType.INCOMING) }
                )

                // AI Intelligence Layer
                HorizontalDivider(color = Color(0xFF30363D))
                AiIntelligenceLayer(segment, onExplain, onApplyAi)

                // Actions
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onResolve(ResolutionType.BOTH) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Accept Both", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onExplain,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Explain", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CodeVariant(
    label: String,
    content: String,
    color: Color,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.03f))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = color.copy(alpha = 0.8f))
            Spacer(Modifier.weight(1f))
            if (label != "ORIGINAL (BASE)") {
                Text(
                    "ACCEPT",
                    modifier = Modifier.clickable { onAccept() },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFFC9D1D9),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AiIntelligenceLayer(
    segment: ConflictSegment.Conflict,
    onExplain: () -> Unit,
    onApplyAi: (String) -> Unit
) {
    if (segment.isAiLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF8B5CF6))
    } else if (segment.aiSuggestion != null) {
        val full = segment.aiSuggestion
        val action = full.substringAfter("SUGGESTED ACTION:").substringBefore("\n").trim()
        val explanation = full.substringAfter("EXPLANATION:").substringBefore("SUGGESTED ACTION:").trim()
        val risks = full.substringAfter("RISKS:").substringBefore("SUGGESTION:").trim()

        Column(modifier = Modifier.background(Color(0xFF8B5CF6).copy(alpha = 0.05f)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(12.dp))
                Text("AI ASSISTANT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF8B5CF6))
                Spacer(Modifier.weight(1f))
                Text(
                    "USE THIS",
                    modifier = Modifier.clickable { onApplyAi(full) },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(8.dp))
            if (action.isNotEmpty()) {
                Text("Recommendation: $action", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
            }
            Text(explanation, fontSize = 12.sp, color = Color.Gray)
            if (risks.isNotEmpty()) {
                Text("⚠️ $risks", fontSize = 11.sp, color = Color(0xFFFCA5A5), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
