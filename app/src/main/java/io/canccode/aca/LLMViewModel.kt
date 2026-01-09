package io.canccode.aca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LLMViewModel : ViewModel() {
    private val _output = MutableLiveData<String>("")
    val output: LiveData<String> = _output

    fun setOutput(text: String) {
        _output.value = text
    }
}