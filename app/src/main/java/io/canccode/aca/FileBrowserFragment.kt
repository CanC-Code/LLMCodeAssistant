package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.canccode.aca.EditorFragment
import io.canccode.aca.R
import android.widget.FrameLayout

class FileBrowserFragment : Fragment() {

    private lateinit var fragmentContainer: FrameLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_browser, container, false)

        fragmentContainer = activity?.findViewById(R.id.fragment_container)!!

        view.findViewById<View>(R.id.someFileItem)?.setOnClickListener {
            // Example: replace fragment on file click
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EditorFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}