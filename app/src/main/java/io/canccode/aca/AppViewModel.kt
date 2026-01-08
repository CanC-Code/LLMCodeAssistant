package io.canccode.aca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

class AppViewModel : ViewModel() {

    // Currently selected file
    private val _selectedFile = MutableLiveData<File?>()
    val selectedFile: LiveData<File?> = _selectedFile

    // Current editor content
    private val _editorContent = MutableLiveData<String>()
    val editorContent: LiveData<String> = _editorContent

    // Send content to LLM
    private val _llmInput = MutableLiveData<String>()
    val llmInput: LiveData<String> = _llmInput

    fun selectFile(file: File) {
        _selectedFile.value = file
        _editorContent.value = file.readText()
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun sendToLLM(input: String) {
        _llmInput.value = input
    }
}