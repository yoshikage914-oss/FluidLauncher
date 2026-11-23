import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import android.media.AudioManager

private fun dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

private fun rounded(color: Int, radius: Int): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
}

private fun showControlCenter() {
    val root = window.decorView as ViewGroup

    // Prevent duplicate panels
    if (root.findViewWithTag<View>("fluid_control_center") != null) return

    val overlay = FrameLayout(this).apply {
        tag = "fluid_control_center"
        setBackgroundColor(0x99000000.toInt())
        isClickable = true
    }

    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(28))
        background = rounded(0xFF17191F.toInt(), 30)
        elevation = dp(20).toFloat()
        isClickable = true
    }

    val scroll = ScrollView(this)
    scroll.addView(panel)

    val panelParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(590),
        Gravity.BOTTOM
    ).apply {
        setMargins(dp(8), 0, dp(8), dp(8))
    }

    overlay.addView(scroll, panelParams)

    overlay.setOnClickListener {
        root.removeView(overlay)
    }

    panel.setOnClickListener { }

    fun heading(text: String, size: Float = 25f) {
        panel.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(8), 0, dp(12))
        })
    }

    fun tile(text: String, subtitle: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(8), dp(12))
            background = rounded(0xFF303640.toInt(), 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

        card.addView(TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })

        card.addView(TextView(this).apply {
            this.text = subtitle
            textSize = 12f
            setTextColor(0xFFB9C0CC.toInt())
        })

        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = dp(88)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }

        grid.addView(card, params)
    }

    val grid = GridLayout(this).apply {
        columnCount = 2
    }

    heading("Control centre")
    heading("Quick settings", 15f)

    tile("Wi-Fi", "Network settings") {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    tile("Bluetooth", "Connected devices") {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    tile("Internet", "Mobile data and networks") {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        } else {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    tile("Airplane mode", "Network controls") {
        startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    }

    tile("Flashlight", "Flashlight controls") {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    tile("Settings", "System settings") {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    panel.addView(grid)

    heading("Brightness", 17f)

    val brightness = SeekBar(this).apply {
        max = 255
        progress = 180
        progressTintList = android.content.res.ColorStateList.valueOf(
            0xFF8DBBFF.toInt()
        )
        thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)

        setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    val attributes = window.attributes
                    attributes.screenBrightness =
                        (progress.coerceAtLeast(1) / 255f)
                    window.attributes = attributes
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    panel.addView(brightness)

    heading("Volume", 17f)

    val audio = getSystemService(AUDIO_SERVICE) as AudioManager

    val volume = SeekBar(this).apply {
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

        setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    audio.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        progress,
                        0
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    panel.addView(volume)

    heading("Fluid Launcher", 14f)

    panel.addView(TextView(this).apply {
        text = "HyperOS-inspired quick settings"
        textSize = 12f
        setTextColor(0xFF9DA8B8.toInt())
    })

    root.addView(
        overlay,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
}
