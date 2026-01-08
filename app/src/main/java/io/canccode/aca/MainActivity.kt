package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import io.canccode.aca.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize fragments
        setupFragments()
    }

    private fun setupFragments() {
        // Add file browser fragment
        replaceFragment(R.id.file_browser_container, FileBrowserFragment())

        // Add code editor fragment
        replaceFragment(R.id.code_editor_container, CodeEditorFragment())

        // Add output console fragment
        replaceFragment(R.id.output_console_container, OutputConsoleFragment())
    }

    private fun replaceFragment(containerId: Int, fragment: Fragment) {
        supportFragmentManager.commit {
            replace(containerId, fragment)
        }
    }

    // Example method to update output console
    fun appendOutput(text: String) {
        val fragment = supportFragmentManager.findFragmentById(R.id.output_console_container)
        if (fragment is OutputConsoleFragment) {
            fragment.appendText(text)
        }
    }
}