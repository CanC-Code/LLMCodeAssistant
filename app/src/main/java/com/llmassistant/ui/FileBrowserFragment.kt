// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/FileBrowserFragment.kt
// Author: CCVO
// Purpose: Custom in-APK file browser with collapsible folders, hidden file toggle, and project boundary indicator

package com.llmassistant.ui

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.llmassistant.R
import java.io.File

class FileBrowserFragment : Fragment() {

    private var projectRootPath: String? = null
    private var showHiddenFiles: Boolean = false

    companion object {
        private const val ARG_PROJECT_PATH = "project_path"

        fun newInstance(projectPath: String): FileBrowserFragment {
            val fragment = FileBrowserFragment()
            val args = Bundle()
            args.putString(ARG_PROJECT_PATH, projectPath)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var containerLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectRootPath = arguments?.getString(ARG_PROJECT_PATH)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        containerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8)
        }

        // Hidden files toggle
        val toggleHidden = CheckBox(requireContext()).apply {
            text = "Show Hidden Files"
            isChecked = showHiddenFiles
            setOnCheckedChangeListener { _, isChecked ->
                showHiddenFiles = isChecked
                refreshFileList()
            }
        }
        containerLayout.addView(toggleHidden)

        refreshFileList()
        return containerLayout
    }

    private fun refreshFileList() {
        // Keep the hidden toggle at top
        val toggleHidden = containerLayout.getChildAt(0)
        containerLayout.removeAllViews()
        containerLayout.addView(toggleHidden)

        val rootFolder = projectRootPath?.let { File(it) } ?: return
        if (!rootFolder.exists()) return

        addFileView(rootFolder, 0)
    }

    private fun addFileView(file: File, indentLevel: Int) {
        if (!showHiddenFiles && file.name.startsWith(".")) return

        val textView = TextView(requireContext()).apply {
            text = if (file.isDirectory) "[${file.name}]" else file.name
            setPadding(20 * indentLevel, 8, 8, 8)
            setOnClickListener {
                if (file.isDirectory) {
                    toggleDirectory(this, file, indentLevel + 1)
                } else {
                    (activity as? MainActivity)?.openFileInEditor(file)
                }
                highlightSelectedFile(this)
            }
        }

        // Mark if outside project root
        val isOutside = !(activity as? MainActivity)?.checkFileWithinProject(file)!!
        textView.setBackgroundColor(if (isOutside) Color.parseColor("#33FF0000") else Color.TRANSPARENT)

        containerLayout.addView(textView)
    }

    private fun toggleDirectory(parentView: TextView, folder: File, indentLevel: Int) {
        // Remove or add children
        val startIndex = containerLayout.indexOfChild(parentView) + 1
        val endIndex = containerLayout.childCount
        val childrenToRemove = mutableListOf<View>()

        for (i in startIndex until endIndex) {
            val v = containerLayout.getChildAt(i)
            if ((v.tag as? File)?.parentFile == folder) {
                childrenToRemove.add(v)
            }
        }

        if (childrenToRemove.isNotEmpty()) {
            // Collapse
            childrenToRemove.forEach { containerLayout.removeView(it) }
        } else {
            // Expand
            folder.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach {
                it.tag = folder
                addFileView(it, indentLevel)
            }
        }
    }

    private fun highlightSelectedFile(selectedView: TextView) {
        for (i in 1 until containerLayout.childCount) { // skip toggle checkbox
            val child = containerLayout.getChildAt(i)
            child.setBackgroundColor(Color.TRANSPARENT)
        }
        selectedView.setBackgroundColor(Color.parseColor("#8833AAFF")) // light blue highlight
    }
}