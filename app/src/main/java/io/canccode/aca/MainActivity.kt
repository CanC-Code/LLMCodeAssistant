package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.canccode.aca.fragments.FileBrowserFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load initial fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contentContainer, FileBrowserFragment())
                .commit()
        }
    }
}