package io.canccode.aca

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LLMFragment())
                .commit()
        }

        autoInitModel()
    }

    private fun autoInitModel() {
        val prefs = getSharedPreferences("model_prefs", MODE_PRIVATE)
        val path = prefs.getString("selected_model_path", null) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                val f = File(path)
                f.exists() && f.canRead() && LlamaBridge.initNative(f.absolutePath, 2048)
            } catch (t: Throwable) {
                false
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    if (ok) "Model ready" else "Model load failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}