package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.llmassistant.llm.LLMHandler
import io.canccode.aca.EditorFragment
import io.canccode.aca.R

class LLMFragment : Fragment() {

    private lateinit var llmHandler: LLMHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        llmHandler = LLMHandler(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_llm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Example: swap in EditorFragment when LLM is ready
        val editorFragment = EditorFragment()
        parentFragmentManager.commit {
            replace(R.id.fragment_container, editorFragment)
            setReorderingAllowed(true)
            addToBackStack(null)
        }
    }
}