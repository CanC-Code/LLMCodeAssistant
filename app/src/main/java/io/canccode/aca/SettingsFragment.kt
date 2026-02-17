package io.canccode.aca

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsFragment : Fragment() {

    // Define the interface to communicate with MainActivity
    interface OnSettingsChangedListener {
        fun onThemeChanged(isDarkMode: Boolean)
        fun onModelSelectionChanged(modelFile: File?)
    }

    companion object {
        private const val TAG = "SettingsFragment"
        const val KEY_MODEL_PATH = "model_path"
    }

    // Shared ViewModel — the single source of truth for model state across all fragments
    private val appViewModel: AppViewModel by activityViewModels()

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPickLocal: Button
    private lateinit var btnClear: Button

    private var listener: OnSettingsChangedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSettingsChangedListener) {
            listener = context
        }
    }

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

        prefs = requireContext().getSharedPreferences("model_prefs", Context.MODE_PRIVATE)

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
                tvStatus.text = "Model: ${file.name}\nSize: ${sizeMB}MB"
            } else {
                tvStatus.text = "Model path set but file not found:\n${file.name}"
            }
        }
    }

    private fun handlePickedModelUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = context ?: return@launch

                // Get filename from URI using ContentResolver
                val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) cursor.getString(nameIndex) else "model.gguf"
                    } else "model.gguf"
                } ?: "model.gguf"

                if (!filename.lowercase().endsWith(".gguf")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Please select a valid .gguf file", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true
                    btnPickLocal.isEnabled = false
                    Toast.makeText(context, "Copying model, please wait...", Toast.LENGTH_SHORT).show()
                }

                // Copy into app's private filesDir so the native layer can open it by path
                val destFile = File(context.filesDir, filename)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    // Use ContentResolver to get the real file size for accurate progress
                    val totalSize = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        it.statSize
                    } ?: -1L

                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            if (totalSize > 0) {
                                val progress = (totalRead * 100 / totalSize).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = progress
                                }
                            }
                        }
                    }
                }

                Log.i(TAG, "Model copy complete: ${destFile.absolutePath}")

                // --- FIX: Actually initialize the LLM after copying ---
                withContext(Dispatchers.Main) {
                    progressBar.isIndeterminate = true
                    Toast.makeText(context, "Loading model into memory...", Toast.LENGTH_SHORT).show()
                }

                // Shut down any previously loaded model before loading the new one
                LlamaBridge.shutdown()

                val success = LlamaBridge.init(destFile.absolutePath, 2048)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnPickLocal.isEnabled = true

                    if (success) {
                        // Persist the path
                        prefs.edit().putString(KEY_MODEL_PATH, destFile.absolutePath).apply()
                        updateStatusText()

                        // Update the shared ViewModel so LLMFragment unlocks immediately
                        appViewModel.setModelLoaded(destFile.absolutePath)

                        // Notify MainActivity (for any legacy UI updates)
                        listener?.onModelSelectionChanged(destFile)

                        Toast.makeText(context, "✅ Model ready!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Clean up the broken copy so it doesn't appear to be valid next launch
                        destFile.delete()
                        Toast.makeText(
                            context,
                            "❌ Model failed to load. The file may be corrupt or incompatible.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy/init model", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnPickLocal.isEnabled = true
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearModelSelection() {
        val path = prefs.getString(KEY_MODEL_PATH, null)
        path?.let {
            val file = File(it)
            if (file.exists()) file.delete()
        }

        // Shut down the native layer cleanly
        LlamaBridge.shutdown()

        prefs.edit().remove(KEY_MODEL_PATH).apply()
        updateStatusText()

        // Clear the shared ViewModel so LLMFragment re-locks
        appViewModel.clearModel()

        listener?.onModelSelectionChanged(null)
        Toast.makeText(requireContext(), "Model cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
