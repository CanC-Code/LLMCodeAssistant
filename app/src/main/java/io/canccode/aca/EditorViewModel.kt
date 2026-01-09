package io.canccode.aca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class EditorViewModel : ViewModel() {
    private val _text = MutableLiveData("")
    val text: LiveData<String> = _text

    fun setText(newText: String) {
        _text.value = newText
    }
}