package io.canccode.aca

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LLMViewModel(application: Application) : AndroidViewModel(application) {

    private val _output = MutableLiveData<String>("")
    val output: LiveData<String> = _output

    private val _isModelLoaded = MutableLiveData<Boolean>(false)
    val isModelLoaded: LiveData<Boolean> = _isModelLoaded

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    /**
     * Initializes the model using a URI provided by the system file picker.
     */
    fun loadModelFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = getApplication<Application>().contentResolver
                
                // 1. Open a ParcelFileDescriptor for the GGUF file
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fd = pfd.detachFd() // This is the integer FD for JNI
                    val fileSize = pfd.statSize // Modern llama.cpp needs the size
                    
                    Log.i("LLMViewModel", "Loading model from FD: $fd, Size: $fileSize")

                    // 2. Call the native bridge
                    val success = LlamaBridge.initNative(fd, fileSize, 2048)
                    
                    if (success) {
                        _isModelLoaded.postValue(true)
                        _output.postValue("Model loaded successfully from: ${uri.path}")
                    } else {
                        _error.postValue("Failed to initialize native model.")
                    }
                }
            } catch (e: Exception) {
                Log.e("LLMViewModel", "Error loading model", e)
                _error.postValue("Error opening file: ${e.message}")
            }
        }
    }

    fun sendPrompt(prompt: String) {
        if (_isModelLoaded.value != true) {
            _error.value = "Please load a model first."
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            LlamaBridge.generateNative(prompt, 512, object : LlamaBridge.GenerateCallback {
                override fun onToken(piece: String) {
                    // Update the output LiveData in real-time
                    _output.postValue(_output.value + piece)
                }

                override fun onComplete(fullResponse: String) {
                    Log.i("LLMViewModel", "Generation complete")
                }

                override fun onError(error: String) {
                    _error.postValue(error)
                }
            })
        }
    }

    fun clearOutput() {
        _output.value = ""
    }
}
