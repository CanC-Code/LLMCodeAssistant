package io.canccode.aca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

class AppViewModel : ViewModel() {

    // --- Editor State ---
    
    private val _selectedFile = MutableLiveData<File?>()
    val selectedFile: LiveData<File?> = _selectedFile

    private val _editorContent = MutableLiveData<String>()
    val editorContent: LiveData<String> = _editorContent


    // --- LLM / Model State ---

    // Tracks the absolute path of the currently initialized .gguf model
    private val _activeModelPath = MutableLiveData<String?>(null)
    val activeModelPath: LiveData<String?> = _activeModelPath

    // Tracks if the JNI layer is currently busy loading or generating
    private val _isModelLoaded = MutableLiveData<Boolean>(false)
    val isModelLoaded: LiveData<Boolean> = _isModelLoaded

    // The current conversation or input text for the LLM
    private val _llmInput = MutableLiveData<String>()
    val llmInput: LiveData<String> = _llmInput


    // --- Actions ---

    fun selectFile(file: File) {
        _selectedFile.value = file
        try {
            _editorContent.value = file.readText()
        } catch (e: Exception) {
            _editorContent.value = "Error reading file: ${e.message}"
        }
    }

    /**
     * Call this from your FileBrowser or Settings when LlamaBridge.initNative returns true.
     */
    fun setModelLoaded(path: String) {
        _activeModelPath.value = path
        _isModelLoaded.value = true
    }

    /**
     * Call this if the model is unloaded or if an error occurs.
     */
    fun clearModel() {
        _activeModelPath.value = null
        _isModelLoaded.value = false
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun sendToLLM(input: String) {
        _llmInput.value = input
    }
}
