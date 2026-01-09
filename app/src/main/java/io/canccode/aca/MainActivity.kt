package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import io.canccode.aca.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Example file list, replace with your actual directory
    private val files: List<File> by lazy {
        val dir = File(filesDir, "example") // or Environment.getExternalStorageDirectory()
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.toList() ?: listOf()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load fragments
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(binding.fragmentContainer.id, FileBrowserFragment(), "FileBrowser")
            }
        }

        // Handle file selection from FileBrowserFragment
        binding.openEditorButton.setOnClickListener {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(binding.fragmentContainer.id, EditorFragment(), "Editor")
                addToBackStack(null)
            }
        }

        binding.openLLMButton.setOnClickListener {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(binding.fragmentContainer.id, LLMFragment(), "LLM")
                addToBackStack(null)
            }
        }
    }
}