package io.canccode.aca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import io.canccode.aca.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load FileBrowserFragment by default
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(binding.topContainer.id, FileBrowserFragment())
            }
        }

        // LLM Fragment is always mounted
        supportFragmentManager.commit {
            replace(binding.llmContainer.id, LLMFragment())
        }
    }
}