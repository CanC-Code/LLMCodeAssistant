package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.canccode.aca.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val codeEditorFragment = CodeEditorFragment()
    private val fileBrowserFragment = FileBrowserFragment()
    private val outputConsoleFragment = OutputConsoleFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load fragments
        supportFragmentManager.beginTransaction()
            .replace(binding.codeEditorContainer.id, codeEditorFragment)
            .replace(binding.fileBrowserContainer.id, fileBrowserFragment)
            .replace(binding.outputConsoleContainer.id, outputConsoleFragment)
            .commit()
    }

    // Convenience methods to interact with OutputConsoleFragment
    fun appendToConsole(text: String) {
        outputConsoleFragment.appendText(text)
    }

    fun clearConsole() {
        outputConsoleFragment.clear()
    }
}