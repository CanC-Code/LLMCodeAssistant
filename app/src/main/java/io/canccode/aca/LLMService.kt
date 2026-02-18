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
 * Foreground Service that keeps the LLM inference coroutine alive regardless of:
 *   - Screen lock / display off
 *   - App being moved to background
 *   - System attempting to pause the process
 *
 * Architecture:
 *   Activities/Fragments bind to this service and submit inference jobs.
 *   The service runs its own CoroutineScope (SupervisorJob) so cancellation
 *   of UI lifecycles does NOT cancel in-flight inference.
 *
 * Android 14+ (API 34) requirements for specialUse foreground services:
 *   1. AndroidManifest: foregroundServiceType="specialUse"
 *   2. AndroidManifest: FOREGROUND_SERVICE_SPECIAL_USE permission
 *   3. AndroidManifest: PROPERTY_SPECIAL_USE_FGS_SUBTYPE property in <service>
 *   4. Code: startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
 *      on API 34+  ← this file handles #4.
 *
 * The type passed to startForeground() MUST match the manifest declaration.
 * Mismatching causes InvalidForegroundServiceTypeException (immediate crash).
 */
class LlmService : Service() {

    companion object {
        private const val TAG        = "LlmService"
        private const val CHANNEL_ID = "llm_inference"
        private const val NOTIF_ID   = 1001
        const val ACTION_STOP        = "io.canccode.aca.STOP_SERVICE"

        /** Returns an Intent that starts this service. Use with startService() + bindService(). */
        fun startAndBind(context: Context): Intent =
            Intent(context, LlmService::class.java)
    }

    /** SupervisorJob: one failed coroutine does not cancel siblings. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Volatile so reads from the UI thread see writes from the coroutine thread. */
    @Volatile private var isGenerating = false

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

        // API 34 (UPSIDE_DOWN_CAKE) requires the service type to be specified
        // explicitly. The type MUST be SPECIAL_USE to match AndroidManifest.xml.
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
        if (intent?.action == ACTION_STOP) stopSelf()
        // START_STICKY: if the OS kills the service, restart it with a null intent
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "LlmService destroyed")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submits a generation request to the service's coroutine scope.
     * [callback] is invoked from a background thread — callers must dispatch
     * UI updates to the main thread themselves (e.g. runOnUiThread / lifecycleScope).
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
                callback.onError(e.message ?: "Unknown inference error")
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
                NotificationManager.IMPORTANCE_LOW   // Silent — no sound/vibration
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
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status))
    }
}
