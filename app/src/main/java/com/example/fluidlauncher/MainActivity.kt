package com.example.fluidlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

// ── ColorOS 17 palette ─────────────────────────────────────────────
val COS_TEXT       = Color.parseColor("#F21A1A22") // near-black text
val COS_TEXT_SEC   = Color.parseColor("#B35A5A66") // secondary text
val COS_PANEL      = Color.parseColor("#D9FFFFFF") // frosted panel
val COS_CARD       = Color.parseColor("#33FFFFFF") // card fill (on light)
val COS_ACCENT     = Color.parseColor("#FF2BB6A3") // ColorOS teal-green

class MainActivity : Activity() {

    private var allApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var dockPkgs: Set<String> = emptySet()
    private var drawerOpen = false
    private var folderOpen = false
    private var currentPage = 0
    private var pageCount = 1
    private var startScroll = 0

    private lateinit var root: FrameLayout
    private lateinit var homeLayer: LinearLayout
    private lateinit var pager: HorizontalScrollView
    private lateinit var pagesBox: LinearLayout
    private lateinit var pill: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var grid: GridView
    private lateinit var search: EditText
    private lateinit var folderOverlay: FrameLayout
    private lateinit var folderPanel: LinearLayout
    private lateinit var detector: GestureDetector
    private lateinit var cc: ControlCenter
    private lateinit var widgets: WidgetArea
    private lateinit var homeGrid: HomeGrid

    private val gridAdapter = object : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val cell = (convertView as? LinearLayout) ?: makeCell()
            bindCell(cell, shown[position])
            return cell
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── ColorOS 17: light system bars + cross-window blur ──
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.attributes.blurBehindRadius = 40
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            } catch (e: Throwable) {}
        }

        widgets = WidgetArea(this)
        cc = ControlCenter(this)
        homeGrid = HomeGrid(
            this,
            widgets,
            { app, v -> launch(app, v) },
            { app -> openInfo(app) },
            { f -> openFolder(f) }
        )

        root = FrameLayout(this)

        homeLayer = LinearLayout(this)
        homeLayer.orientation = LinearLayout.VERTICAL
        homeLayer.setPadding(0, dp(8), 0, dp(20))

        pager = HorizontalScrollView(this)
        pager.isHorizontalScrollBarEnabled = false
        pager.overScrollMode = View.OVER_SCROLL_NEVER
        pagesBox = LinearLayout(this)
        pagesBox.orientation = LinearLayout.HORIZONTAL
        pager.addView(pagesBox, FrameLayout.LayoutParams(WRAP, MATCH))
        homeLayer.addView(pager, LinearLayout.LayoutParams(MATCH, 0, 1f))

        pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER
        pill.minimumWidth = dp(120)
        // frosted white pill, dark text
        pill.background = glass(Color.parseColor("#99FFFFFF"), 20)
        pill.bounceOnPress()
        pill.setOnClickListener { if (currentPage == 0) openSearch() }
        val pillLp = LinearLayout.LayoutParams(WRAP, dp(40))
        pillLp.gravity = Gravity.CENTER_HORIZONTAL
        pillLp.bottomMargin = dp(14)
        homeLayer.addView(pill, pillLp)

        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.setPadding(dp(14), dp(12), dp(14), dp(12))
        // frosted white dock pill
        dock.background = glass(Color.parseColor("#B3FFFFFF"), 40)
        val dockLp = LinearLayout.LayoutParams(WRAP, WRAP)
        dockLp.gravity = Gravity.CENTER_HORIZONTAL
        homeLayer.addView(dock, dockLp)

        drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.VERTICAL
        val r = dp(36).toFloat()
        val bg = GradientDrawable()
        // light frosted drawer sheet
        bg.setColor(COS_PANEL)
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        drawer.background = bg
        drawer.setPadding(0, dp(40), 0, 0)
        drawer.visibility = View.INVISIBLE

        search = EditText(this)
        search.hint = "Search apps"
        search.setSingleLine(true)
        // dark text on light sheet
        search.setTextColor(COS_TEXT)
        search.setHintTextColor(COS_TEXT_SEC)
        search.textSize = 16f
        search.setPadding(dp(22), 0, dp(22), 0)
        search.background = glass(COS_CARD, 28)
        val searchLp = LinearLayout.LayoutParams(MATCH, dp(52))
        searchLp.setMargins(dp(20), dp(12), dp(20), dp(8))
        drawer.addView(search, searchLp)

        grid = GridView(this)
        grid.numColumns = 4
        grid.selector = ColorDrawable(Color.TRANSPARENT)
        grid.setPadding(dp(8), 0, dp(8), dp(48))
        grid.clipToPadding = false
        grid.adapter = gridAdapter
        grid.setOnItemClickListener { _, v, pos, _ -> launch(shown[pos], v) }
        grid.setOnItemLongClickListener { _, _, pos, _ -> openInfo(shown[pos]); true }
        drawer.addView(grid, LinearLayout.LayoutParams(MATCH, MATCH))

        folderOverlay = FrameLayout(this)
        folderOverlay.setBackgroundColor(Color.parseColor("#59FFFFFF")) // light scrim
        folderOverlay.visibility = View.GONE
        folderOverlay.setOnClickListener { closeFolder() }
        folderPanel = LinearLayout(this)
        folderPanel.orientation = LinearLayout.VERTICAL
        folderPanel.setPadding(dp(16), dp(16), dp(16), dp(16))
        folderPanel.background = glass(Color.parseColor("#F2FFFFFF"), 32)
        folderPanel.setOnClickListener { }
        val panelLp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER)
        panelLp.setMargins(dp(20), 0, dp(20), 0)
        folderOverlay.addView(folderPanel, panelLp)

        root.addView(homeLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(folderOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(cc.view, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                shown = if (q.isEmpty()) allApps
                else allApps.filter { it.label.contains(q, ignoreCase = true) }
                gridAdapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (folderOpen) return false
                if (abs(vy) < 900f || abs(vy) < abs(vx) * 1.5f) return false
                if (vy < 0f) {
                    if (cc.isOpen) cc.close()
                    else if (!drawerOpen) setDrawer(true)
                } else {
                    if (drawerOpen) {
                        if (!grid.canScrollVertically(-1)) setDrawer(false)
                    } else if (!cc.isOpen) {
                        cc.open()
                    }
                }
                return false
            }
        })

        drawer.post { drawer.translationY = drawer.height.toFloat() }
        loadApps()
    }

    override fun onStart() {
        super.onStart()
        widgets.start()
    }

    override fun onStop() {
        super.onStop()
        widgets.stop()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        cc.close()
        if (folderOpen) closeFolder()
        if (drawerOpen) setDrawer(false)
        currentPage = 0
        pager.post { pager.smoothScrollTo(0, 0) }
        updatePill()
    }

    override fun onBackPressed() {
        if (cc.isOpen) cc.close()
        else if (folderOpen) closeFolder()
        else if (drawerOpen) setDrawer(false)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onResult(requestCode, resultCode)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) startScroll = pager.scrollX
        val handled = super.dispatchTouchEvent(ev)
        val a = ev.actionMasked
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) &&
            !drawerOpen && !folderOpen && !cc.isOpen
        ) {
            snapPage()
        }
        return handled
    }

    private fun snapPage() {
        val w = resources.displayMetrics.widthPixels
        val startPage = Math.round(startScroll.toFloat() / w)
        val delta = pager.scrollX - startScroll
        var target = startPage
        if (delta > dp(40)) target = startPage + 1
        else if (delta < -dp(40)) target = startPage - 1
        target = target.coerceIn(0, pageCount - 1)
        pager.post { pager.smoothScrollTo(target * w, 0) }
        if (target != currentPage) {
            currentPage = target
            updatePill()
        }
    }

    private fun updatePill() {
        pill.removeAllViews()
        if (currentPage == 0) {
            val t = TextView(this)
            t.text = "\uD83D\uDD0D  Search"
            t.setTextColor(COS_TEXT)
            t.textSize = 15f
            t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            pill.setPadding(dp(22), 0, dp(22), 0)
            pill.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
        } else {
            pill.setPadding(dp(16), 0, dp(16), 0)
            for (i in 0 until pageCount) {
                val dot = View(this)
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(if (i == currentPage) COS_TEXT else Color.parseColor("#665A5A66"))
                dot.background = g
                val lp = LinearLayout.LayoutParams(dp(7), dp(7))
                lp.setMargins(dp(5), 0, dp(5), 0)
                pill.addView(dot, lp)
            }
        }
    }

    private fun loadApps() {
        allApps = loadAllApps(this)
        shown = allApps
        gridAdapter.notifyDataSetChanged()

        val dockList = dockApps(this, allApps)
        dockPkgs = dockList.map { it.pkg }.toSet()
        dock.removeAllViews()
        for (app in dockList) {
            val iv = makeTile(app)
            iv.setOnClickListener { launch(app, iv) }
            val lp = LinearLayout.LayoutParams(dp(62), dp(62))
            lp.setMargins(dp(10), 0, dp(10), 0)
            dock.addView(iv, lp)
        }

        val pool = allApps.filter { !dockPkgs.contains(it.pkg) }
        pageCount = homeGrid.build(pagesBox, makeSlots(pool))
        if (currentPage > pageCount - 1) currentPage = pageCount - 1
        val w = resources.displayMetrics.widthPixels
        pager.post { pager.scrollTo(currentPage * w, 0) }
        updatePill()
    }

    private fun openFolder(s: FolderSlot) {
        folderPanel.removeAllViews()
        val title = TextView(this)
        title.text = s.name
        title.setTextColor(COS_TEXT)
        title.textSize = 18f
        title.gravity = Gravity.CENTER
        folderPanel.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
        var i = 0
        while (i < s.apps.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 4) {
                val app = s.apps.getOrNull(i + c)
                val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
                if (app != null) {
                    val cell = makeCell()
                    bindCell(cell, app)
                    cell.bounceOnPress()
                    cell.setOnClickListener { launch(app, cell) }
                    cell.setOnLongClickListener { openInfo(app); true }
                    row.addView(cell, lp)
                } else {
                    row.addView(View(this), lp)
                }
            }
            folderPanel.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
            i += 4
        }
        folderOpen = true
        folderOverlay.alpha = 0f
        folderOverlay.visibility = View.VISIBLE
        folderOverlay.animate().alpha(1f).setDuration(180).start()
        folderPanel.scaleX = 0.8f
        folderPanel.scaleY = 0.8f
        folderPanel.animate().scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(OvershootInterpolator(1.4f)).start()
    }

    private fun closeFolder() {
        folderOpen = false
        folderOverlay.animate().alpha(0f).setDuration(150)
            .withEndAction { if (!folderOpen) folderOverlay.visibility = View.GONE }
            .start()
    }

    private fun openSearch() {
        setDrawer(true)
        search.postDelayed({
            search.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(search, 0)
        }, 350)
    }

    private fun setDrawer(open: Boolean) {
        if (open == drawerOpen) return
        drawerOpen = open
        val h = drawer.height.toFloat()
        if (open) drawer.visibility = View.VISIBLE
        drawer.animate()
            .translationY(if (open) 0f else h)
            .setDuration(340)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction { if (!open) drawer.visibility = View.INVISIBLE }
            .start()
        homeLayer.animate()
            .alpha(if (open) 0f else 1f)
            .scaleX(if (open) 0.92f else 1f)
            .scaleY(if (open) 0.92f else 1f)
            .setDuration(340)
            .start()
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.setBackgroundBlurRadius(if (open) 90 else 0)
            } catch (e: Throwable) {
            }
        }
        if (!open) {
            search.setText("")
            search.clearFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(search.windowToken, 0)
            grid.setSelection(0)
        }
    }

    private fun launch(app: AppEntry, from: View?) {
        try {
            if (from != null) {
                val opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height)
                startActivity(app.intent, opts.toBundle())
            } else {
                startActivity(app.intent)
            }
        } catch (e: Exception) {
        }
        if (folderOpen) closeFolder()
        if (drawerOpen) setDrawer(false)
    }

    private fun openInfo(app: AppEntry) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppEntry(val label: String, val pkg: String, val intent: Intent, val icon: Drawable)

sealed class Slot {
    abstract val span: Int
}

class AppSlot(val app: AppEntry) : Slot() {
    override val span: Int = 1
}

class FolderSlot(val name: String, val apps: List<AppEntry>) : Slot() {
    override val span: Int = 2
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.glass(color: Int, radiusDp: Int): GradientDrawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = dp(radiusDp).toFloat()
    // subtle border that works on both light & frosted surfaces
    d.setStroke(dp(1), Color.parseColor("#1A1A1A22"))
    return d
}

fun squircle(v: View) {
    v.clipToOutline = true
    v.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.30f)
        }
    }
}

fun View.bounceOnPress() {
    setOnTouchListener { v, ev ->
        val a = ev.actionMasked
        if (a == MotionEvent.ACTION_DOWN) {
            v.animate().scaleX(0.88f).scaleY(0.88f).setStartDelay(0).setDuration(110)
                .setInterpolator(DecelerateInterpolator()).start()
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(380)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
        false
    }
}

// Soft squircle card tinted from the icon itself (ColorOS 17 style)
fun squircleCard(color: Int, radiusPx: Int): GradientDrawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = radiusPx.toFloat()
    return d
}

fun iconTint(icon: Drawable): Int {
    val bmp = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    icon.setBounds(0, 0, 24, 24)
    icon.draw(canvas)
