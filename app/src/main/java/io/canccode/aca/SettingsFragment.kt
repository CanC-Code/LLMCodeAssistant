package io.canccode.aca

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsFragment : Fragment() {

    companion object {
        private const val TAG = "SettingsFragment"
        const val KEY_MODEL_PATH = "selected_model_path"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var btnPickLocal: Button
    private lateinit var btnClear: Button

    private val pickModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handlePickedModelUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("model_prefs", android.content.Context.MODE_PRIVATE)

        tvStatus = view.findViewById(R.id.tv_model_status)
        progressBar = view.findViewById(R.id.progress_model)
        tvProgress = view.findViewById(R.id.tv_progress)
        btnPickLocal = view.findViewById(R.id.btn_pick_local_model)
        btnClear = view.findViewById(R.id.btn_clear_model)

        updateStatusText()

        btnPickLocal.setOnClickListener {
            pickModelLauncher.launch(arrayOf("*/*"))
        }

        btnClear.setOnClickListener {
            clearModelSelection()
        }
    }

    private fun updateStatusText() {
        val path = prefs.getString(KEY_MODEL_PATH, null)
        if (path.isNullOrEmpty()) {
            tvStatus.text = "No model selected"
        } else {
            val file = File(path)
            if (file.exists()) {
                val sizeMB = file.length() / (1024 * 1024)
                tvStatus.text = "Model: ${file.name}\nSize: ${sizeMB}MB\nPath: ${file.parent}"
            } else {
                tvStatus.text = "Model path set but file not found:\n${file.name}"
            }
        }
    }

    private fun handlePickedModelUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                
                // Get filename from URI
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val filename = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) it.getString(nameIndex) else "model.gguf"
                    } else "model.gguf"
                } ?: "model.gguf"
                
                if (!filename.lowercase().endsWith(".gguf")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Please select a .gguf model file", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = false
                    progressBar.max = 100
                    progressBar.progress = 0
                    tvProgress.visibility = View.VISIBLE
                    tvProgress.text = "Copying: 0%"
                    btnPickLocal.isEnabled = false
                }

                // Copy file to internal storage with real progress
                val destFile = File(context.filesDir, filename)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // Get actual file size
                    val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val fileSize = if (sizeIndex != null && sizeIndex != -1) {
                        cursor.getLong(sizeIndex)
                    } else {
                        -1L
                    }

                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgress = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            
                            if (fileSize > 0) {
                                val progress = (totalRead * 100 / fileSize).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    withContext(Dispatchers.Main) {
                                        progressBar.progress = progress
                                        tvProgress.text = "Copying: $progress% (${totalRead / (1024 * 1024)}MB / ${fileSize / (1024 * 1024)}MB)"
                                    }
                                }
                            } else {
                                // If size unknown, show bytes copied
                                if (totalRead % (1024 * 1024) == 0L) {
                                    withContext(Dispatchers.Main) {
                                        tvProgress.text = "Copying: ${totalRead / (1024 * 1024)}MB..."
                                    }
                                }
                            }
                        }
                    }
                } ?: throw IllegalStateException("Cannot open input stream from URI")

                Log.i(TAG, "Model copied to: ${destFile.absolutePath}")
                Log.i(TAG, "Model size: ${destFile.length() / (1024 * 1024)}MB")

                withContext(Dispatchers.Main) {
                    tvProgress.text = "Initializing model..."
                    progressBar.isIndeterminate = true
                }

                // Initialize the model
                val initSuccess = try {
                    LlamaBridge.initNative(destFile.absolutePath, 2048)
                } catch (e: Exception) {
                    Log.e(TAG, "Model initialization failed", e)
                    false
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    btnPickLocal.isEnabled = true

                    if (initSuccess) {
                        saveModelPath(destFile.absolutePath)
                        Toast.makeText(context, "Model loaded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Delete the file if init failed
                        destFile.delete()
                        Toast.makeText(context, "Model initialization failed", Toast.LENGTH_LONG).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    btnPickLocal.isEnabled = true
                    Toast.makeText(
                        requireContext(), 
                        "Error: ${e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveModelPath(path: String) {
        prefs.edit().putString(KEY_MODEL_PATH, path).apply()
        updateStatusText()
        
        // Notify MainActivity
        (activity as? MainActivity)?.onModelSelectionChanged()
    }

    private fun clearModelSelection() {
        val path = prefs.getString(KEY_MODEL_PATH, null)
        if (!path.isNullOrEmpty()) {
            // Delete the copied model file
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    Log.i(TAG, "Deleted model file: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting model file", e)
            }
        }
        
        // Shutdown model if loaded
        try {
            LlamaBridge.shutdownNative()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down model", e)
        }
        
        prefs.edit().remove(KEY_MODEL_PATH).apply()
        updateStatusText()
        
        (activity as? MainActivity)?.onModelSelectionChanged()
    }
}