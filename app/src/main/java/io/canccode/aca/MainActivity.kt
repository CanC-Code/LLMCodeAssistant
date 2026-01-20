package io.canccode.aca

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var llmHandler: LLMHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate UI FIRST so fragments & touch handling work
        setContentView(R.layout.activity_main)

        // Initialize LLM handler
        llmHandler = LLMHandler(this)

        // Initialize LLM asynchronously (NO permissions required)
        initializeLLM()
    }

    private fun initializeLLM() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Initializing LLM…")
                llmHandler.ensureModelReady()
                Log.i(TAG, "LLM initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize LLM", e)
            }
        }
    }
}