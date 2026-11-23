package com.example.fluidlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#101014"))
        root.setPadding(dp(20), dp(48), dp(20), dp(20))

        val title = TextView(this)
        title.text = "Fluid Launcher settings"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        val titleLp = LinearLayout.LayoutParams(MATCH, WRAP)
        titleLp.bottomMargin = dp(20)
        root.addView(title, titleLp)

        addRow(root, "Set as default home app", Intent(Settings.ACTION_HOME_SETTINGS))
        addRow(
            root,
            "Change wallpaper",
            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")
        )
        addRow(root, "Phone lock screen settings", Intent(Settings.ACTION_SECURITY_SETTINGS))
        addRow(root, "Display settings", Intent(Settings.ACTION_DISPLAY_SETTINGS))

        setContentView(root)
    }

    private fun addRow(root: LinearLayout, label: String, intent: Intent) {
        val t = TextView(this)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.textSize = 16f
        t.setPadding(dp(18), dp(16), dp(18), dp(16))
        t.background = glass(Color.parseColor("#33FFFFFF"), 20)
        t.bounceOnPress()
        t.setOnClickListener {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Not available on this phone", Toast.LENGTH_SHORT).show()
            }
        }
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = dp(12)
        root.addView(t, lp)
    }
} 
