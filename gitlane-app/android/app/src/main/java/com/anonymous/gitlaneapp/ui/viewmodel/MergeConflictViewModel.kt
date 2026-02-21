package com.anonymous.gitlaneapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anonymous.gitlaneapp.GitManager
import com.anonymous.gitlaneapp.data.SettingsRepository
import com.anonymous.gitlaneapp.data.api.GroqApiService
import com.anonymous.gitlaneapp.data.api.GroqMessage
import com.anonymous.gitlaneapp.data.api.GroqRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.*

sealed class ConflictSegment {
    data class Normal(val content: String) : ConflictSegment()
    data class Conflict(
        val headContent: String,
        val incomingContent: String,
        val baseContent: String? = null, // Original version if available
        val resolvedContent: String? = null,
        val resolution: ResolutionType = ResolutionType.NONE,
        val aiSuggestion: String? = null,
        val isAiLoading: Boolean = false
    ) : ConflictSegment()
}

enum class ResolutionType { NONE, HEAD, INCOMING, BOTH }

data class MergeUiState(
    val fileName: String = "",
    val segments: List<ConflictSegment> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val aiExplanation: String? = null
)

class MergeConflictViewModel(
    val git: GitManager,
    private val repoDir: File,
    private val relativePath: String,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergeUiState(fileName = relativePath.split("/").last()))
    val uiState: StateFlow<MergeUiState> = _uiState

    private val history = Stack<List<ConflictSegment>>()
    private val redoStack = Stack<List<ConflictSegment>>()

    private val groqService by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(GroqApiService::class.java)
    }

    init {
        loadConflict()
    }

    private fun loadHistoryState() {
        _uiState.value = _uiState.value.copy(
            canUndo = history.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    private fun pushToHistory() {
        history.push(_uiState.value.segments.toList())
        redoStack.clear()
        loadHistoryState()
    }

    fun undo() {
        if (history.isNotEmpty()) {
            redoStack.push(_uiState.value.segments.toList())
            val previous = history.pop()
            _uiState.value = _uiState.value.copy(segments = previous)
            loadHistoryState()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            history.push(_uiState.value.segments.toList())
            val next = redoStack.pop()
            _uiState.value = _uiState.value.copy(segments = next)
            loadHistoryState()
        }
    }

    private fun loadConflict() {
        viewModelScope.launch {
            try {
                val rawContent = git.readFile(repoDir, relativePath)
                val baseFallback = git.getConflictStageContent(repoDir, relativePath, 1)
                val parsedSegments = parseConflicts(rawContent, baseFallback)
                _uiState.value = _uiState.value.copy(segments = parsedSegments)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun parseConflicts(content: String, baseFallback: String? = null): List<ConflictSegment> {
        val lines = content.lines()
        val segments = mutableListOf<ConflictSegment>()
        var i = 0
        val currentNormal = StringBuilder()

        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("<<<<<<<")) {
                if (currentNormal.isNotEmpty()) {
                    segments.add(ConflictSegment.Normal(currentNormal.toString().trimEnd()))
                    currentNormal.clear()
                }

                val head = StringBuilder()
                val base = StringBuilder()
                val incoming = StringBuilder()

                i++ // skip <<<<<<<
                while (i < lines.size && !lines[i].startsWith("=======") && !lines[i].startsWith("|||||||")) {
                    head.appendLine(lines[i])
                    i++
                }

                if (i < lines.size && lines[i].startsWith("|||||||")) {
                    i++ // skip |||||||
                    while (i < lines.size && !lines[i].startsWith("=======")) {
                        base.appendLine(lines[i])
                        i++
                    }
                }

                if (i < lines.size && lines[i].startsWith("=======")) {
                    i++ // skip =======
                    while (i < lines.size && !lines[i].startsWith(">>>>>>>")) {
                        incoming.appendLine(lines[i])
                        i++
                    }
                }
                
                if (i < lines.size && lines[i].startsWith(">>>>>>>")) {
                    // skip >>>>>>>
                }

                segments.add(ConflictSegment.Conflict(
                    headContent = head.toString().trimEnd(),
                    incomingContent = incoming.toString().trimEnd(),
                    baseContent = if (base.isNotEmpty()) base.toString().trimEnd() else baseFallback
                ))
            } else {
                currentNormal.appendLine(line)
            }
            i++
        }

        if (currentNormal.isNotEmpty()) {
            segments.add(ConflictSegment.Normal(currentNormal.toString().trimEnd()))
        }

        return segments
    }

    fun resolve(index: Int, type: ResolutionType) {
        pushToHistory()
        val currentSegments = _uiState.value.segments.toMutableList()
        val segment = currentSegments[index] as? ConflictSegment.Conflict ?: return

        val resolved = when (type) {
            ResolutionType.HEAD -> segment.headContent
            ResolutionType.INCOMING -> segment.incomingContent
            ResolutionType.BOTH -> segment.headContent + "\n" + segment.incomingContent
            ResolutionType.NONE -> null
        }

        currentSegments[index] = segment.copy(
            resolution = type,
            resolvedContent = resolved
        )
        _uiState.value = _uiState.value.copy(segments = currentSegments)
    }

    fun explainConflictWithAi(index: Int) {
        val apiKey = settings.getGroqApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = "Groq API Key missing. Please set it in Settings.")
            return
        }

        val segment = _uiState.value.segments[index] as? ConflictSegment.Conflict ?: return
        val fileName = _uiState.value.fileName
        val extension = fileName.substringAfterLast(".", "txt")
        
        viewModelScope.launch {
            val updatedSegments = _uiState.value.segments.toMutableList()
            updatedSegments[index] = segment.copy(isAiLoading = true)
            _uiState.value = _uiState.value.copy(segments = updatedSegments)

            try {
                val systemPrompt = """
                    You are a Merge Conflict Assistant for a Git-based version control system. Your task is to analyze three versions of code (Original/Base, Current/HEAD, and Incoming) and provide a professional resolution.

                    TASK:
                    1. EXPLANATION: Briefly explain what changed between the Original, Current, and Incoming versions.
                    2. SUGGESTED ACTION: Based on the changes, definitively suggest the best merge action: "Accept Current", "Accept Incoming", or "Merge Both Versions".
                    3. RISKS: Highlight any risky areas (e.g., potential crashes, logic loops, or syntax breakages).
                    4. SUGGESTION: Provide the final merged code version that resolves the conflict.

                    RULES:
                    - Maintain the syntax and styling of the provided code (File: $fileName, Language: $extension).
                    - Keep explanations concise and mobile-friendly.
                    - The SUGGESTION block must contain the final resolved code ONLY.

                    OUTPUT FORMAT:
                    EXPLANATION: [text]
                    SUGGESTED ACTION: [Accept Current / Accept Incoming / Merge Both]
                    RISKS: [text]
                    SUGGESTION: [code]
                """.trimIndent()

                val userPrompt = """
                    FILE: $fileName
                    
                    ORIGINAL VERSION (BASE):
                    ${segment.baseContent ?: "Not available"}
                    
                    ---
                    CURRENT BRANCH (HEAD / User's Code):
                    ${segment.headContent}
                    
                    ---
                    INCOMING BRANCH (Other Branch Changes):
                    ${segment.incomingContent}
                    
                    Please resolve this conflict according to the task rules.
                """.trimIndent()

                val response = groqService.getCompletion(
                    auth = "Bearer $apiKey",
                    request = GroqRequest(
                        model = settings.getGroqModel(),
                        messages = listOf(
                            GroqMessage("system", systemPrompt),
                            GroqMessage("user", userPrompt)
                        ),
                        temperature = 0.1 // Lower temp for precise code generation
                    )
                )

                val content = response.choices.firstOrNull()?.message?.content ?: "AI failed to suggest."
                
                val finalSegments = _uiState.value.segments.toMutableList()
                finalSegments[index] = (finalSegments[index] as ConflictSegment.Conflict).copy(
                    aiSuggestion = content,
                    isAiLoading = false
                )
                _uiState.value = _uiState.value.copy(segments = finalSegments)

            } catch (e: Exception) {
                val finalSegments = _uiState.value.segments.toMutableList()
                finalSegments[index] = (finalSegments[index] as ConflictSegment.Conflict).copy(isAiLoading = false)
                _uiState.value = _uiState.value.copy(segments = finalSegments, error = "AI Error: ${e.message}")
            }
        }
    }

    fun applyAiSuggestion(index: Int, suggestionText: String) {
        pushToHistory()
        // Extract the SUGGESTION part from the full AI response
        val suggestedCode = suggestionText.substringAfter("SUGGESTION:").trim()
        
        val currentSegments = _uiState.value.segments.toMutableList()
        val segment = currentSegments[index] as? ConflictSegment.Conflict ?: return

        currentSegments[index] = segment.copy(
            resolution = ResolutionType.BOTH, // Using BOTH as a placeholder for custom resolution
            resolvedContent = suggestedCode
        )
        _uiState.value = _uiState.value.copy(segments = currentSegments)
    }

    fun save(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val finalContent = _uiState.value.segments.joinToString("\n") { segment ->
                    when (segment) {
                        is ConflictSegment.Normal -> segment.content
                        is ConflictSegment.Conflict -> segment.resolvedContent ?: (
                            "<<<<<<< HEAD\n${segment.headContent}\n=======\n${segment.incomingContent}\n>>>>>>>"
                        )
                    }
                }
                
                // Atomic write using temp file
                val tempFile = File(repoDir, "$relativePath.tmp")
                tempFile.writeText(finalContent)
                val targetFile = File(repoDir, relativePath)
                if (tempFile.renameTo(targetFile)) {
                    onSuccess()
                } else {
                    targetFile.writeText(finalContent) // fallback
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Save failed: ${e.message}", isSaving = false)
            }
        }
    }
}
