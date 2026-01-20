package io.canccode.aca

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var floatingMenuButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate UI FIRST so fragments & touch handling work
        setContentView(R.layout.activity_main)

        // -----------------------------
        // Initialize drawer & hamburger
        // -----------------------------
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.drawer_open, R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // -----------------------------
        // Floating menu button (click + drag)
        // -----------------------------
        floatingMenuButton = findViewById(R.id.floatingMenuButton)
        floatingMenuButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(navView)) {
                drawerLayout.closeDrawer(navView)
            } else {
                drawerLayout.openDrawer(navView)
            }
        }

        enableDrag(floatingMenuButton)

        // -----------------------------
        // Attach LLM Fragment
        // -----------------------------
        supportFragmentManager.beginTransaction()
            .replace(R.id.llm_container, LLMFragment())
            .commit()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (drawerLayout.isDrawerOpen(navView)) {
            drawerLayout.closeDrawer(navView)
            true
        } else {
            drawerLayout.openDrawer(navView)
            true
        }
    }

    private fun enableDrag(view: View) {
        var dX = 0f
        var dY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                    true
                }
                else -> false
            }
        }
    }

    // Optional: force keyboard to open when focusing LLM input
    fun focusLLMInput(editText: EditText) {
        editText.requestFocus()
        val imm = getSystemService<InputMethodManager>()
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
}