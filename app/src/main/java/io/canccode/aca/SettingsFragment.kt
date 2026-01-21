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

class SettingsFragment : Fragment() {

    companion object {
        private const val TAG = "SettingsFragment"
        const val KEY_MODEL_PATH = "model_path"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
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
                tvStatus.text = "Model: ${file.name}\nSize: ${sizeMB}MB\nPath: $path"
            } else {
                tvStatus.text = "Model path set but file not found:\n$path"
            }
        }
    }

    private fun handlePickedModelUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                
                // Get the real file path from URI
                val realPath = getRealPathFromURI(uri)
                
                if (realPath == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot access file path. Please select a file from device storage.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                val file = File(realPath)
                
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File not found: ${file.name}", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                if (!file.name.lowercase().endsWith(".gguf")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Please select a .gguf model file", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                Log.i(TAG, "Selected model: ${file.absolutePath}")
                Log.i(TAG, "Model size: ${file.length() / (1024 * 1024)}MB")

                withContext(Dispatchers.Main) {
                    // Use the file directly - no copying
                    saveModelPath(file.absolutePath)
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true
                    Toast.makeText(context, "Model selected, initializing...", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle picked model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(), 
                        "Error selecting model: ${e.localizedMessage ?: "Unknown error"}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            // Try to get the path directly from the URI
            if (uri.scheme == "file") {
                uri.path
            } else if (uri.scheme == "content") {
                // Query the content provider for the real path
                val cursor = requireContext().contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.Images.Media.DATA),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                        it.getString(columnIndex)
                    } else {
                        // Fallback: try to extract path from URI
                        uri.path
                    }
                } ?: uri.path
            } else {
                uri.path
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting real path from URI", e)
            null
        }
    }

    private fun saveModelPath(path: String) {
        prefs.edit().putString(KEY_MODEL_PATH, path).apply()
        updateStatusText()
        
        // Notify MainActivity to reinitialize LLM with new model
        (activity as? MainActivity)?.onModelSelectionChanged()
    }

    private fun clearModelSelection() {
        prefs.edit().remove(KEY_MODEL_PATH).apply()
        updateStatusText()
        Toast.makeText(requireContext(), "Model selection cleared", Toast.LENGTH_SHORT).show()
        
        (activity as? MainActivity)?.onModelSelectionChanged()
    }

    fun onModelInitComplete(success: Boolean) {
        progressBar.visibility = View.GONE
        updateStatusText()
        
        if (success) {
            Toast.makeText(requireContext(), "Model ready to use!", Toast.LENGTH_SHORT).show()
        }
    }
}