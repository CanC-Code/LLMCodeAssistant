// app/src/main/java/io/canccode/aca/SettingsFragment.kt
package io.canccode.aca

import android.content.Context
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
import com.llmassistant.utils.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsFragment : Fragment() {

    companion object {
        private const val PREF_NAME = "model_prefs"

        // Made public so MainActivity can read them
        const val KEY_MODEL_PATH = "selected_model_path"
        const val KEY_MODEL_URI = "selected_model_uri"

        // Default remote model
        const val DEFAULT_MODEL_NAME = "mistral-7b-instruct-v0.2.Q4_K_M.gguf"
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDownload: Button
    private lateinit var btnPickLocal: Button
    private lateinit var btnClearModel: Button

    private val pickModelFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handlePickedModel(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        tvStatus     = view.findViewById(R.id.tv_model_status)
        progressBar  = view.findViewById(R.id.progress_model)
        btnDownload  = view.findViewById(R.id.btn_download_model)
        btnPickLocal = view.findViewById(R.id.btn_pick_local_model)
        btnClearModel = view.findViewById(R.id.btn_clear_model)

        prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        updateUI()

        btnDownload.setOnClickListener  { startDownload() }
        btnPickLocal.setOnClickListener { pickModelFile.launch("*/*") }
        btnClearModel.setOnClickListener { clearModelSelection() }

        return view
    }

    private fun updateUI() {
        val path = prefs.getString(KEY_MODEL_PATH, null)
        val uri  = prefs.getString(KEY_MODEL_URI, null)

        when {
            path != null && File(path).exists() -> {
                tvStatus.text = "Model ready:\n$path"
                progressBar.visibility = View.GONE
            }
            uri != null -> {
                tvStatus.text = "Using picked file (SAF):\n$uri"
                progressBar.visibility = View.GONE
            }
            else -> {
                tvStatus.text = "No model selected"
            }
        }
    }

    private fun saveModelPath(path: String) {
        prefs.edit()
            .putString(KEY_MODEL_PATH, path)
            .remove(KEY_MODEL_URI)
            .apply()
        updateUI()
        notifyModelChanged()
    }

    private fun saveModelUri(uri: String) {
        prefs.edit()
            .putString(KEY_MODEL_URI, uri)
            .remove(KEY_MODEL_PATH)
            .apply()
        updateUI()
        notifyModelChanged()
    }

    private fun clearModelSelection() {
        prefs.edit().clear().apply()
        updateUI()
        notifyModelChanged()
        Toast.makeText(context, "Model selection cleared", Toast.LENGTH_SHORT).show()
    }

    private fun handlePickedModel(uri: Uri) {
        // FIXED: pass flags as IntArray (required by takePersistableUriPermission)
        val flags = intArrayOf(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        requireContext().contentResolver.takePersistableUriPermission(uri, flags)

        // Copy to app-private storage (llama.cpp cannot read content:// URIs directly)
        val destFile = File(requireContext().filesDir, "picked_model.gguf")
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            saveModelPath(destFile.absolutePath)
            Toast.makeText(context, "Local model copied & selected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Failed to copy picked model", e)
            // Fallback: save URI (but won't work with llama.cpp until copied)
            saveModelUri(uri.toString())
            Toast.makeText(context, "Picked model (copy failed – may not load)", Toast.LENGTH_LONG).show()
        }
    }

    private fun startDownload() {
        btnDownload.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Downloading… 0%"

        lifecycleScope.launch(Dispatchers.IO) {
            val downloader = ModelDownloader(requireContext())

            try {
                val file = downloader.downloadModel(
                    modelUrl = DEFAULT_MODEL_URL,
                    filename = DEFAULT_MODEL_NAME,
                    expectedSha256 = null,
                    onProgress = { pct: Int ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            progressBar.progress = pct
                            tvStatus.text = "Downloading… $pct%"
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    saveModelPath(file.absolutePath)
                    Toast.makeText(context, "Download complete", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Download failed: ${e.message}"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    btnDownload.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun notifyModelChanged() {
        (activity as? MainActivity)?.onModelSelectionChanged()
    }
}