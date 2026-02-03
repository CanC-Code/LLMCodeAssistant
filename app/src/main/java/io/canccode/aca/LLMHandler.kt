package io.canccode.aca

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LLMFragment : Fragment() {

    private val TAG = "LLMFragment"
    // Using viewModels delegate to get our updated LLMViewModel
    private val viewModel: LLMViewModel by viewModels()

    private lateinit var chatOutput: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var clearBtn: Button

    private var thinkingJob: Job? = null

    // 1. Define the File Picker Launcher
    private val modelPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.loadModelFromUri(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatOutput = view.findViewById(R.id.chatOutput)
        chatScroll = view.findViewById(R.id.chatScroll)
        inputBox = view.findViewById(R.id.inputBox)
        sendBtn = view.findViewById(R.id.sendBtn)
        clearBtn = view.findViewById(R.id.clearBtn)

        // 2. Observe ViewModel Output
        viewModel.output.observe(viewLifecycleOwner) { text ->
            chatOutput.text = text
            stopThinkingAnimation()
            scrollToBottom()
        }

        // 3. Observe Errors
        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                stopThinkingAnimation()
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        // 4. Observe Model Load Status
        viewModel.isModelLoaded.observe(viewLifecycleOwner) { isLoaded ->
            sendBtn.isEnabled = isLoaded
            if (isLoaded) {
                chatOutput.append("\n✅ Ready for input.")
            }
        }

        sendBtn.setOnClickListener {
            val prompt = inputBox.text.toString().trim()
            if (prompt.isNotEmpty()) {
                startThinkingAnimation()
                viewModel.sendPrompt(prompt)
                inputBox.text.clear()
            }
        }

        clearBtn.setOnClickListener {
            viewModel.clearOutput()
            LlamaBridge.clearHistoryNative()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.llm_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_load_model -> { // Add this ID to your menu xml
                openModelPicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openModelPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // GGUF files often don't have a specific MIME type
        }
        modelPickerLauncher.launch(intent)
    }

    private fun startThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = viewLifecycleOwner.lifecycleScope.launch {
            val baseText = chatOutput.text.toString()
            var dots = ""
            while (true) {
                dots = if (dots.length >= 3) "" else dots + "."
                chatOutput.text = "$baseText\n\n🤖 Thinking$dots"
                delay(500)
            }
        }
    }

    private fun stopThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
