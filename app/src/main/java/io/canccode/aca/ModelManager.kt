package io.canccode.aca

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.First
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension for simple persistence (optional but recommended)
private val Context.dataStore by preferencesDataStore(name = "model_settings")

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private val SELECTED_MODEL_URI = stringPreferencesKey("selected_model_uri")
    }

    /**
     * Converts a Uri from the File Picker into a File Descriptor and Size.
     * This is the bridge between Android's storage and the native llama.cpp.
     */
    fun getModelDescriptor(uri: Uri): ModelDescriptor? {
        return try {
            // Take persistable permission so the app can access the file even after a reboot
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                ModelDescriptor(
                    fd = pfd.detachFd(), // Get the raw int for JNI
                    size = pfd.statSize   // Get total bytes for llama.cpp
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open FileDescriptor for URI: $uri", e)
            null
        }
    }

    /**
     * Data class to bundle the requirements for initNative
     */
    data class ModelDescriptor(val fd: Int, val size: Long)

    /**
     * Persists the selected URI string so the app remembers the model on next launch.
     */
    suspend fun saveSelectedModelUri(uri: Uri) {
        context.dataStore.edit { settings ->
            settings[SELECTED_MODEL_URI] = uri.toString()
        }
    }

    /**
     * Retrieves the last used URI.
     */
    suspend fun getSavedModelUri(): Uri? {
        val uriString = context.dataStore.data.map { it[SELECTED_MODEL_URI] }.first()
        return uriString?.let { Uri.parse(it) }
    }
}
