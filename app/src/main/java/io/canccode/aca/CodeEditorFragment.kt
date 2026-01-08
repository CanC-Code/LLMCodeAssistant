package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.File

class CodeEditorFragment : Fragment() {

    private lateinit var filePath: String
    private lateinit var editor: EditText

    companion object {
        private const val ARG_FILE_PATH = "file_path"

        fun newInstance(filePath: String): CodeEditorFragment {
            val fragment = CodeEditorFragment()
            val args = Bundle()
            args.putString(ARG_FILE_PATH, filePath)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filePath = it.getString(ARG_FILE_PATH, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_code_editor, container, false)
        editor = view.findViewById(R.id.code_editor)
        val saveButton: Button = view.findViewById(R.id.save_button)

        // Load file content
        val file = File(filePath)
        if (file.exists() && file.isFile) {
            editor.setText(file.readText())
        } else {
            Toast.makeText(requireContext(), "File not found: $filePath", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            try {
                file.writeText(editor.text.toString())
                Toast.makeText(requireContext(), "Saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        return view
    }
}