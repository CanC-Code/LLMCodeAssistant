package io.canccode.aca

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment

class EditorFragment : Fragment() {

    private lateinit var editor: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_editor, container, false)

        editor = view.findViewById(R.id.editorText)

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                // no-op
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                // this is where future live-edit hooks go
            }

            override fun afterTextChanged(s: Editable?) {
                // no-op for now
            }
        })

        return view
    }

    fun setText(content: String) {
        editor.setText(content)
    }

    fun getText(): String {
        return editor.text.toString()
    }
}