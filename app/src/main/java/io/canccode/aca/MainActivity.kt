package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import io.canccode.aca.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Add code editor fragment
        supportFragmentManager.commit {
            replace(binding.codeEditorContainer.id, CodeEditorFragment())
        }

        // Add output console fragment
        supportFragmentManager.commit {
            replace(binding.outputConsoleContainer.id, OutputConsoleFragment())
        }

        // Add file browser fragment
        supportFragmentManager.commit {
            replace(R.id.file_browser_container, FileBrowserFragment())
        }
    }

    // Open file in editor
    fun openFileInEditor(file: File) {
        val fragment = supportFragmentManager.findFragmentById(binding.codeEditorContainer.id)
        if (fragment is CodeEditorFragment) {
            fragment.loadFile(file)
        }
    }
}