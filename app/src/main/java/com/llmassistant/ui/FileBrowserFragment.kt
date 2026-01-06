// File: LLMCodeAssistant/app/src/main/java/com/llmassistant/ui/FileBrowserFragment.kt
// Author: CCVO
// Purpose: Custom in-APK file browser with collapsible folders, hidden file toggle, project boundary indicator, and animated expand/collapse

package com.llmassistant.ui

import android.animation.ValueAnimator
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
            text = if (file.isDirectory) "+ [${file.name}]" else file.name
            setPadding(20 * indentLevel, 8, 8, 8)
            setOnClickListener {
                if (file.isDirectory) {
                    toggleDirectoryAnimated(this, file, indentLevel + 1)
                } else {
                    (activity as? MainActivity)?.openFileInEditor(file)
                }
                highlightSelectedFile(this)
            }
        }

        val isOutside = !(activity as? MainActivity)?.checkFileWithinProject(file)!!
        textView.setBackgroundColor(if (isOutside) Color.parseColor("#33FF0000") else Color.TRANSPARENT)

        containerLayout.addView(textView)
    }

    private fun toggleDirectoryAnimated(parentView: TextView, folder: File, indentLevel: Int) {
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
            // Collapse with animation
            childrenToRemove.forEach { animateCollapse(it) }
            parentView.text = "+ [${folder.name}]"
        } else {
            // Expand: add child views first with height = 0, then animate height
            val childViews = folder.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            childViews?.forEach {
                if (!showHiddenFiles && it.name.startsWith(".")) return@forEach
                it.tag = folder
                val textView = TextView(requireContext()).apply {
                    text = if (it.isDirectory) "+ [${it.name}]" else it.name
                    setPadding(20 * indentLevel, 8, 8, 8)
                    alpha = 0f
                    setOnClickListener { v ->
                        if (it.isDirectory) toggleDirectoryAnimated(this, it, indentLevel + 1)
                        else (activity as? MainActivity)?.openFileInEditor(it)
                        highlightSelectedFile(this)
                    }
                    val isOutside = !(activity as? MainActivity)?.checkFileWithinProject(it)!!
                    setBackgroundColor(if (isOutside) Color.parseColor("#33FF0000") else Color.TRANSPARENT)
                }
                containerLayout.addView(textView)
                animateExpand(textView)
            }
            parentView.text = "- [${folder.name}]"
        }
    }

    private fun animateExpand(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(containerLayout.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val targetHeight = view.measuredHeight
        view.layoutParams.height = 0
        view.alpha = 0f

        val animator = ValueAnimator.ofInt(0, targetHeight)
        animator.addUpdateListener { valueAnimator ->
            view.layoutParams.height = valueAnimator.animatedValue as Int
            view.requestLayout()
            view.alpha = (view.layoutParams.height.toFloat() / targetHeight)
        }
        animator.duration = 150
        animator.start()
    }

    private fun animateCollapse(view: View) {
        val initialHeight = view.measuredHeight
        val animator = ValueAnimator.ofInt(initialHeight, 0)
        animator.addUpdateListener { valueAnimator ->
            view.layoutParams.height = valueAnimator.animatedValue as Int
            view.alpha = (view.layoutParams.height.toFloat() / initialHeight)
            view.requestLayout()
        }
        animator.duration = 150
        animator.start()
        animator.doOnEnd { containerLayout.removeView(view) }
    }

    private fun highlightSelectedFile(selectedView: TextView) {
        for (i in 1 until containerLayout.childCount) { // skip toggle checkbox
            val child = containerLayout.getChildAt(i)
            child.setBackgroundColor(Color.TRANSPARENT)
        }
        selectedView.setBackgroundColor(Color.parseColor("#8833AAFF")) // light blue highlight
    }
}

// Extension for ValueAnimator end callback
private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) {}
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
        override fun onAnimationCancel(animation: android.animation.Animator) {}
        override fun onAnimationRepeat(animation: android.animation.Animator) {}
    })
}