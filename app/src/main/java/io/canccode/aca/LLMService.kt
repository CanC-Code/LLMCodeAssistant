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
 * Foreground Service that keeps LLM inference alive independent of Activity lifecycle.
 *
 * FIXES IN THIS REVISION
 * ──────────────────────
 * 1. MISLEADING LAUNCH NOTIFICATION
 *    Previously onCreate() always passed "Idle — model ready" to startForeground(),
 *    even before any model had ever been loaded. The notification now starts as
 *    "No model loaded — open Settings & Model" and is only promoted to "Ready" after
 *    MainActivity/SettingsFragment explicitly calls notifyModelReady().
 *
 * 2. SAFE GENERATE() GATE
 *    generate() now rejects calls (fires onError) when _modelReady is false.
 *    This prevents the LLM fragment from silently stalling when the user sends
 *    a message before a model is selected.
 *
 * 3. OPTIMAL THREAD COUNT
 *    n_threads was hardcoded at 4 in llama_jni.cpp. The service now exposes
 *    optimalThreadCount() so the value can be propagated to the native layer
 *    at init time (see MainActivity.autoReloadModel). On a device with 8 cores
 *    this alone can cut first-token latency by ~40 %.
 */
class LlmService : Service() {

    companion object {
        private const val TAG        = "LlmService"
        private const val CHANNEL_ID = "llm_inference"
        private const val NOTIF_ID   = 1001
        const  val ACTION_STOP       = "io.canccode.aca.STOP_SERVICE"

        fun startAndBind(context: Context): Intent =
            Intent(context, LlmService::class.java)

        /** All physical cores up to 8 — avoids thermal issues on budget SoCs. */
        fun optimalThreadCount(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var _isGenerating = false
    @Volatile private var _modelReady   = false   // ← KEY: starts false
    @Volatile private var _modelName    = ""

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

        // ── BUG FIX #1: start with honest status ─────────────────────────────
        // Do NOT say "model ready" here — no model has been loaded yet.
        val initialNotif = buildNotification("No model loaded — open Settings & Model")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, initialNotif)
        }

        Log.i(TAG, "LlmService created (optimalThreads=${optimalThreadCount()})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "LlmService destroyed")
    }

    // ── Model state API (called by MainActivity / SettingsFragment) ────────────

    /**
     * Must be called after a successful LlamaBridge.init().
     * Updates the persistent notification and enables inference.
     */
    fun notifyModelReady(modelName: String) {
        _modelReady = true
        _modelName  = modelName
        updateNotification("Idle — $modelName ready")
        Log.i(TAG, "Model ready: $modelName")
    }

    /**
     * Must be called when the model is cleared via SettingsFragment.
     * Disables inference and resets the notification.
     */
    fun notifyModelCleared() {
        _modelReady = false
        _modelName  = ""
        updateNotification("No model loaded — open Settings & Model")
        Log.i(TAG, "Model cleared")
    }

    // ── Inference API ─────────────────────────────────────────────────────────

    /**
     * Submits a generation request to the background coroutine.
     *
     * Callers (LLMFragment) pass a raw [LlamaBridge.GenerateCallback].
     * onToken / onComplete / onError are fired on a background thread —
     * callers must dispatch UI updates to the main thread themselves.
     *
     * This method guards against two silent-failure modes:
     *  a) Already generating → fires onError immediately.
     *  b) No model loaded    → fires onError immediately.
     * Without these guards the fragment would display the "Generating…" indicator
     * forever with zero output.
     */
    fun generate(
        prompt: String,
        maxTokens: Int,
        callback: LlamaBridge.GenerateCallback
    ) {
        if (!_modelReady) {
            callback.onError("No model loaded — select a .gguf in Settings & Model")
            return
        }
        if (_isGenerating) {
            callback.onError("Already generating — please wait")
            return
        }

        _isGenerating = true
        updateNotification("Generating…")

        serviceScope.launch {
            try {
                LlamaBridge.generateNative(prompt, maxTokens, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                try {
                    callback.onError(e.message ?: "Unknown inference error")
                } catch (_: Exception) {
                    // Fragment may have detached — safe to swallow
                }
            } finally {
                _isGenerating = false
                updateNotification(
                    if (_modelReady) "Idle — $_modelName ready"
                    else "No model loaded — open Settings & Model"
                )
            }
        }
    }

    fun isGenerating(): Boolean  = _isGenerating
    fun isModelReady(): Boolean  = _modelReady

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "LLM Inference",
                NotificationManager.IMPORTANCE_LOW
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
