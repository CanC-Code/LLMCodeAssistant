package io.canccode.aca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Single source of truth for the currently loaded project.
 * Shared between Editor, FileBrowser, and LLM.
 */
class ProjectViewModel : ViewModel() {

    // Map of URI -> file contents
    private val _files = MutableLiveData<Map<String, String>>(emptyMap())
    val files: LiveData<Map<String, String>> = _files

    // Currently selected file URI
    private val _activeFileUri = MutableLiveData<String?>(null)
    val activeFileUri: LiveData<String?> = _activeFileUri

    // -----------------------------
    // Project lifecycle
    // -----------------------------

    fun setProjectFiles(newFiles: Map<String, String>) {
        _files.value = newFiles
        _activeFileUri.value = newFiles.keys.firstOrNull()
    }

    // -----------------------------
    // File selection
    // -----------------------------

    fun selectFile(uri: String) {
        if (_files.value?.containsKey(uri) == true) {
            _activeFileUri.value = uri
        }
    }

    // -----------------------------
    // File access
    // -----------------------------

    fun getActiveFileContent(): String {
        val uri = _activeFileUri.value ?: return ""
        return _files.value?.get(uri) ?: ""
    }

    fun updateActiveFileContent(newContent: String) {
        val uri = _activeFileUri.value ?: return
        val updated = _files.value?.toMutableMap() ?: return
        updated[uri] = newContent
        _files.value = updated
    }
}