package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var projectLoader: ProjectLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)

        // Mode buttons
        val btnEditorMode: Button = findViewById(R.id.btnEditorMode)
        val btnLLMMode: Button = findViewById(R.id.btnLLMMode)
        btnEditorMode.setOnClickListener { switchMode(EditorFragment()) }
        btnLLMMode.setOnClickListener { switchMode(LLMFragment()) }

        // Load Project button
        val btnLoadProject: Button = findViewById(R.id.btnLoadProject)
        btnLoadProject.setOnClickListener {
            pickProjectFolder()
        }
    }

    // Folder picker using SAF
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist access
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            loadProject(it)
        }
    }

    private fun pickProjectFolder() {
        folderPickerLauncher.launch(null)
    }

    private fun loadProject(folderUri: Uri) {
        projectLoader.loadProject(folderUri)

        // Log loaded files
        projectLoader.getAllFiles().forEach { (path, content) ->
            Log.i(TAG, "Loaded: $path (${content.length} chars)")
        }

        // TODO: Update Editor UI or LLM context here
    }

    private fun switchMode(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .commit()
    }
}