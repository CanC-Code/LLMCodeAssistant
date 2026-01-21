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
import java.io.FileOutputStream

class SettingsFragment : Fragment() {

    companion object {
        private const val PREF_NAME = "model_prefs"
        const val KEY_MODEL_PATH = "selected_model_path"

        private const val DEFAULT_MODEL_NAME = "mistral-7b-instruct-v0.2.Q4_K_M.gguf"
        private const val DEFAULT_MODEL_URL =
            "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDownload: Button
    private lateinit var btnPickLocal: Button
    private lateinit var btnClear: Button

    private val pickModelFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handlePickedModelUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        tvStatus    = view.findViewById(R.id.tv_model_status)
        progressBar = view.findViewById(R.id.progress_model)
        btnDownload = view.findViewById(R.id.btn_download_model)
        btnPickLocal = view.findViewById(R.id.btn_pick_local_model)
        btnClear     = view.findViewById(R.id.btn_clear_model)

        prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        updateModelStatus()

        btnDownload.setOnClickListener  { startDownloadDefaultModel() }
        btnPickLocal.setOnClickListener { pickModelFile.launch("application/octet-stream") }
        btnClear.setOnClickListener     { clearModel() }

        return view
    }

    private fun updateModelStatus() {
        val path = prefs.getString(KEY_MODEL_PATH, null)
        if (path.isNullOrBlank()) {
            tvStatus.text = "No model selected"
            return
        }

        val file = File(path)
        tvStatus.text = if (file.exists() && file.canRead()) {
            "Model ready:\n${file.name}\n(${file.length() / 1_048_576} MB)"
        } else {
            "Model path invalid or file missing:\n$path"
        }
    }

    private fun saveModelPath(path: String) {
        prefs.edit()
            .putString(KEY_MODEL_PATH, path)
            .apply()
        updateModelStatus()
        notifyMainActivity()
    }

    private fun clearModel() {
        prefs.edit().clear().apply()
        updateModelStatus()
        notifyMainActivity()
        Toast.makeText(context, "Model selection cleared", Toast.LENGTH_SHORT).show()
    }

    private fun notifyMainActivity() {
        (activity as? MainActivity)?.onModelSelectionChanged()
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

                // Correct flags usage - single Int value
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                withContext(Dispatchers.Main) {
                    saveModelPath(destFile.absolutePath)
                    Toast.makeText(context, "Model copied and selected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Failed to handle picked model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error copying model: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startDownloadDefaultModel() {
        btnDownload.isEnabled = false
        btnPickLocal.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Downloading… 0%"

        lifecycleScope.launch(Dispatchers.IO) {
            val downloader = ModelDownloader(requireContext())

            try {
                val file = downloader.downloadModel(
                    modelUrl = DEFAULT_MODEL_URL,
                    filename = DEFAULT_MODEL_NAME,
                    expectedSha256 = null,
                    onProgress = { pct ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            progressBar.progress = pct
                            tvStatus.text = "Downloading… $pct%"
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    saveModelPath(file.absolutePath)
                    Toast.makeText(context, "Download complete — model ready", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Download failed", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Download failed: ${e.localizedMessage?.take(80)}"
                    Toast.makeText(context, "Download error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    btnDownload.isEnabled = true
                    btnPickLocal.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
}