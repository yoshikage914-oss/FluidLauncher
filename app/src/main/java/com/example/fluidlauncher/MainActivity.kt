package com.fluidlauncher.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

data class OneUiApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var home: View
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var search: EditText
    private lateinit var grid: RecyclerView

    private val allApps = mutableListOf<OneUiApp>()
    private lateinit var appAdapter: OneUiAppAdapter
    private var isDrawerOpen = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        dock = findViewById(R.id.dock)
        drawer = findViewById(R.id.drawer)
        search = findViewById(R.id.search)
        grid = findViewById(R.id.grid)

        setupInsets()
        setupBackHandler()
        setupDrawerGrid()
        setupSearch()
        setupDock()
        setupGestures()
        refreshApps()

        drawer.post {
            if (!isDrawerOpen) {
                drawer.translationY = drawer.height.toFloat().coerceAtLeast(1200f)
                drawer.visibility = View.INVISIBLE
            }
        }
    }

    private fun setupInsets() {
        val baseHomeTop = home.paddingTop
        val baseHomeBottom = home.paddingBottom
        val baseDrawerTop = drawer.paddingTop
        val baseDrawerBottom = drawer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            home.updatePadding(top = baseHomeTop + bars.top, bottom = baseHomeBottom + bars.bottom)
            drawer.updatePadding(top = baseDrawerTop + bars.top, bottom = baseDrawerBottom + bars.bottom)
            insets
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isDrawerOpen && search.text.isNotEmpty() -> search.text.clear()
                    isDrawerOpen -> setDrawerOpen(false)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun refreshApps() {
        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val apps = launcherApps.getActivityList(null, Process.myUserHandle())
            .asSequence()
            .filter { it.componentName.packageName != packageName }
            .map { info ->
                OneUiApp(
                    label = info.label?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: info.componentName.packageName,
                    packageName = info.componentName.packageName,
                    icon = info.getBadgedIcon(0)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
