package io.canccode.aca.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import io.canccode.aca.R

class EditorFragment : Fragment() {

    companion object {
        private const val ARG_FILENAME = "filename"

        fun newInstance(fileName: String) = EditorFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FILENAME, fileName)
            }
        }
    }

    private lateinit var editorText: EditText
    private var fileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileName = arguments?.getString(ARG_FILENAME)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_editor, container, false)
        editorText = view.findViewById(R.id.editorText)
        editorText.setText("// Editing: ${fileName ?: "Unknown"}")
        return view
    }
}