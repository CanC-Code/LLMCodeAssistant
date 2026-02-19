package io.canccode.aca

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Single source of truth for all shared UI state.
 *
 * New in this revision:
 *  - [pendingDiff]: LLM-suggested code change waiting for Accept/Reject.
 *    Published by LLMFragment, consumed by EnhancedEditorFragment.
 *  - [scrollToLine]: signals editor to scroll to a specific line
 *    (used after undo/redo to bring changed region into view).
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREF_CHAT    = "chat_history_prefs"
        private const val KEY_HISTORY  = "history_json"
        private const val KEY_PROJ_URI = "project_uri"
    }

    private val prefs = app.getSharedPreferences(PREF_CHAT, Context.MODE_PRIVATE)
    private val gson  = Gson()

    // ── Editor ────────────────────────────────────────────────────────────────

    private val _selectedFile    = MutableLiveData<File?>()
    val selectedFile: LiveData<File?> = _selectedFile

    private val _editorContent   = MutableLiveData<String>()
    val editorContent: LiveData<String> = _editorContent

    private val _activeFilePath    = MutableLiveData<String?>(null)
    val activeFilePath: LiveData<String?> = _activeFilePath

    private val _activeFileContent = MutableLiveData<String?>(null)
    val activeFileContent: LiveData<String?> = _activeFileContent

    // ── Diff / suggestion pipeline ────────────────────────────────────────────

    /**
     * A code suggestion produced by the LLM.
     *
     * @param originalContent  Full original file content (for 3-way diff display)
     * @param suggestedContent Full suggested replacement content
     * @param description      Human-readable summary of the change (from LLM)
     * @param targetFilePath   Which file this diff applies to
     */
    data class DiffSuggestion(
        val originalContent: String,
        val suggestedContent: String,
        val description: String,
        val targetFilePath: String
    )

    private val _pendingDiff = MutableLiveData<DiffSuggestion?>(null)
    val pendingDiff: LiveData<DiffSuggestion?> = _pendingDiff

    fun postDiffSuggestion(diff: DiffSuggestion) { _pendingDiff.value = diff }
    fun clearDiffSuggestion()                     { _pendingDiff.value = null }

    // ── Scroll-to-line signal (undo/redo) ─────────────────────────────────────

    private val _scrollToLine = MutableLiveData<Int?>(null)
    val scrollToLine: LiveData<Int?> = _scrollToLine

    fun requestScrollToLine(line: Int) { _scrollToLine.value = line }
    fun consumeScrollToLine()          { _scrollToLine.value = null }

    // ── Model ─────────────────────────────────────────────────────────────────

    private val _activeModelPath = MutableLiveData<String?>(null)
    val activeModelPath: LiveData<String?> = _activeModelPath

    private val _isModelLoaded = MutableLiveData<Boolean>(false)
    val isModelLoaded: LiveData<Boolean> = _isModelLoaded

    // ── Project ───────────────────────────────────────────────────────────────

    private val _projectUri = MutableLiveData<Uri?>(
        prefs.getString(KEY_PROJ_URI, null)?.let { Uri.parse(it) }
    )
    val projectUri: LiveData<Uri?> = _projectUri

    private val _projectName = MutableLiveData<String?>(null)
    val projectName: LiveData<String?> = _projectName

    private val _projectContext = MutableLiveData<Map<String, String>>(emptyMap())
    val projectContext: LiveData<Map<String, String>> = _projectContext

    // ── Chat history (PERSISTED) ──────────────────────────────────────────────

    data class ChatMessage(val role: Role, val text: String) {
        enum class Role { USER, ASSISTANT }
    }

    private val _chatHistory = MutableLiveData<MutableList<ChatMessage>>(loadHistory())
    val chatHistory: LiveData<MutableList<ChatMessage>> = _chatHistory

    fun addChatMessage(message: ChatMessage) {
        val list = _chatHistory.value ?: mutableListOf()
        list.add(message)
        _chatHistory.value = list
        saveHistory(list)
    }

    fun clearChatHistory() {
        _chatHistory.value = mutableListOf()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(list: List<ChatMessage>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    private fun loadHistory(): MutableList<ChatMessage> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    // ── LLM input relay ───────────────────────────────────────────────────────

    private val _llmInput = MutableLiveData<String?>()
    val llmInput: LiveData<String?> = _llmInput

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectFile(file: File) {
        _selectedFile.value = file
        try {
            val content = file.readText()
            _editorContent.value = content
            _activeFilePath.value = file.absolutePath
            _activeFileContent.value = content
        } catch (e: Exception) {
            _editorContent.value = "Error reading file: ${e.message}"
        }
    }

    fun setActiveEditorFile(relativePath: String, content: String) {
        _activeFilePath.value = relativePath
        _activeFileContent.value = content
    }

    fun clearActiveEditorFile() {
        _activeFilePath.value = null
        _activeFileContent.value = null
    }

    fun setModelLoaded(path: String) {
        _activeModelPath.value = path
        _isModelLoaded.value = true
    }

    fun clearModel() {
        _activeModelPath.value = null
        _isModelLoaded.value = false
    }

    fun setProjectContext(name: String, uri: Uri, files: Map<String, String>) {
        _projectName.value = name
        _projectUri.value  = uri
        _projectContext.value = files
        prefs.edit().putString(KEY_PROJ_URI, uri.toString()).apply()
    }

    fun clearProjectContext() {
        _projectName.value    = null
        _projectUri.value     = null
        _projectContext.value = emptyMap()
        prefs.edit().remove(KEY_PROJ_URI).apply()
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun sendToLLM(input: String) {
        _llmInput.value = input
    }

    fun consumeLlmInput() {
        _llmInput.value = null
    }
}
