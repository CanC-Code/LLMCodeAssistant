package io.canccode.aca

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import java.io.File

class FileBrowserFragment : Fragment() {

    private var projectRootPath: String? = null
    private var showHiddenFiles = false

    companion object {
        private const val ARG_PROJECT_PATH = "project_path"

        fun newInstance(projectPath: String): FileBrowserFragment =
            FileBrowserFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROJECT_PATH, projectPath)
                }
            }
    }

    private lateinit var containerLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectRootPath = arguments?.getString(ARG_PROJECT_PATH)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        containerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8)
        }

        val toggleHidden = CheckBox(requireContext()).apply {
            text = "Show Hidden Files"
            isChecked = showHiddenFiles
            setOnCheckedChangeListener { _, checked ->
                showHiddenFiles = checked
                refreshFileList()
            }
        }

        containerLayout.addView(toggleHidden)
        refreshFileList()
        return containerLayout
    }

    private fun refreshFileList() {
        val toggle = containerLayout.getChildAt(0)
        containerLayout.removeAllViews()
        containerLayout.addView(toggle)

        val root = projectRootPath?.let(::File) ?: return
        if (!root.exists()) return

        addFileView(root, 0)
    }

    private fun addFileView(file: File, indent: Int) {
        if (!showHiddenFiles && file.name.startsWith(".")) return

        val view = TextView(requireContext()).apply {
            text = if (file.isDirectory) "+ [${file.name}]" else file.name
            setPadding(20 * indent, 8, 8, 8)
            tag = file

            setOnClickListener {
                if (file.isDirectory) {
                    toggleDirectoryAnimated(this, file, indent + 1)
                } else {
                    (activity as? MainActivity)?.openFileInEditor(file)
                }
                highlightSelectedFile(this)
            }
        }

        val isOutside =
            (activity as? MainActivity)?.checkFileWithinProject(file) == false

        view.setBackgroundColor(
            if (isOutside) Color.parseColor("#33FF0000") else Color.TRANSPARENT
        )

        containerLayout.addView(view)
    }

    private fun toggleDirectoryAnimated(
        parentView: TextView,
        folder: File,
        indent: Int
    ) {
        val parentIndex = containerLayout.indexOfChild(parentView)
        val descendants = mutableListOf<View>()

        for (i in parentIndex + 1 until containerLayout.childCount) {
            val tagged = containerLayout.getChildAt(i).tag as? File ?: break
            if (!tagged.canonicalPath.startsWith(folder.canonicalPath)) break
            descendants.add(containerLayout.getChildAt(i))
        }

        if (descendants.isNotEmpty()) {
            descendants.forEach { animateCollapse(it) }
            parentView.text = "+ [${folder.name}]"
        } else {
            var insertIndex = parentIndex + 1

            folder.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?.forEach { child ->
                    if (!showHiddenFiles && child.name.startsWith(".")) return@forEach
                    insertIndex = addAnimatedChildAt(insertIndex, child, indent)
                }

            parentView.text = "- [${folder.name}]"
        }
    }

    private fun addAnimatedChildAt(index: Int, file: File, indent: Int): Int {
        val view = TextView(requireContext()).apply {
            text = if (file.isDirectory) "+ [${file.name}]" else file.name
            setPadding(20 * indent, 8, 8, 8)
            tag = file
            alpha = 0f

            setOnClickListener {
                if (file.isDirectory) toggleDirectoryAnimated(this, file, indent + 1)
                else (activity as? MainActivity)?.openFileInEditor(file)
                highlightSelectedFile(this)
            }
        }

        val isOutside =
            (activity as? MainActivity)?.checkFileWithinProject(file) == false

        view.setBackgroundColor(
            if (isOutside) Color.parseColor("#33FF0000") else Color.TRANSPARENT
        )

        containerLayout.addView(view, index)
        animateExpand(view)
        return index + 1
    }

    private fun animateExpand(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(containerLayout.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )

        val target = view.measuredHeight.coerceAtLeast(1)
        view.layoutParams.height = 0

        ValueAnimator.ofInt(0, target).apply {
            duration = 150
            addUpdateListener {
                val h = it.animatedValue as Int
                view.layoutParams.height = h
                view.alpha = h.toFloat() / target
                view.requestLayout()
            }
            start()
        }
    }

    private fun animateCollapse(view: View) {
        val start = view.measuredHeight.coerceAtLeast(1)

        ValueAnimator.ofInt(start, 0).apply {
            duration = 150
            addUpdateListener {
                val h = it.animatedValue as Int
                view.layoutParams.height = h
                view.alpha = h.toFloat() / start
                view.requestLayout()
            }
            doOnEnd { containerLayout.removeView(view) }
            start()
        }
    }

    private fun highlightSelectedFile(selected: TextView) {
        for (i in 1 until containerLayout.childCount) {
            containerLayout.getChildAt(i)
                .setBackgroundColor(Color.TRANSPARENT)
        }
        selected.setBackgroundColor(Color.parseColor("#8833AAFF"))
    }
}

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) {}
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
        override fun onAnimationCancel(animation: android.animation.Animator) {}
        override fun onAnimationRepeat(animation: android.animation.Animator) {}
    })
}