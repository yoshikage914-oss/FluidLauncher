package com.example.fluidlauncher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("fluid_settings", MODE_PRIVATE)
    }

    private val white = Color.WHITE
    private val muted = 0xFFAAAAAA.toInt()
    private val backgroundColor = 0xFF101114.toInt()
    private val cardColor = 0xFF202126.toInt()
    private lateinit var root: LinearLayout

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun section(title: String) {
        val heading = text(title, 14f, muted, true)
        heading.setPadding(dp(5), dp(22), 0, dp(10))
        root.addView(heading)
    }

    private fun setting(
        title: String,
        description: String,
        action: () -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(15), dp(17), dp(15))
            background = rounded(cardColor, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

        card.addView(text(title, 16f, white, true))

        val subtitle = text(description, 12f, muted)
        subtitle.setPadding(0, dp(5), 0, 0)
        card.addView(subtitle)

        root.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(9)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(35), dp(20), dp(30))
        }

        root.addView(text("Settings", 32f, white, true))
        root.addView(
            text("Customize your Fluid Launcher", 14f, muted).apply {
                setPadding(0, dp(5), 0, dp(18))
            }
        )

        section("PERSONALIZATION")

        setting(
            "Wallpaper",
            "Choose a wallpaper for your home screen"
        ) {
            try {
                startActivity(
                    Intent(Intent.ACTION_SET_WALLPAPER)
                )
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Wallpaper picker unavailable",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        setting(
            "Lock screen",
            "Preview the Fluid lock screen"
        ) {
            startActivity(
                Intent(this, LockScreenActivity::class.java)
            )
        }

        section("APPEARANCE")

        val darkMode = Switch(this).apply {
            text = "Dark mode"
            textSize = 16f
            setTextColor(white)
            isChecked = prefs.getBoolean("dark_mode", true)

            setOnCheckedChangeListener { _, checked ->
                prefs.edit()
                    .putBoolean("dark_mode", checked)
                    .apply()

                Toast.makeText(
                    this@SettingsActivity,
                    if (checked) "Dark mode enabled"
                    else "Dark mode disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(
            darkMode,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        val iconTitle = text(
            "Icon size",
            16f,
            white,
            true
        )

        iconTitle.setPadding(0, dp(18), 0, dp(5))
        root.addView(iconTitle)

        val iconValue = text(
            "Size: ${prefs.getInt("icon_size", 100)}%",
            13f,
            muted
        )
        root.addView(iconValue)

        val iconSlider = SeekBar(this).apply {
            max = 50
            progress = prefs.getInt("icon_size", 100) - 75

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        val size = progress + 75
                        iconValue.text = "Size: $size%"
                        prefs.edit()
                            .putInt("icon_size", size)
                            .apply()
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        root.addView(
            iconSlider,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        section("ABOUT")

        setting(
            "Fluid Launcher",
            "Version 2.0 • HyperOS-inspired edition"
        ) {
            Toast.makeText(
                this,
                "Fluid Launcher",
                Toast.LENGTH_SHORT
            ).show()
        }

        setting(
            "Reset preferences",
            "Restore the launcher's default settings"
        ) {
            prefs.edit().clear().apply()
            Toast.makeText(
                this,
                "Preferences reset. Reopen settings.",
                Toast.LENGTH_SHORT
            ).show()
        }

        scroll.addView(root)
        setContentView(scroll)
    }
}
