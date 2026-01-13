package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.canccode.aca.fragments.FileBrowserFragment
import io.canccode.aca.adapters.FileListAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var fileRecyclerView: RecyclerView
    private lateinit var fileAdapter: FileListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize RecyclerView
        fileRecyclerView = findViewById(R.id.fileRecyclerView)
        fileRecyclerView.layoutManager = LinearLayoutManager(this)

        fileAdapter = FileListAdapter(getCurrentFileList()) { fileName ->
            openFile(fileName)
        }

        fileRecyclerView.adapter = fileAdapter

        // Load initial fragment
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, FileBrowserFragment())
            }
        }
    }

    private fun openFile(fileName: String) {
        val fragment = EditorFragment.newInstance(fileName)
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }

    // Stub function, replace with your project loader logic
    fun getCurrentFileList(): List<String> {
        return listOf("Example1.txt", "Example2.txt", "Example3.txt")
    }
}