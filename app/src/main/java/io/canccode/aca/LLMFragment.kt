package io.canccode.aca

import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
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
 * Fragment displaying:
 *  - Persistent LLM chat interface
 *  - File browser
 *  - Editor panel (opens EditorFragment)
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
    ): View? {
        val view = inflater.inflate(R.layout.fragment_llm, container, false)

        // --------------------
        // Chat interface setup
        // --------------------
        chatOutput = view.findViewById(R.id.chatOutput)
        chatOutput.movementMethod = ScrollingMovementMethod()

        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)

        sendBtn.setOnClickListener {
            val userInput = inputBox.text.toString()
            if (userInput.isNotBlank()) {
                appendChatMessage("You: $userInput")
                inputBox.text.clear()

                // Call LLM handler (stubbed here, implement your LLM logic)
                val response = "LLM Response to: $userInput"
                appendChatMessage("LLM: $response")
            }
        }

        // --------------------
        // File browser setup
        // --------------------
        fileRecyclerView = view.findViewById(R.id.fileRecyclerView)
        fileRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Load files from project (replace with actual SAF URI if needed)
        val fileList = projectLoader.getAllFiles().keys.toList()
        fileAdapter = FileListAdapter(fileList) { fileName ->
            openFile(fileName)
        }
        fileRecyclerView.adapter = fileAdapter

        return view
    }

    /**
     * Opens a file in the editor panel
     */
    private fun openFile(fileName: String) {
        val fragment = EditorFragment.newInstance(fileName)
        parentFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }

    /**
     * Append a message to the chat output and scroll to bottom
     */
    private fun appendChatMessage(message: String) {
        chatOutput.append("$message\n")
        val scrollAmount = chatOutput.layout?.getLineTop(chatOutput.lineCount) ?: 0
        if (scrollAmount > chatOutput.height) {
            chatOutput.scrollTo(0, scrollAmount - chatOutput.height)
        }
    }
}