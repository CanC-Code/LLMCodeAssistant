package io.canccode.aca

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.canccode.aca.adapters.FileListAdapter

/**
 * Fragment that always shows the LLM chat interface at the bottom
 * and optionally a file browser above.
 */
class LLMFragment : Fragment() {

    private lateinit var fileRecyclerView: RecyclerView
    private lateinit var fileAdapter: FileListAdapter
    private lateinit var projectLoader: ProjectLoader

    private lateinit var chatOutput: TextView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button

    override fun onAttach(context: Context) {
        super.onAttach(context)
        projectLoader = ProjectLoader(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_llm, container, false)

        // File browser setup
        fileRecyclerView = view.findViewById(R.id.fileRecyclerView)
        fileRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        val fileList = projectLoader.getAllFiles().keys.toList()
        fileAdapter = FileListAdapter(fileList) { fileName ->
            openFile(fileName)
        }
        fileRecyclerView.adapter = fileAdapter

        // Chat interface setup
        chatOutput = view.findViewById(R.id.chatOutput)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)

        sendBtn.setOnClickListener {
            val message = inputBox.text.toString()
            if (message.isNotBlank()) {
                sendMessageToLLM(message)
                inputBox.text.clear()
            }
        }

        return view
    }

    private fun openFile(fileName: String) {
        val fragment = EditorFragment.newInstance(fileName)
        parentFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }

    private fun sendMessageToLLM(message: String) {
        // TODO: Replace with your LLM integration
        chatOutput.append("\n> $message\nLLM: (response here)")
    }
}