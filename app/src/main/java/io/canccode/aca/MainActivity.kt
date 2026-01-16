package io.canccode.aca

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var llmHandler: LLMHandler

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                initializeLLM()
            } else {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        llmHandler = LLMHandler(this)

        checkPermissionsAndInitialize()
    }

    private fun checkPermissionsAndInitialize() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeLLM()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun initializeLLM() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                llmHandler.ensureModelReady()
                Log.i(TAG, "LLM initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize LLM", e)
            }
        }
    }
}