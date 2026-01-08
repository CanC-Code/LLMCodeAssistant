package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.top_container, FileBrowserFragment())
                .commit()
        }
    }

    fun openFileInEditor(file: File) {
        val fragment = EditorFragment.newInstance(file.absolutePath)

        supportFragmentManager.beginTransaction()
            .replace(R.id.top_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}