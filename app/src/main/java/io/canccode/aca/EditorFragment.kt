package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import java.io.File

class EditorFragment : Fragment() {

    private lateinit var editor: EditText
    private lateinit var saveButton: Button

    private var currentFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_editor, container, false)

        editor = view.findViewById(R.id.editor)
        saveButton = view.findViewById(R.id.save_button)

        saveButton.setOnClickListener {
            saveFile()
        }

        return view
    }

    fun loadFile(file: File) {
        currentFile = file
        editor.setText(file.readText())
    }

    private fun saveFile() {
        currentFile?.writeText(editor.text.toString())
    }
}