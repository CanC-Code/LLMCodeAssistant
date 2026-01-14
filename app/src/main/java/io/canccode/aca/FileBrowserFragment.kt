package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import io.canccode.aca.R

class FileBrowserFragment : Fragment() {

    private lateinit var fragmentContainer: FrameLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)

        // Get the container for fragment replacement
        fragmentContainer = activity?.findViewById(R.id.fragment_container)!!

        // Make the file item clickable — use the correct ID from item_file.xml
        view.findViewById<View>(R.id.item_file_root)?.setOnClickListener {
            // Replace this fragment with EditorFragment on click
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EditorFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}