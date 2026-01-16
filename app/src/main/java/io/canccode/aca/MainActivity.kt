package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var llmHandler: LLMHandler
    private lateinit var llmProgressBar: ProgressBar

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        llmProgressBar = findViewById(R.id.llm_progress_bar)
        llmHandler = LLMHandler(this)

        initializeLLM()
    }

    private fun initializeLLM() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runOnUiThread { llmProgressBar.progress = 0; llmProgressBar.isIndeterminate = true }

                val initialized = llmHandler.initialize { progress ->
                    runOnUiThread { llmProgressBar.isIndeterminate = false; llmProgressBar.progress = progress }
                }

                runOnUiThread {
                    llmProgressBar.isIndeterminate = false
                    llmProgressBar.progress = 100
                }

                if (initialized) {
                    Log.i(TAG, "LLM initialized successfully!")
                    val testOutput = llmHandler.infer("Hello LLM!", 64)
                    Log.i(TAG, "Test output: $testOutput")
                } else {
                    Log.e(TAG, "LLM failed to initialize")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing LLM", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Shutting down LLM...")
        llmHandler.close()
    }
}