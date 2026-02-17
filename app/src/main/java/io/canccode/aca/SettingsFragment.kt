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

    interface OnSettingsChangedListener {
        fun onThemeChanged(isDarkMode: Boolean)
        fun onModelSelectionChanged(modelFile: File?)
    }

    companion object {
        private const val TAG            = "SettingsFragment"
        const val PREF_NAME              = "model_prefs"
        const val KEY_MODEL_PATH         = "model_path"
    }

    private val appViewModel: AppViewModel by activityViewModels()

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPickLocal: Button
    private lateinit var btnClear: Button

    private var listener: OnSettingsChangedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSettingsChangedListener) listener = context
    }

    private val pickModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { handlePickedModelUri(it) } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs        = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        tvStatus     = view.findViewById(R.id.tv_model_status)
        progressBar  = view.findViewById(R.id.progress_model)
        btnPickLocal = view.findViewById(R.id.btn_pick_local_model)
        btnClear     = view.findViewById(R.id.btn_clear_model)

        updateStatusText()

        btnPickLocal.setOnClickListener { pickModelLauncher.launch(arrayOf("*/*")) }
        btnClear.setOnClickListener    { clearModelSelection() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateStatusText() {
        val path = prefs.getString(KEY_MODEL_PATH, null)
        tvStatus.text = when {
            path.isNullOrEmpty() -> "No model selected"
            else -> {
                val file = File(path)
                if (file.exists()) {
                    val sizeMB = file.length() / (1024 * 1024)
                    "Model: ${file.name}\nSize: ${sizeMB}MB"
                } else {
                    prefs.edit().remove(KEY_MODEL_PATH).apply()
                    "Saved model not found — please re-select"
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pick & import
    // ─────────────────────────────────────────────────────────────────────────

    private fun handlePickedModelUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ctx = context ?: return@launch

                val filename = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) cursor.getString(idx) else "model.gguf"
                    } else "model.gguf"
                } ?: "model.gguf"

                if (!filename.lowercase().endsWith(".gguf")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Please select a .gguf file", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility      = View.VISIBLE
                    progressBar.isIndeterminate = true
                    btnPickLocal.isEnabled      = false
                    tvStatus.text               = "Copying model…"
                    Toast.makeText(ctx, "Copying model, please wait…", Toast.LENGTH_SHORT).show()
                }

                val destFile = File(ctx.filesDir, filename)

                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    val totalSize = ctx.contentResolver.openFileDescriptor(uri, "r")
                        ?.use { it.statSize } ?: -1L

                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalSize > 0) {
                                val pct = (totalRead * 100 / totalSize).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.isIndeterminate = false
                                    progressBar.progress        = pct
                                    tvStatus.text               = "Copying… $pct%"
                                }
                            }
                        }
                    }
                }

                Log.i(TAG, "Copy complete: ${destFile.absolutePath}")

                withContext(Dispatchers.Main) {
                    progressBar.isIndeterminate = true
                    tvStatus.text               = "Loading model into memory…"
                }

                initModel(destFile)

            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnPickLocal.isEnabled = true
                    tvStatus.text          = "Import failed: ${e.message}"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model init — also called by MainActivity on cold start
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shuts down any running model, inits [file], and if successful:
     *   - persists the absolute path to SharedPreferences
     *   - updates the shared ViewModel so LLMFragment unlocks
     *
     * KEY FIX for "model forgotten on close":
     * We save the ABSOLUTE PATH of the private-storage copy (inside filesDir).
     * On every cold start, MainActivity reads this and calls initModel() again,
     * so the model is always ready without the user having to re-pick it.
     */
    suspend fun initModel(file: File) {
        withContext(Dispatchers.IO) {
            LlamaBridge.shutdown()
            val success = LlamaBridge.init(file.absolutePath, 4096)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnPickLocal.isEnabled = true

                if (success) {
                    prefs.edit().putString(KEY_MODEL_PATH, file.absolutePath).apply()
                    appViewModel.setModelLoaded(file.absolutePath)
                    listener?.onModelSelectionChanged(file)
                    updateStatusText()
                    Toast.makeText(context, "✅ Model ready!", Toast.LENGTH_SHORT).show()
                } else {
                    file.delete()
                    tvStatus.text = "Load failed — file may be corrupt or incompatible"
                    Toast.makeText(
                        context,
                        "❌ Model failed to load. File may be corrupt.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clear
    // ─────────────────────────────────────────────────────────────────────────

    private fun clearModelSelection() {
        val path = prefs.getString(KEY_MODEL_PATH, null)
        path?.let { File(it).delete() }

        LlamaBridge.shutdown()
        prefs.edit().remove(KEY_MODEL_PATH).apply()

        appViewModel.clearModel()
        listener?.onModelSelectionChanged(null)
        updateStatusText()
        Toast.makeText(requireContext(), "Model cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
