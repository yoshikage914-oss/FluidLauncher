
package com.example.fluidlauncher

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.TypedValue

class LockScreenActivity : Activity() {

    private var startY = 0f

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun makeText(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.CENTER
            if (bold) {
                typeface = Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(30, 48, 85),
                    Color.rgb(80, 105, 145),
                    Color.rgb(20, 28, 48)
                )
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(65), dp(24), dp(30))
        }

        // Date
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        content.addView(
            date,
            LinearLayout.LayoutParams(
                -1,
                dp(35)
            )
        )

        // Large clock
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 86f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(
                "sans-serif-light",
                Typeface.NORMAL
            )
        }

        content.addView(
            clock,
            LinearLayout.LayoutParams(
                -1,
                dp(115)
            )
        )

        // Weather card
        val weatherCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBackground(
                0x33FFFFFF,
                24
            )
            setPadding(dp(20), dp(15), dp(20), dp(15))
        }

        val weatherIcon = makeText("☀", 32f, Color.WHITE)
        val weatherInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        weatherInfo.addView(
            makeText("28°", 25f, Color.WHITE, true)
        )

        weatherInfo.addView(
            makeText("Clear sky", 13f, Color.WHITE)
        )

        weatherCard.addView(weatherIcon)
        weatherCard.addView(weatherInfo)

        val weatherParams = LinearLayout.LayoutParams(
            dp(190),
            dp(85)
        ).apply {
            topMargin = dp(22)
        }

        content.addView(weatherCard, weatherParams)

        // Spacer
        val spacer = View(this)
        content.addView(
            spacer,
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        // Music widget
        val musicCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(
                0x33FFFFFF,
                22
            )
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        val musicInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        }

        musicInfo.addView(
            makeText(
                "Now playing",
                12f,
                0xCCFFFFFF.toInt()
            )
        )

        musicInfo.addView(
            makeText(
                "Your music",
                17f,
                Color.WHITE,
                true
            )
        )

        musicCard.addView(musicInfo)

        musicCard.addView(
            makeText("Ⅱ", 25f, Color.WHITE, true)
        )

        content.addView(
            musicCard,
            LinearLayout.LayoutParams(
                -1,
                dp(75)
            ).apply {
                bottomMargin = dp(30)
            }
        )

        // Swipe instruction
        val swipeText = makeText(
            "Swipe up to continue",
            13f,
            Color.WHITE
        )

        content.addView(
            swipeText,
            LinearLayout.LayoutParams(
                -1,
                dp(35)
            )
        )

        root.addView(
            content,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        // Settings shortcut
        val settingsButton = TextView(this).apply {
            text = "⚙"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(
                0x33FFFFFF,
                50
            )
            setOnClickListener {
                try {
                    startActivity(
                        Intent(Settings.ACTION_DISPLAY_SETTINGS)
                    )
                } catch (_: Exception) {
                }
            }
        }

        val settingsParams = FrameLayout.LayoutParams(
            dp(52),
            dp(52),
            Gravity.BOTTOM or Gravity.END
        ).apply {
            bottomMargin = dp(45)
            marginEnd = dp(24)
        }

        root.addView(settingsButton, settingsParams)

        setContentView(root)

        // Swipe up to close the lock-screen preview
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val difference = startY - event.y

                    if (difference > dp(90)) {
                        finish()
                    }

                    true
                }

                else -> true
            }
        }
    }
}
