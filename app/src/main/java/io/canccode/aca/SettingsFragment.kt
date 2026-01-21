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

            // ──────────────────────────────────────────────────────
            // This is the corrected call - single Int flags only
            // No arrays, no strings, no variables that could confuse inference
            // ──────────────────────────────────────────────────────
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
                Toast.makeText(context, "Error copying model: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }
}