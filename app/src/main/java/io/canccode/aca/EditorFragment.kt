package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment

class EditorFragment : Fragment() {

    private lateinit var editText: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_editor, container, false)
        editText = view.findViewById(R.id.editText)
        return view
    }

    fun getText(): String {
        return editText.text.toString()
    }

    fun setText(text: String) {
        editText.setText(text)
    }
}