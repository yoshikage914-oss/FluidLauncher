
package com.example.fluidlauncher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val apps = mutableListOf<Pair<String, android.content.pm.ResolveInfo>>()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var dark = true
    private var columns = 4
    private var showLabels = true

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun background(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun text(value: String, size: Float, color: Int = Color.WHITE): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun button(label: String, action: () -> Unit): TextView {
        return text(label, 15f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = background(Color.parseColor("#292D36"), 20)
            setOnClickListener { action() }
        }
    }

    private fun base(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(20, 22, 29)
        window.navigationBarColor = Color.rgb(20, 22, 29)
        loadApps()
        showHome()
    }

    private fun loadApps() {
        apps.clear()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = packageManager.queryIntentActivities(intent, 0)
        list.filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .forEach { apps.add(it.loadLabel(packageManager).toString() to it) }
    }

    private fun setup(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (dark) Color.rgb(20, 22, 29) else Color.rgb(241, 243, 248))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        header.addView(text(title, 25f), LinearLayout.LayoutParams(0, dp(65), 1f))
        header.addView(button("⚙") { showSettings() })
        root.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun showHome() {
        setup("FluidLauncher")

        val clock = TextView(this).apply {
            textSize = 54f
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(4))
        }
        clock.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        content.addView(clock)

        val date = TextView(this).apply {
            text = java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale.getDefault()).format(java.util.Date())
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        content.addView(date)

        val search = button("⌕     Search apps") { showDrawer() }
        val searchLp = LinearLayout.LayoutParams(-1, dp(52))
        searchLp.setMargins(0, dp(28), 0, dp(18))
        content.addView(search, searchLp)

        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Apps" to { showDrawer() }, "Control" to { showControlCenter() }, "Wallpaper" to { chooseWallpaper() }).forEach { item ->
            shortcuts.addView(button(item.first, item.second),
                LinearLayout.LayoutParams(0, dp(70), 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                })
        }
        content.addView(shortcuts)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val grid = GridLayout(this).apply { columnCount = columns }

        apps.take(12).forEach { pair ->
            grid.addView(appTile(pair.first, pair.second),
                ViewGroup.LayoutParams(0, dp(105)).apply {})
        }

        scroll.addView(list)
        list.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = button("   Home          Apps          Settings   ") { showDrawer() }
        content.addView(bottom)
    }

    private fun appTile(label: String, info: android.content.pm.ResolveInfo): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        val icon = ImageView(this).apply {
            setImageDrawable(info.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        cell.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

        if (showLabels) {
            val name = text(label, 11f)
            name.gravity = Gravity.CENTER
            name.maxLines = 1
            cell.addView(name)
        }

        cell.setOnClickListener {
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(info.activityInfo.packageName, info.activityInfo.name)
            startActivity(launch)
        }
        return cell
    }

    private fun showDrawer() {
        setup("All applications")

        val search = EditText(this).apply {
            hint = "Search applications"
            textSize = 16f
            setSingleLine(true)
            setPadding(dp(16), 0, dp(16), 0)
            background = background(Color.parseColor("#292D36"), 22)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(52)))

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = columns }
        scroll.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun populate(query: String) {
            grid.removeAllViews()
            apps.filter { it.first.contains(query, true) }.forEach {
                grid.addView(appTile(it.first, it.second),
                    GridLayout.LayoutParams().apply {
                        width = resources.displayMetrics.widthPixels / columns
                        height = dp(105)
                    })
            }
        }

        populate("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populate(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        content.addView(button("← Back to home") { showHome() })
    }

    private fun showSettings() {
        setup("Launcher settings")

        content.addView(button("Appearance: ${if (dark) "Dark" else "Light"}") {
            dark = !dark
            showSettings()
        })

        content.addView(button("Grid columns: $columns") {
            columns = if (columns >= 6) 3 else columns + 1
            showSettings()
        })

        content.addView(button("App labels: ${if (showLabels) "On" else "Off"}") {
            showLabels = !showLabels
            showSettings()
        })

        content.addView(button("Change wallpaper") { chooseWallpaper() })
        content.addView(button("Control Center") { showControlCenter() })
        content.addView(button("Android system settings") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })
        content.addView(button("Back to home") { showHome() })
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        startActivityForResult(intent, 101)
    }

    @Deprecated("Deprecated Android callback retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri)?.use {
                    WallpaperManager.getInstance(this).setStream(it)
                }
                Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not apply wallpaper", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showControlCenter() {
        setup("Control Center")

        content.addView(button("Wi-Fi settings") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        })
        content.addView(button("Bluetooth settings") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        })
        content.addView(button("Display settings") {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        })
        content.addView(button("Sound settings") {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        })
        content.addView(button("Battery settings") {
            startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        })
        content.addView(button("System settings") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })
        content.addView(button("Back home") { showHome() })
    }

    override fun onBackPressed() {
        showHome()
    }
}
