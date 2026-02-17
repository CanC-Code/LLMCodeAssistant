package io.canccode.aca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

/**
 * Single source of truth for all shared UI state.
 * Lives for the lifetime of the Activity (survives fragment transactions).
 */
class AppViewModel : ViewModel() {

    // ── Editor ────────────────────────────────────────────────────────────────

    private val _selectedFile = MutableLiveData<File?>()
    val selectedFile: LiveData<File?> = _selectedFile

    private val _editorContent = MutableLiveData<String>()
    val editorContent: LiveData<String> = _editorContent

    // ── Model state ───────────────────────────────────────────────────────────

    private val _activeModelPath = MutableLiveData<String?>(null)
    val activeModelPath: LiveData<String?> = _activeModelPath

    private val _isModelLoaded = MutableLiveData<Boolean>(false)
    val isModelLoaded: LiveData<Boolean> = _isModelLoaded

    // ── Project context ───────────────────────────────────────────────────────

    /**
     * Flat map of  relative-path -> file-content  for the loaded project.
     * Populated by ProjectContextBuilder when the user opens a project.
     * LLMFragment reads this to inject project context into prompts.
     */
    private val _projectContext = MutableLiveData<Map<String, String>>(emptyMap())
    val projectContext: LiveData<Map<String, String>> = _projectContext

    /** Human-readable project name shown in the LLM chat header. */
    private val _projectName = MutableLiveData<String?>(null)
    val projectName: LiveData<String?> = _projectName

    // ── Chat history ──────────────────────────────────────────────────────────

    /**
     * Retained across fragment transactions (but not process death — that's
     * intentional: history is in-memory only; the KV cache matches it).
     */
    data class ChatMessage(val role: Role, val text: String) {
        enum class Role { USER, ASSISTANT, SYSTEM }
    }

    private val _chatHistory = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val chatHistory: LiveData<MutableList<ChatMessage>> = _chatHistory

    fun addChatMessage(message: ChatMessage) {
        val current = _chatHistory.value ?: mutableListOf()
        current.add(message)
        _chatHistory.value = current
    }

    fun clearChatHistory() {
        _chatHistory.value = mutableListOf()
    }

    // ── LLM input relay ───────────────────────────────────────────────────────

    private val _llmInput = MutableLiveData<String>()
    val llmInput: LiveData<String> = _llmInput

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectFile(file: File) {
        _selectedFile.value = file
        try {
            _editorContent.value = file.readText()
        } catch (e: Exception) {
            _editorContent.value = "Error reading file: ${e.message}"
        }
    }

    fun setModelLoaded(path: String) {
        _activeModelPath.value = path
        _isModelLoaded.value = true
    }

    fun clearModel() {
        _activeModelPath.value = null
        _isModelLoaded.value = false
    }

    fun setProjectContext(name: String, files: Map<String, String>) {
        _projectName.value = name
        _projectContext.value = files
    }

    fun clearProjectContext() {
        _projectName.value = null
        _projectContext.value = emptyMap()
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun sendToLLM(input: String) {
        _llmInput.value = input
    }
}
