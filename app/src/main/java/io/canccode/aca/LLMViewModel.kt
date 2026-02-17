package io.canccode.aca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Manages the UI state for the LLM interaction.
 * Handles token accumulation and generation status.
 */
class LLMViewModel : ViewModel() {

    // The accumulated text response from the model
    private val _output = MutableLiveData<String>("")
    val output: LiveData<String> = _output

    // Status tracking to update UI buttons/loaders
    private val _isGenerating = MutableLiveData<Boolean>(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    // Error message handling
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * Appends a new token/piece to the current output.
     * Uses postValue to ensure thread safety when called from JNI threads.
     */
    fun appendToken(piece: String) {
        val currentText = _output.value ?: ""
        _output.postValue(currentText + piece)
    }

    /**
     * Sets the final completed text.
     */
    fun setFullResponse(text: String) {
        _output.postValue(text)
        _isGenerating.postValue(false)
    }

    /**
     * Updates the generation status.
     */
    fun setGenerating(active: Boolean) {
        _isGenerating.postValue(active)
        if (active) _errorMessage.postValue(null) // Clear errors on new start
    }

    /**
     * Posts an error to be displayed via Toast or Snackbar.
     */
    fun setError(message: String) {
        _errorMessage.postValue(message)
        _isGenerating.postValue(false)
    }

    /**
     * Resets the chat display.
     */
    fun clearOutput() {
        _output.value = ""
        _errorMessage.value = null
    }
}
