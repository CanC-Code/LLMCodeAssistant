package io.canccode.aca

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Foreground Service that keeps the LLM inference thread alive regardless of:
 *   - Screen lock / display off
 *   - App being moved to background
 *   - System attempting to pause the process
 *
 * Architecture:
 *   Activities/Fragments bind to this service and submit inference jobs.
 *   The service runs its own CoroutineScope (SupervisorJob) so cancellation of
 *   the UI does not cancel the inference.
 *
 * Lifecycle:
 *   - Started when the model is first loaded (MainActivity.autoReloadModel)
 *   - Stays alive until the user explicitly clears the model
 *   - Stopped in onDestroy only if not currently generating
 */
class LlmService : Service() {

    companion object {
        private const val TAG              = "LlmService"
        private const val CHANNEL_ID       = "llm_inference"
        private const val NOTIF_ID         = 1001
        const val ACTION_STOP              = "io.canccode.aca.STOP_SERVICE"

        /** Convenience: start service and bind in one call. */
        fun startAndBind(context: Context): Intent =
            Intent(context, LlmService::class.java)
    }

    // Own scope — outlives any Fragment/Activity lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var isGenerating = false

    // ── Binder ────────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): LlmService = this@LlmService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // On Android 14+ (API 34+), startForeground() MUST declare the service type
        // that matches the foregroundServiceType in AndroidManifest.xml.
        // Omitting this on API 34+ causes a ForegroundServiceStartNotAllowedException.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                buildNotification("Idle — model ready"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification("Idle — model ready"))
        }

        Log.i(TAG, "LlmService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY   // Restart automatically if killed by OS
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "LlmService destroyed")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submit a generation request.
     * The [callback] is invoked from a background thread — callers must
     * dispatch UI updates themselves (e.g. with lifecycleScope.launch(Main)).
     */
    fun generate(prompt: String, maxTokens: Int, callback: LlamaBridge.GenerateCallback) {
        if (isGenerating) {
            callback.onError("Already generating — please wait")
            return
        }

        isGenerating = true
        updateNotification("Generating…")

        serviceScope.launch {
            try {
                LlamaBridge.generateNative(prompt, maxTokens, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                callback.onError(e.message ?: "Unknown error")
            } finally {
                isGenerating = false
                updateNotification("Idle — model ready")
            }
        }
    }

    fun isGenerating(): Boolean = isGenerating

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LLM Inference",
                NotificationManager.IMPORTANCE_LOW      // Silent — no sound/vibration
            ).apply {
                description = "Keeps the LLM running when the screen is off"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, LlmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ACA Code Assistant")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(status))
    }
}
