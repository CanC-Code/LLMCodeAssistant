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
    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = requireArguments().getString(ARG_PATH)!!
        file = File(path)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        editor = view.findViewById(R.id.editor)
        saveButton = view.findViewById(R.id.save_button)

        editor.setText(file.readText())

        saveButton.setOnClickListener {
            file.writeText(editor.text.toString())
        }
    }

    companion object {
        private const val ARG_PATH = "path"

        fun newInstance(path: String): EditorFragment {
            val f = EditorFragment()
            f.arguments = Bundle().apply {
                putString(ARG_PATH, path)
            }
            return f
        }
    }
}