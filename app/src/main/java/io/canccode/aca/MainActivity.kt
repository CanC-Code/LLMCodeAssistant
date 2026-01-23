package io.canccode.aca

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, LLMFragment())
            }
        }

        // Make floating menu button draggable + clickable
        findViewById<ImageView>(R.id.floatingMenuButton)?.let { btn ->
            makeButtonDraggableAndClickable(btn)
        }

        autoLoadLastModel()
    }

    private fun autoLoadLastModel() {
        val prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
        val path = prefs.getString("selected_model_path", null) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(path)
            val success = try {
                file.exists() && file.canRead() &&
                        LlamaBridge.initNative(file.absolutePath, 2048)
            } catch (e: Throwable) {
                Log.e(TAG, "Model init failed", e)
                false
            }

            withContext(Dispatchers.Main) {
                val msg = if (success) "Model loaded successfully ✓" else "Failed to load model"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                Log.i(TAG, "$msg → $path")
            }
        }
    }

    private fun makeButtonDraggableAndClickable(view: View) {
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        var isDrag = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.rawX - startX)
                    val dy = abs(event.rawY - startY)
                    if (dx > 12 || dy > 12) {
                        isDrag = true
                        v.animate()
                            .x(event.rawX + dX)
                            .y(event.rawY + dY)
                            .setDuration(0)
                            .start()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) {
                        // You can open drawer / menu here later
                        Toast.makeText(this, "Menu button tapped", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LlamaBridge.shutdownNative()
    }
}