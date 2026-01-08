// File: LLMCodeAssistant/app/src/main/java/io/canccode/aca/OutputConsoleFragment.kt
package io.canccode.aca

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class OutputConsoleFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var consoleLayout: LinearLayout

    companion object {
        fun newInstance(): OutputConsoleFragment {
            return OutputConsoleFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        scrollView = ScrollView(requireContext())
        consoleLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(consoleLayout)
        return scrollView
    }

    fun appendOutput(text: String, type: MessageType = MessageType.LLM, chunkIndex: Int? = null) {
        val textView = TextView(requireContext()).apply {
            val prefix = chunkIndex?.let { "[Chunk $it] " } ?: ""
            val spannable = SpannableString(prefix + text)
            val color = when (type) {
                MessageType.USER -> Color.parseColor("#0077CC")
                MessageType.LLM -> Color.parseColor("#000000")
                MessageType.SYSTEM -> Color.parseColor("#888888")
            }
            spannable.setSpan(ForegroundColorSpan(color), 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setText(spannable)
            setPadding(8, 4, 8, 4)
        }
        consoleLayout.addView(textView)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    enum class MessageType { USER, LLM, SYSTEM }
}