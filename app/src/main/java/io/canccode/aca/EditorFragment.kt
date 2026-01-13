package io.canccode.aca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class EditorFragment : Fragment() {

    private var fileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileName = arguments?.getString("fileName")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_editor, container, false)
    }

    companion object {
        fun newInstance(fileName: String): EditorFragment {
            val fragment = EditorFragment()
            val args = Bundle()
            args.putString("fileName", fileName)
            fragment.arguments = args
            return fragment
        }
    }
}