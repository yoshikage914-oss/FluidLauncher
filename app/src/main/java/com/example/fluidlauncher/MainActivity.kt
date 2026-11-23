package com.fluidlauncher.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.Toast
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
    private var isReceiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshApps()
        }
    }

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

    override fun onResume() {
        super.onResume()
        registerPackageListener()
        if (allApps.isEmpty()) {
            refreshApps()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterPackageListener()
    }

    private fun setupInsets() {
        val baseHomeTop = home.paddingTop
        val baseHomeBottom = home.paddingBottom
        val baseDrawerTop = drawer.paddingTop
        val baseDrawerBottom = drawer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            home.updatePadding(
                top = baseHomeTop + bars.top,
                bottom = baseHomeBottom + bars.bottom
            )
            drawer.updatePadding(
                top = baseDrawerTop + bars.top,
                bottom = baseDrawerBottom + bars.bottom
            )
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

    private fun registerPackageListener() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        isReceiverRegistered = true
    }

    private fun unregisterPackageListener() {
        if (!isReceiverRegistered) return
        runCatching { unregisterReceiver(packageReceiver) }
        isReceiverRegistered = false
    }

    private fun refreshApps() {
        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val selfPkg = packageName
        val apps = launcherApps.getActivityList(null, Process.myUserHandle())
            .asSequence()
            .filter { it.componentName.packageName != selfPkg }
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
            .toList()

        allApps.clear()
        allApps.addAll(apps)
        applySearchFilter(search.text?.toString().orEmpty())
    }

    private fun setupDrawerGrid() {
        grid.layoutManager = GridLayoutManager(this, 4)
        grid.setHasFixedSize(true)
        appAdapter = OneUiAppAdapter(mutableListOf()) { app ->
            launchPackage(app.packageName)
        }
        grid.adapter = appAdapter
    }

    private fun setupSearch() {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun applySearchFilter(rawQuery: String) {
        if (!::appAdapter.isInitialized) return
        val q = rawQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        appAdapter.submitList(filtered)
    }

    private fun setupDock() {
        dock.removeAllViews()
        val dockItems = listOf(
            "Phone" to Intent(Intent.ACTION_DIAL),
            "Messages" to Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
            },
            "Browser" to Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
            "Camera" to Intent("android.media.action.STILL_IMAGE_CAMERA")
        )

        dockItems.forEach { (label, intent) ->
            val btn = TextView(this).apply {
                text = label
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                maxLines = 1
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { view ->
                    animateOneUiTap(view) {
                        startActivitySafely(intent)
                    }
                }
            }
            dock.addView(btn)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (abs(dy) < abs(dx)) return false

                if (!isDrawerOpen && dy < -110f && abs(velocityY) > 350f) {
                    setDrawerOpen(true)
                    return true
                }

                val gridAtTop = !grid.canScrollVertically(-1)
                if (isDrawerOpen && gridAtTop && dy > 140f && abs(velocityY) > 350f) {
                    setDrawerOpen(false)
                    return true
                }
                return false
            }
        })

        home.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }

        drawer.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }

        grid.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun setDrawerOpen(open: Boolean) {
        if (isDrawerOpen == open && drawer.visibility == if (open) View.VISIBLE else View.INVISIBLE) {
            return
        }
        isDrawerOpen = open
        val curve = DecelerateInterpolator(1.8f)
        val targetClosedY = drawer.height.toFloat().coerceAtLeast(root.height.toFloat())

        if (open) {
            drawer.visibility = View.VISIBLE
            if (drawer.translationY == 0f) {
                drawer.translationY = targetClosedY
            }
            drawer.animate()
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(curve)
                .withEndAction(null)
                .start()

            home.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .alpha(0.55f)
                .setDuration(240)
                .setInterpolator(curve)
                .start()
        } else {
            hideKeyboard()
            search.clearFocus()
            search.text.clear()

            drawer.animate()
                .translationY(targetClosedY)
                .setDuration(230)
                .setInterpolator(curve)
                .withEndAction {
                    if (!isDrawerOpen) {
                        drawer.visibility = View.INVISIBLE
                    }
                }
                .start()

            home.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(curve)
                .start()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun launchPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, "Unable to open app", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivitySafely(intent)
        if (isDrawerOpen) {
            setDrawerOpen(false)
        }
    }

    private fun startActivitySafely(intent: Intent) {
        val prepared = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val canHandle = prepared.resolveActivity(packageManager) != null
        if (!canHandle) {
            Toast.makeText(this, "No app available for this action", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(prepared)
        }.onFailure {
            Toast.makeText(this, "Couldn't launch action", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateOneUiTap(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.91f)
            .scaleY(0.91f)
            .setDuration(75)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140)
                    .setInterpolator(DecelerateInterpolator(1.6f))
                    .withEndAction(onComplete)
                    .start()
            }
            .start()
    }

    private class OneUiAppAdapter(
        private val items: MutableList<OneUiApp>,
        private val onAppClick: (OneUiApp) -> Unit
    ) : RecyclerView.Adapter<OneUiAppAdapter.AppViewHolder>() {

        class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(android.R.id.icon)
            val label: TextView = view.findViewById(android.R.id.text1)
        }

        fun submitList(newItems: List<OneUiApp>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val context = parent.context
            val density = context.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val squircleRadius = dp(18).toFloat()
            val iconView = ImageView(context).apply {
                id = android.R.id.icon
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, squircleRadius)
                    }
                }
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            }

            val labelView = TextView(context).apply {
                id = android.R.id.text1
                setTextColor(0xF5FFFFFF.toInt())
                textSize = 11.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), 0)
            }

            container.addView(iconView)
            container.addView(labelView)
            return AppViewHolder(container)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.itemView.setOnClickListener { v ->
                v.animate()
                    .scaleX(0.89f)
                    .scaleY(0.89f)
                    .setDuration(75)
                    .withEndAction {
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(140)
                            .setInterpolator(DecelerateInterpolator(1.6f))
                            .withEndAction { onAppClick(item) }
                            .start()
                    }
                    .start()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
