package com.example.fluidlauncher
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
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import android.widget.TextClock
import kotlin.math.abs
class MainActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences("fluid_launcher", MODE_PRIVATE)
    }
    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var grid: GridView
    private lateinit var search: EditText
    private var apps = mutableListOf<AppItem>()
    private var filtered = mutableListOf<AppItem>()
    private var startY = 0f
    private var drawerOpen = false
    private val darkMode get() = prefs.getBoolean("dark", true)
    private data class AppItem(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun background(color: Int, radius: Int): GradientDrawable {
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
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        buildHome()
        loadApps()
    }
    private fun buildHome() {
        root = FrameLayout(this)
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(55), dp(18), dp(25))
            setBackgroundColor(Color.rgb(18, 20, 27))
        }
        // Top date
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }
        home.addView(date)
        // Large HyperOS-inspired clock
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 76f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(
                "sans-serif-light",
                Typeface.NORMAL
            )
            gravity = Gravity.CENTER
        }
        home.addView(
            clock,
            LinearLayout.LayoutParams(-1, dp(125))
        )
        // Weather card placeholder
        val weather = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = background(
                Color.rgb(38, 43, 55), 25
            )
        }
        weather.addView(
            text("☀", 30f, Color.WHITE)
        )
        weather.addView(
            text("  Weather  •  Add weather widget", 14f, Color.WHITE)
        )
        home.addView(
            weather,
            LinearLayout.LayoutParams(-1, dp(75)).apply {
                topMargin = dp(18)
            }
        )
        // Quick shortcuts
        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addShortcut(shortcuts, "Apps", "▦") {
            showDrawer()
        }
        addShortcut(shortcuts, "Settings", "⚙") {
            openSettings()
        }
        addShortcut(shortcuts, "Wallpaper", "◈") {
            openWallpaperPicker()
        }
        addShortcut(shortcuts, "Control", "☷") {
            openControlCenter()
        }
        home.addView(
            shortcuts,
            LinearLayout.LayoutParams(-1, dp(100)).apply {
                topMargin = dp(12)
            }
        )
        val spacer = Space(this)
        home.addView(
            spacer,
            LinearLayout.LayoutParams(1, 0, 1f)
        )
        // App drawer button
        val appButton = text(
            "Swipe up or tap to open apps",
            13f,
            Color.LTGRAY
        )
        appButton.setOnClickListener {
            showDrawer()
        }
        home.addView(appButton)

        // Bottom dock
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = background(
                Color.rgb(43, 48, 61), 32
            )
        }
        home.addView(
            dock,
            LinearLayout.LayoutParams(-1, dp(75)).apply {
                topMargin = dp(15)
            }
        )
        root.addView(home)

        buildDrawer()

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val difference = startY - event.y
                    if (difference > dp(65)) showDrawer()
                    true
                }
                else -> true
            }
        }
        home.setOnLongClickListener {
            showPersonalization()
            true
        }
        setContentView(root)
    }
    private fun addShortcut(
        parent: LinearLayout,
        title: String,
        symbol: String,
        action: () -> Unit
    ) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { action() }
        }

        item.addView(
            text(symbol, 27f, Color.WHITE)
        )

        item.addView(
            text(title, 11f, Color.LTGRAY)
        )

        parent.addView(
            item,
            LinearLayout.LayoutParams(0, dp(85), 1f)
        )
    }

    private fun buildDrawer() {
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(55), dp(18), dp(20))
            setBackgroundColor(Color.rgb(23, 26, 34))
            visibility = View.GONE
        }

        val heading = text(
            "All apps",
            29f,
            Color.WHITE,
            true
        )

        heading.gravity = Gravity.START

        drawer.addView(heading)

        search = EditText(this).apply {
            hint = "Search applications"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 15f
            singleLine = true
            setPadding(dp(18), 0, dp(18), 0)
            background = background(
                Color.rgb(45, 50, 63), 28
            )
        }

        drawer.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(52)).apply {
                topMargin = dp(18)
                bottomMargin = dp(15)
            }
        )

        grid = GridView(this).apply {
            numColumns = 4
            horizontalSpacing = dp(5)
            verticalSpacing = dp(10)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            adapter = appAdapter
        }

        drawer.addView(
            grid,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val close = text("Close apps", 14f, Color.LTGRAY)
        close.setOnClickListener { hideDrawer() }

        drawer.addView(
            close,
            LinearLayout.LayoutParams(-1, dp(45))
        )

        root.addView(drawer)

        grid.setOnItemClickListener { _, _, position, _ ->
            launchApp(filtered[position])
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int,
                count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int,
                before: Int, count: Int
            ) {
                val query = s.toString().trim()

                filtered = apps.filter {
                    it.name.contains(query, ignoreCase = true)
                }.toMutableList()

                appAdapter.notifyDataSetChanged()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private val appAdapter = object : BaseAdapter() {

        override fun getCount() = filtered.size

        override fun getItem(position: Int) = filtered[position]

        override fun getItemId(position: Int) =
            position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            val app = filtered[position]

            val cell = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(8), dp(3), dp(8))
            }

            val icon = ImageView(this@MainActivity).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            cell.addView(
                icon,
                LinearLayout.LayoutParams(dp(48), dp(48))
            )

            cell.addView(
                text(app.name, 11f, Color.WHITE).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
            )

            return cell
        }
    }

    private fun loadApps() {
        apps.clear()

        val pm = packageManager

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val results = pm.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        )

        for (result in results) {
            val info = result.activityInfo
            if (info.packageName == packageName) continue

            try {
                apps.add(
                    AppItem(
                        info.loadLabel(pm).toString(),
                        info.packageName,
                        info.loadIcon(pm)
                    )
                )
            } catch (_: Exception) {
            }
        }

        apps.sortBy { it.name.lowercase() }
        filtered = apps.toMutableList()

        if (::grid.isInitialized) {
            appAdapter.notifyDataSetChanged()
        }
    }

    private fun launchApp(app: AppItem) {
        val intent = packageManager.getLaunchIntentForPackage(
            app.packageName
        )

        if (intent != null) {
            startActivity(intent)
        }
    }

    private fun showDrawer() {
        drawer.visibility = View.VISIBLE
        drawer.alpha = 0f
        drawer.translationY = dp(35).toFloat()
        drawer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .start()

        drawerOpen = true
    }

    private fun hideDrawer() {
        drawer.animate()
            .alpha(0f)
            .translationY(dp(30).toFloat())
            .setDuration(180)
            .withEndAction {
                drawer.visibility = View.GONE
                drawer.translationY = 0f
            }
            .start()

        drawerOpen = false
    }

    private fun openWallpaperPicker() {
        try {
            startActivity(
                Intent(Intent.ACTION_SET_WALLPAPER)
            )
        } catch (_: Exception) {
            Toast.makeText(
                this, "Wallpaper picker unavailable",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openSettings() {
        val options = arrayOf(
            "Dark mode",
            "Light mode",
            "Personalization",
            "Lock screen",
            "About Fluid Launcher"
        )

        AlertDialog.Builder(this)
            .setTitle("Fluid Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setDarkMode(true)
                    1 -> setDarkMode(false)
                    2 -> showPersonalization()
                    3 -> startActivity(
                        Intent(this, LockScreenActivity::class.java)
                    )
                    4 -> showAbout()
                }
            }
            .show()
    }
    private fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark", enabled).apply()
        Toast.makeText(
            this,
            "Restart launcher to apply theme",
            Toast.LENGTH_SHORT
        ).show()
    }
    private fun openControlCenter() {
        val options = arrayOf(
            "Wi-Fi settings",
            "Bluetooth settings",
            "Display settings",
            "Sound settings"
        )
        AlertDialog.Builder(this)
            .setTitle("Control Center")
            .setItems(options) { _, which ->
                val action = when (which) {
                    0 -> android.provider.Settings.ACTION_WIFI_SETTINGS
                    1 -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                    2 -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
                    else -> android.provider.Settings.ACTION_SOUND_SETTINGS
                }
                try {
                    startActivity(Intent(action))
                } catch (_: Exception) {
                    Toast.makeText(
                        this, "Setting unavailable",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }
    private fun showPersonalization() {
        val options = arrayOf(
            "Change wallpaper",
            "Launcher settings",
            "Lock screen preview"
        )
        AlertDialog.Builder(this)
            .setTitle("Personalize")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openWallpaperPicker()
                    1 -> openSettings()
                    2 -> startActivity(
                        Intent(this, LockScreenActivity::class.java)
                    )
                }
            }
            .show()
    }
    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Fluid Launcher")
            .setMessage(
                "HyperOS-inspired Android launcher\nVersion 3.0\nMade for Android 14 and above."
            )
            .setPositiveButton("OK", null)
            .show()
    }
    override fun onBackPressed() {
        if (drawerOpen) {
            hideDrawer()
        } else {
            super.onBackPressed()
        }
    }
}
