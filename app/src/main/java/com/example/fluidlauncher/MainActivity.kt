package com.example.fluidlauncher
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var audio: AudioManager

    private val apps = mutableListOf<android.content.pm.ResolveInfo>()

    private var darkMode = true
    private var labels = true
    private var columns = 4

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).roundToInt()

    private fun glass(
        color: String = "#66777777",
        radius: Int = 28
    ) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.parseColor("#35FFFFFF"))
    }

    private fun label(
        value: String,
        size: Float = 15f,
        color: Int = Color.WHITE
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun button(
        value: String,
        action: () -> Unit
    ) = TextView(this).apply {
        text = value
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(13), dp(12), dp(13))
        background = glass("#66777777", 24)
        setOnClickListener { action() }
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun setup(title: String) {
        root = FrameLayout(this)

        val page = column().apply {
            setPadding(dp(18), dp(12), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(20, 21, 29),
                    Color.rgb(37, 39, 49),
                    Color.rgb(18, 19, 26)
                )
            )
        }

        val header = row()

        header.addView(
            label(title, 25f),
            LinearLayout.LayoutParams(0, dp(65), 1f)
        )

        header.addView(
            button("⚙") { showSettings() },
            LinearLayout.LayoutParams(dp(55), dp(55))
        )

        page.addView(header)

        content = column()

        page.addView(
            content,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        window.statusBarColor = Color.rgb(20, 21, 29)
        window.navigationBarColor = Color.rgb(20, 21, 29)

        loadApps()
        showHome()
    }

    private fun loadApps() {
        apps.clear()

        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy {
                it.loadLabel(packageManager).toString().lowercase()
            }
            .forEach { apps.add(it) }
    }

    // HOME SCREEN

    private fun showHome() {
        setup("FluidLauncher")

        val clock = label(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            66f
        )
        clock.gravity = Gravity.CENTER

        content.addView(clock)

        val date = label(
            SimpleDateFormat(
                "EEEE, dd MMMM",
                Locale.getDefault()
            ).format(Date()),
            15f,
            Color.LTGRAY
        )

        date.gravity = Gravity.CENTER
        content.addView(date)

        val weather = button("☁   Weather   •   Add weather provider") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }

        val weatherLp = LinearLayout.LayoutParams(-1, dp(55))
        weatherLp.setMargins(0, dp(22), 0, dp(12))
        content.addView(weather, weatherLp)

        content.addView(
            button("⌕       Search applications") { showDrawer() }
        )

        val shortcuts = row()

        listOf(
            "Apps" to { showDrawer() },
            "Control" to { showControlCenter() },
            "Wallpaper" to { chooseWallpaper() }
        ).forEach { item ->
            val lp = LinearLayout.LayoutParams(0, dp(76), 1f)
            lp.setMargins(dp(4), dp(12), dp(4), dp(12))
            shortcuts.addView(button(item.first, item.second), lp)
        }

        content.addView(shortcuts)

        val title = label("Your applications", 18f)
        title.setPadding(0, dp(12), 0, dp(8))
        content.addView(title)

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = columns
        }

        apps.take(16).forEach {
            grid.addView(
                appTile(it),
                GridLayout.LayoutParams().apply {
                    width = resources.displayMetrics.widthPixels / columns
                    height = dp(100)
                }
            )
        }

        scroll.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val dock = row().apply {
            background = glass("#99505050", 38)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            gravity = Gravity.CENTER
        }

        listOf("☎", "🌐", "📷", "▦").forEachIndexed { index, icon ->
            val item = label(icon, 26f)
            item.gravity = Gravity.CENTER
            item.setOnClickListener {
                when (index) {
                    0 -> openSystem(Settings.ACTION_DIAL)
                    1 -> showDrawer()
                    2 -> openSystem(Settings.ACTION_SETTINGS)
                    3 -> showDrawer()
                }
            }
            dock.addView(item, LinearLayout.LayoutParams(0, dp(55), 1f))
        }

        content.addView(dock)
    }

    private fun appTile(info: android.content.pm.ResolveInfo): View {
        val cell = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        val icon = ImageView(this).apply {
            setImageDrawable(info.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        cell.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

        if (labels) {
            val name = label(
                info.loadLabel(packageManager).toString(),
                11f
            )
            name.gravity = Gravity.CENTER
            name.maxLines = 1
            cell.addView(name)
        }

        cell.setOnClickListener {
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(
                    info.activityInfo.packageName,
                    info.activityInfo.name
                )
            startActivity(launch)
        }

        return cell
    }

    // APP DRAWER

    private fun showDrawer() {
        setup("Applications")

        val search = EditText(this).apply {
            hint = "Search apps..."
            setSingleLine()
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(16), 0, dp(16), 0)
            background = glass("#55444444", 25)
        }

        content.addView(search, LinearLayout.LayoutParams(-1, dp(52)))

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = columns
        }

        scroll.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun populate(query: String) {
            grid.removeAllViews()

            apps.filter {
                it.loadLabel(packageManager).toString()
                    .contains(query, true)
            }.forEach {
                grid.addView(
                    appTile(it),
                    GridLayout.LayoutParams().apply {
                        width = resources.displayMetrics.widthPixels / columns
                        height = dp(100)
                    }
                )
            }
        }

        populate("")

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int,
                count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int,
                before: Int, count: Int
            ) {
                populate(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        content.addView(button("← Home") { showHome() })
    }

    // CONTROL CENTER

    private fun showControlCenter() {
        setup("Control Center")

        val edit = row()

        edit.addView(
            button("✎  Edit tiles") { showSettings() },
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        edit.addView(
            button("⚙") {
                openSystem(Settings.ACTION_SETTINGS)
            },
            LinearLayout.LayoutParams(dp(65), dp(52))
        )

        content.addView(edit)

        val first = row()

        first.addView(
            largeTile("◉", "Wi-Fi", "Settings") {
                openSystem(Settings.ACTION_WIFI_SETTINGS)
            },
            LinearLayout.LayoutParams(0, dp(100), 1f)
        )

        first.addView(
            largeTile("ᛒ", "Bluetooth", "Settings") {
                openSystem(Settings.ACTION_BLUETOOTH_SETTINGS)
            },
            LinearLayout.LayoutParams(0, dp(100), 1f)
        )

        content.addView(first)

        val media = column().apply {
            background = glass("#88777777", 30)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        media.addView(label("♫     MUSIC PLAYER", 13f))
        media.addView(label("Not playing", 23f))

        val controls = row()
        listOf("⏮", "▶", "⏭").forEach {
            controls.addView(
                label(it, 27f),
                LinearLayout.LayoutParams(0, dp(55), 1f)
            )
        }

        media.addView(controls)

        val mediaLp = LinearLayout.LayoutParams(-1, dp(155))
        mediaLp.setMargins(0, dp(12), 0, dp(12))
        content.addView(media, mediaLp)

        content.addView(label("Brightness", 15f))

        val brightness = SeekBar(this).apply {
            max = 255
            progress = 150
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        bar: SeekBar?, value: Int, user: Boolean
                    ) {
                        if (user) {
                            val params = window.attributes
                            params.screenBrightness =
                                (value / 255f).coerceIn(0.05f, 1f)
                            window.attributes = params
                        }
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                }
            )
        }
        content.addView(brightness)

        content.addView(label("Volume", 15f))

        val volume = SeekBar(this).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        bar: SeekBar?, value: Int, user: Boolean
                    ) {
                        if (user) {
                            audio.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                value,
                                0
                            )
                        }
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                }
            )
        }

        content.addView(volume)

        val toggles = listOf(
            "✈ Airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            "🔕 Sound" to Settings.ACTION_SOUND_SETTINGS,
            "▣ Display" to Settings.ACTION_DISPLAY_SETTINGS,
            "⌁ Battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "◉ Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "⚙ Settings" to Settings.ACTION_SETTINGS
        )

        val toggleGrid = GridLayout(this).apply {
            columnCount = 3
        }

        toggles.forEach { item ->
            toggleGrid.addView(
                button(item.first) { openSystem(item.second) },
                GridLayout.LayoutParams().apply {
                    width = resources.displayMetrics.widthPixels / 3 - dp(20)
                    height = dp(85)
                    setMargins(dp(3), dp(4), dp(3), dp(4))
                }
            )
        }

        val scroll = ScrollView(this)
        scroll.addView(toggleGrid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(button("← Home") { showHome() })
    }

    private fun largeTile(
        icon: String,
        title: String,
        subtitle: String,
        action: () -> Unit
    ): View {
        return column().apply {
            background = glass("#AA777777", 32)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            addView(label(icon, 25f))
            addView(label(title, 19f))
            addView(label(subtitle, 13f, Color.LTGRAY))
            setOnClickListener { action() }
        }
    }

    // SETTINGS

    private fun showSettings() {
        setup("Fluid settings")

        content.addView(button(
            "Appearance: ${if (darkMode) "Dark" else "Light"}"
        ) {
            darkMode = !darkMode
            showSettings()
        })

        content.addView(button("Grid columns: $columns") {
            columns = if (columns >= 6) 3 else columns + 1
            showSettings()
        })

        content.addView(button(
            "App labels: ${if (labels) "On" else "Off"}"
        ) {
            labels = !labels
            showSettings()
        })

        content.addView(button("Change wallpaper") {
            chooseWallpaper()
        })

        content.addView(button("Control Center") {
            showControlCenter()
        })

        content.addView(button("Lock screen preview") {
            showLockPreview()
        })

        content.addView(button("Android system settings") {
            openSystem(Settings.ACTION_SETTINGS)
        })

        content.addView(button("Back to home") {
            showHome()
        })
    }

    // LOCK SCREEN PREVIEW

    private fun showLockPreview() {
        setup("Lock screen preview")

        val clock = label(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            76f
        )
        clock.gravity = Gravity.CENTER
        content.addView(clock)

        val date = label(
            SimpleDateFormat("EEE, MMMM dd", Locale.getDefault())
                .format(Date()),
            18f
        )
        date.gravity = Gravity.CENTER
        content.addView(date)

        val spacer = View(this)
        content.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        content.addView(
            button("●   Notifications preview") {}
        )

        val bottom = row()

        bottom.addView(
            button("☼ Flashlight") {
                Toast.makeText(
                    this,
                    "Use your phone's system flashlight",
                    Toast.LENGTH_SHORT
                ).show()
            },
            LinearLayout.LayoutParams(0, dp(65), 1f)
        )

        bottom.addView(
            button("📷 Camera") {
                openSystem(Settings.ACTION_SETTINGS)
            },
            LinearLayout.LayoutParams(0, dp(65), 1f)
        )

        content.addView(bottom)
        content.addView(button("← Back") { showSettings() })
    }

    // WALLPAPER

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, 200)
    }

    @Deprecated("Uses legacy activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return

            try {
                contentResolver.openInputStream(uri)?.use {
                    WallpaperManager.getInstance(this).setStream(it)
                }

                Toast.makeText(
                    this,
                    "Wallpaper applied!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Wallpaper could not be applied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openSystem(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun onBackPressed() {
        showHome()
    }
}
