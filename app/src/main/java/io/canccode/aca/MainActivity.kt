package io.canccode.aca

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    // Public so fragments can access shared project state
    lateinit var projectLoader: ProjectLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectLoader = ProjectLoader(this)

        val btnEditor = findViewById<Button>(R.id.btnEditorMode)
        val btnLLM = findViewById<Button>(R.id.btnLLMMode)
        val btnLoadProject = findViewById<Button>(R.id.btnLoadProject)

        btnEditor.setOnClickListener {
            openFragment(EditorFragment())
        }

        btnLLM.setOnClickListener {
            openFragment(LLMFragment())
        }

        btnLoadProject.setOnClickListener {
            pickProjectFolder()
        }

        // Default screen
        if (savedInstanceState == null) {
            openFragment(EditorFragment())
        }
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .commit()
    }

    // -------------------------
    // Project folder selection
    // -------------------------
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                projectLoader.loadProject(it)
            }
        }

    fun pickProjectFolder() {
        folderPickerLauncher.launch(null)
    }
}