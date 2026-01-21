package io.canccode.aca

import android.content.Intent
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
        const val KEY_MODEL_PATH = "model_path"
        
        private const val DEFAULT_MODEL_URL = 
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
        private const val DEFAULT_MODEL_NAME = "tinyllama-1.1b-chat.Q4_K_M.gguf"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDownload: Button
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
        btnDownload = view.findViewById(R.id.btn_download_model)
        btnPickLocal = view.findViewById(R.id.btn_pick_local_model)
        btnClear = view.findViewById(R.id.btn_clear_model)

        updateStatusText()

        btnDownload.setOnClickListener {
            downloadDefaultModel()
        }

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

    private fun downloadDefaultModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
                    btnDownload.isEnabled = false
                }

                val destFile = File(requireContext().filesDir, DEFAULT_MODEL_NAME)

                if (destFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Model already exists", Toast.LENGTH_SHORT).show()
                        saveModelPath(destFile.absolutePath)
                        progressBar.visibility = View.GONE
                        btnDownload.isEnabled = true
                    }
                    return@launch
                }

                val url = java.net.URL(DEFAULT_MODEL_URL)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw RuntimeException("HTTP ${connection.responseCode}")
                }

                val contentLength = connection.contentLength
                var downloaded = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            if (contentLength > 0) {
                                val progress = (downloaded * 100 / contentLength).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.progress = progress
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    saveModelPath(destFile.absolutePath)
                    Toast.makeText(requireContext(), "Model downloaded successfully", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnDownload.isEnabled = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    btnDownload.isEnabled = true
                }
            }
        }
    }

    private fun handlePickedModelUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                val filename = uri.lastPathSegment?.substringAfterLast("/") ?: "picked_model.gguf"
                
                if (!filename.lowercase().endsWith(".gguf")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Please select a .gguf model file", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val destFile = File(context.filesDir, filename)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot open input stream from URI")

                // Take persistable URI permission
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                withContext(Dispatchers.Main) {
                    saveModelPath(destFile.absolutePath)
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true
                    Toast.makeText(context, "Model copied, initializing...", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle picked model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(), 
                        "Error copying model: ${e.localizedMessage ?: "Unknown error"}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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