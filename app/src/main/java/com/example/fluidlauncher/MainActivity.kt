package com.example.fluidlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
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
        pill.background = glass(Color.parseColor("#66000000"), 20)
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
        dock.background = glass(Color.parseColor("#73000000"), 40)
        val dockLp = LinearLayout.LayoutParams(WRAP, WRAP)
        dockLp.gravity = Gravity.CENTER_HORIZONTAL
        homeLayer.addView(dock, dockLp)

        drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.VERTICAL
        val r = dp(36).toFloat()
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#B3101010"))
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        drawer.background = bg
        drawer.setPadding(0, dp(40), 0, 0)
        drawer.visibility = View.INVISIBLE

        search = EditText(this)
        search.hint = "Search apps"
        search.setSingleLine(true)
        search.setTextColor(Color.WHITE)
        search.setHintTextColor(Color.parseColor("#CCFFFFFF"))
        search.textSize = 16f
        search.setPadding(dp(22), 0, dp(22), 0)
        search.background = glass(Color.parseColor("#33FFFFFF"), 28)
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
        folderOverlay.setBackgroundColor(Color.parseColor("#80000000"))
        folderOverlay.visibility = View.GONE
        folderOverlay.setOnClickListener { closeFolder() }
        folderPanel = LinearLayout(this)
        folderPanel.orientation = LinearLayout.VERTICAL
        folderPanel.setPadding(dp(16), dp(16), dp(16), dp(16))
        folderPanel.background = glass(Color.parseColor("#E6202020"), 32)
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
            t.setTextColor(Color.WHITE)
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
                g.setColor(if (i == currentPage) Color.WHITE else Color.parseColor("#66FFFFFF"))
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
        title.setTextColor(Color.WHITE)
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
} const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
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
    d.setStroke(dp(1), Color.parseColor("#26FFFFFF"))
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

fun Context.makeTile(app: AppEntry): ImageView {
    val iv = ImageView(this)
    iv.setImageDrawable(app.icon)
    iv.scaleType = ImageView.ScaleType.FIT_CENTER
    squircle(iv)
    iv.bounceOnPress()
    return iv
}

fun Context.makeCell(): LinearLayout {
    val cell = LinearLayout(this)
    cell.orientation = LinearLayout.VERTICAL
    cell.gravity = Gravity.CENTER_HORIZONTAL
    cell.setPadding(dp(4), dp(10), dp(4), dp(10))
    val icon = ImageView(this)
    squircle(icon)
    cell.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))
    val label = TextView(this)
    label.setTextColor(Color.WHITE)
    label.textSize = 11f
    label.maxLines = 1
    label.gravity = Gravity.CENTER
    label.ellipsize = TextUtils.TruncateAt.END
    val lp = LinearLayout.LayoutParams(MATCH, WRAP)
    lp.topMargin = dp(6)
    cell.addView(label, lp)
    return cell
}

fun bindCell(cell: LinearLayout, app: AppEntry) {
    (cell.getChildAt(0) as ImageView).setImageDrawable(app.icon)
    (cell.getChildAt(1) as TextView).text = app.label
}

fun loadAllApps(act: Activity): List<AppEntry> {
    val pm = act.packageManager
    val main = Intent(Intent.ACTION_MAIN)
    main.addCategory(Intent.CATEGORY_LAUNCHER)
    val result = ArrayList<AppEntry>()
    for (ri in pm.queryIntentActivities(main, 0)) {
        val pkg = ri.activityInfo.packageName
        if (pkg == act.packageName) continue
        val li = Intent(Intent.ACTION_MAIN)
        li.addCategory(Intent.CATEGORY_LAUNCHER)
        li.setClassName(pkg, ri.activityInfo.name)
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        result.add(AppEntry(ri.loadLabel(pm).toString(), pkg, li, ri.loadIcon(pm)))
    }
    result.sortBy { it.label.lowercase() }
    return result
}

fun dockApps(act: Activity, all: List<AppEntry>): List<AppEntry> {
    val probes = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    )
    val used = HashSet<String>()
    val out = ArrayList<AppEntry>()
    for (p in probes) {
        val pkg = act.packageManager.resolveActivity(p, 0)?.activityInfo?.packageName ?: continue
        if (!used.add(pkg)) continue
        val app = all.firstOrNull { it.pkg == pkg } ?: continue
        out.add(app)
    }
    return out
}

fun makeSlots(pool: List<AppEntry>): List<Slot> {
    val keys = listOf(
        "com.discord", "com.snapchat", "com.zhiliaoapp", "com.ss.android.ugc",
        "com.instagram", "com.whatsapp", "com.facebook.katana", "com.twitter", "org.telegram"
    )
    val social = pool.filter { a -> keys.any { a.pkg.startsWith(it) } }
    val slots = ArrayList<Slot>()
    if (social.size >= 2) {
        slots.add(FolderSlot("Social", social))
        for (a in pool) {
            if (!social.contains(a)) slots.add(AppSlot(a))
        }
    } else {
        for (a in pool) slots.add(AppSlot(a))
    }
    return slots
}

class HomeGrid(
    private val act: Activity,
    private val widgets: WidgetArea,
    private val onLaunch: (AppEntry, View) -> Unit,
    private val onInfo: (AppEntry) -> Unit,
    private val onFolder: (FolderSlot) -> Unit
) {

    fun build(pagesBox: LinearLayout, slots: List<Slot>): Int {
        pagesBox.removeAllViews()
        val w = act.resources.displayMetrics.widthPixels
        val side = act.dp(14)
        val cellW = (w - side * 2) / 4
        val tile = (cellW * 0.78f).toInt()
        val rowH = tile + act.dp(22)

        val pages = ArrayList<ArrayList<ArrayList<Slot>>>()
        var rows = ArrayList<ArrayList<Slot>>()
        var row = ArrayList<Slot>()
        var col = 0
        var cap = 3
        for (s in slots) {
            if (col + s.span > 4) {
                rows.add(row)
                row = ArrayList()
                col = 0
                if (rows.size >= cap) {
                    pages.add(rows)
                    rows = ArrayList()
                    cap = 5
                }
            }
            row.add(s)
            col += s.span
            if (col == 4) {
                rows.add(row)
                row = ArrayList()
                col = 0
                if (rows.size >= cap) {
                    pages.add(rows)
                    rows = ArrayList()
                    cap = 5
                }
            }
        }
        if (row.isNotEmpty()) rows.add(row)
        if (rows.isNotEmpty() || pages.isEmpty()) pages.add(rows)

        for ((idx, pageRows) in pages.withIndex()) {
            val page = LinearLayout(act)
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(side, act.dp(8), side, 0)

            if (idx == 0) {
                val cardLp = LinearLayout.LayoutParams(MATCH, act.dp(120))
                cardLp.bottomMargin = act.dp(10)
                page.addView(clockCard(), cardLp)

                (widgets.view.parent as? ViewGroup)?.removeView(widgets.view)
                page.addView(widgets.view, LinearLayout.LayoutParams(MATCH, WRAP))
            }

            for (r in pageRows) {
                val rowView = LinearLayout(act)
                rowView.orientation = LinearLayout.HORIZONTAL
                for (s in r) {
                    val cell = FrameLayout(act)
                    when (s) {
                        is AppSlot -> {
                            val iv = act.makeTile(s.app)
                            iv.setOnClickListener { onLaunch(s.app, iv) }
                            iv.setOnLongClickListener { onInfo(s.app); true }
                            cell.addView(iv, FrameLayout.LayoutParams(tile, tile, Gravity.CENTER))
                        }
                        is FolderSlot -> {
                            val fw = cellW * 2 - act.dp(8)
                            cell.addView(folderView(s, tile), FrameLayout.LayoutParams(fw, tile, Gravity.CENTER))
                        }
                    }
                    rowView.addView(cell, LinearLayout.LayoutParams(0, rowH, s.span.toFloat()))
                }
                page.addView(rowView, LinearLayout.LayoutParams(MATCH, rowH))
            }
            pagesBox.addView(page, LinearLayout.LayoutParams(w, MATCH))
        }
        return pages.size
    }

    private fun clockCard(): View {
        val card = FrameLayout(act)
        card.background = act.glass(Color.parseColor("#59000000"), 28)
        card.bounceOnPress()
        card.setOnLongClickListener { widgets.pick(); true }
        val inner = LinearLayout(act)
        inner.orientation = LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER
        val clock = TextClock(act)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        clock.textSize = 50f
        clock.setTextColor(Color.WHITE)
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        inner.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
        val date = TextClock(act)
        date.format12Hour = "EEEE, MMM d"
        date.format24Hour = "EEEE, MMM d"
        date.textSize = 15f
        date.setTextColor(Color.parseColor("#E6FFFFFF"))
        inner.addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
        card.addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
        return card
    }

    private fun folderView(s: FolderSlot, h: Int): View {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER
        box.background = act.glass(Color.parseColor("#40FFFFFF"), 28)
        box.bounceOnPress()
        box.setOnClickListener { onFolder(s) }

        val big = (h * 0.60f).toInt()
        for (a in s.apps.take(2)) {
            val lp = LinearLayout.LayoutParams(big, big)
            lp.setMargins(act.dp(5), 0, act.dp(5), 0)
            box.addView(act.makeTile(a), lp)
        }
        val mini = (h * 0.27f).toInt()
        val cluster = LinearLayout(act)
        cluster.orientation = LinearLayout.VERTICAL
        val rest = s.apps.drop(2).take(4)
        for (ri in 0..1) {
            val rr = LinearLayout(act)
            rr.orientation = LinearLayout.HORIZONTAL
            for (ci in 0..1) {
                val a = rest.getOrNull(ri * 2 + ci)
                val lp = LinearLayout.LayoutParams(mini, mini)
                lp.setMargins(act.dp(2), act.dp(2), act.dp(2), act.dp(2))
                if (a != null) rr.addView(act.makeTile(a), lp) else rr.addView(View(act), lp)
            }
            cluster.addView(rr, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        val clp = LinearLayout.LayoutParams(WRAP, WRAP)
        clp.setMargins(act.dp(4), 0, act.dp(4), 0)
        box.addView(cluster, clp)
        return box
    }
} class ControlCenter(private val act: Activity) {

    val view = FrameLayout(act)
    var isOpen = false
        private set

    private val panel = LinearLayout(act)
    private val tiles = ArrayList<View>()
    private val audio = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val volSeek = SeekBar(act)
    private var torchOn = false
    private var torchId: String? = null

    init {
        view.visibility = View.GONE
        view.setBackgroundColor(Color.parseColor("#D90A0A0F"))
        view.setOnClickListener { close() }

        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(act.dp(18), act.dp(36), act.dp(18), act.dp(18))
        panel.setOnClickListener { }
        view.addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        val top = row(act.dp(84))
        top.addView(wide("\uD83D\uDCF6", "Wi-Fi", "Tap to open") { openWifi() }, cell(1f))
        top.addView(
            wide("\uD83C\uDFA7", "Bluetooth", "Tap to open") {
                go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            cell(1f)
        )

        val volRow = row(act.dp(64))
        val volBox = LinearLayout(act)
        volBox.orientation = LinearLayout.HORIZONTAL
        volBox.gravity = Gravity.CENTER_VERTICAL
        volBox.setPadding(act.dp(18), 0, act.dp(18), 0)
        volBox.background = act.glass(Color.parseColor("#4DFFFFFF"), 32)
        val volIcon = TextView(act)
        volIcon.text = "\uD83D\uDD0A"
        volIcon.textSize = 20f
        volBox.addView(volIcon, LinearLayout.LayoutParams(WRAP, WRAP))
        volSeek.max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volSeek.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        volSeek.progressTintList = ColorStateList.valueOf(Color.WHITE)
        volSeek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        volSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        volBox.addView(volSeek, LinearLayout.LayoutParams(0, WRAP, 1f))
        tiles.add(volBox)
        volRow.addView(volBox, cell(1f))

        val r1 = row(act.dp(72))
        r1.addView(round("\u2708\uFE0F") { go(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }, cell(1f))
        r1.addView(round("\uD83D\uDD26") { t -> toggleTorch(t) }, cell(1f))
        r1.addView(round("\uD83D\uDCCD") { go(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }, cell(1f))
        r1.addView(round("\uD83C\uDF19") { go(Intent(Settings.ACTION_SOUND_SETTINGS)) }, cell(1f))

        val r2 = row(act.dp(72))
        r2.addView(round("\uD83D\uDD06") { go(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }, cell(1f))
        r2.addView(round("\uD83D\uDD0B") { go(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }, cell(1f))
        r2.addView(round("\uD83D\uDCE1") { go(Intent(Settings.ACTION_NFC_SETTINGS)) }, cell(1f))
        r2.addView(round("\uD83C\uDF10") { go(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }, cell(1f))

        val r3 = row(act.dp(72))
        r3.addView(
            round("\uD83E\uDDEE") {
                go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            },
            cell(1f)
        )
        r3.addView(round("\u23F0") { go(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }, cell(1f))
        r3.addView(round("\u23F1\uFE0F") { go(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }, cell(1f))
        r3.addView(round("\u2699\uFE0F") { go(Intent(Settings.ACTION_SETTINGS)) }, cell(1f))
    }

    private fun row(h: Int): LinearLayout {
        val r = LinearLayout(act)
        r.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(MATCH, h)
        lp.bottomMargin = act.dp(12)
        panel.addView(r, lp)
        return r
    }

    private fun cell(weight: Float): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, MATCH, weight)
        lp.setMargins(act.dp(6), 0, act.dp(6), 0)
        return lp
    }

    private fun round(icon: String, action: (TextView) -> Unit): TextView {
        val t = TextView(act)
        t.text = icon
        t.textSize = 26f
        t.gravity = Gravity.CENTER
        t.background = act.glass(Color.parseColor("#4DFFFFFF"), 36)
        t.bounceOnPress()
        t.setOnClickListener { action(t) }
        tiles.add(t)
        return t
    }

    private fun wide(icon: String, title: String, sub: String, action: () -> Unit): LinearLayout {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(act.dp(14), 0, act.dp(14), 0)
        box.background = act.glass(Color.parseColor("#4DFFFFFF"), 42)

        val ic = TextView(act)
        ic.text = icon
        ic.textSize = 22f
        ic.gravity = Gravity.CENTER
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(Color.parseColor("#33FFFFFF"))
        ic.background = dot
        box.addView(ic, LinearLayout.LayoutParams(act.dp(48), act.dp(48)))

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(act.dp(12), 0, 0, 0)
        val t1 = TextView(act)
        t1.text = title
        t1.setTextColor(Color.WHITE)
        t1.textSize = 16f
        col.addView(t1, LinearLayout.LayoutParams(WRAP, WRAP))
        val t2 = TextView(act)
        t2.text = sub
        t2.setTextColor(Color.parseColor("#B3FFFFFF"))
        t2.textSize = 12f
        col.addView(t2, LinearLayout.LayoutParams(WRAP, WRAP))
        box.addView(col, LinearLayout.LayoutParams(WRAP, WRAP))

        box.bounceOnPress()
        box.setOnClickListener { action() }
        tiles.add(box)
        return box
    }

    private fun go(i: Intent) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(act, "Not available on this phone", Toast.LENGTH_SHORT).show()
        }
        close()
    }

    private fun openWifi() {
        if (Build.VERSION.SDK_INT >= 29) go(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        else go(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun toggleTorch(t: TextView) {
        try {
            val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var id = torchId
            if (id == null) {
                for (cid in cm.cameraIdList) {
                    val flash = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                    if (flash == true) {
                        id = cid
                        break
                    }
                }
            }
            if (id == null) return
            torchId = id
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            t.background = act.glass(Color.parseColor(if (torchOn) "#E6FFFFFF" else "#4DFFFFFF"), 36)
        } catch (e: Exception) {
            Toast.makeText(act, "Flashlight not available", Toast.LENGTH_SHORT).show()
        }
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        volSeek.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(220).start()
        panel.translationY = -act.dp(60).toFloat()
        panel.animate().translationY(0f).setDuration(380)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        for ((i, t) in tiles.withIndex()) {
            t.scaleX = 0.6f
            t.scaleY = 0.6f
            t.alpha = 0f
            t.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setStartDelay(i * 20L).setDuration(340)
                .setInterpolator(OvershootInterpolator(1.6f)).start()
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        panel.animate().translationY(-act.dp(40).toFloat()).setDuration(220).start()
        view.animate().alpha(0f).setDuration(220)
            .withEndAction { if (!isOpen) view.visibility = View.GONE }
            .start()
    }
}

private const val REQ_BIND = 7101
private const val REQ_CONFIG = 7102

class WidgetArea(private val act: Activity) {

    val view = LinearLayout(act)
    private val host = AppWidgetHost(act, 1024)
    private val mgr = AppWidgetManager.getInstance(act)
    private val prefs = act.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val addPill = TextView(act)
    private var pendingId = -1

    init {
        view.orientation = LinearLayout.VERTICAL
        addPill.text = "+  Add widget"
        addPill.setTextColor(Color.WHITE)
        addPill.textSize = 13f
        addPill.gravity = Gravity.CENTER
        addPill.setPadding(act.dp(18), 0, act.dp(18), 0)
        addPill.background = act.glass(Color.parseColor("#59000000"), 18)
        addPill.bounceOnPress()
        addPill.setOnClickListener { pick() }
        val lp = LinearLayout.LayoutParams(WRAP, act.dp(34))
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.bottomMargin = act.dp(10)
        view.addView(addPill, lp)
    }

    fun start() {
        try {
            host.startListening()
        } catch (e: Exception) {
        }
        restore()
    }

    fun stop() {
        try {
            host.stopListening()
        } catch (e: Exception) {
        }
    }

    private fun load(): MutableList<String> {
        val raw = prefs.getString("ids", "") ?: ""
        return raw.split(",").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun save(l: List<String>) {
        prefs.edit().putString("ids", l.joinToString(",")).apply()
    }

    private fun restore() {
        while (view.childCount > 1) view.removeViewAt(0)
        val dead = ArrayList<String>()
        for (s in load()) {
            val id = s.toIntOrNull()
            if (id == null || !attach(id, false)) dead.add(s)
        }
        if (dead.isNotEmpty()) {
            val ids = load()
            ids.removeAll(dead)
            save(ids)
        }
    }

    private fun attach(id: Int, animate: Boolean): Boolean {
        val info = mgr.getAppWidgetInfo(id) ?: return false
        val hv = host.createView(act, id, info)
        hv.setOnLongClickListener {
            confirmRemove(id, hv)
            true
        }
        val h = maxOf(info.minHeight, act.dp(90))
        val lp = LinearLayout.LayoutParams(MATCH, h)
        lp.bottomMargin = act.dp(10)
        view.addView(hv, view.childCount - 1, lp)
        if (animate) {
            hv.scaleX = 0.85f
            hv.scaleY = 0.85f
            hv.alpha = 0f
            hv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350)
                .setInterpolator(OvershootInterpolator(1.5f)).start()
        }
        return true
    }

    private fun confirmRemove(id: Int, hv: View) {
        AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Remove widget?")
            .setPositiveButton("Remove") { _, _ ->
                try {
                    host.deleteAppWidgetId(id)
                } catch (e: Exception) {
                }
                val ids = load()
                ids.remove(id.toString())
                save(ids)
                view.removeView(hv)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun pick() {
        val list = ArrayList<AppWidgetProviderInfo>()
        for (p in mgr.installedProviders) {
            if ((p.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0) list.add(p)
        }
        if (list.isEmpty()) {
            Toast.makeText(act, "No widgets found", Toast.LENGTH_SHORT).show()
            return
        }
        val pm = act.packageManager
        list.sortBy { it.loadLabel(pm).lowercase() }
        val labels = Array(list.size) { list[it].loadLabel(pm) }
        AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Choose a widget")
            .setItems(labels) { _, which -> bind(list[which]) }
            .show()
    }

    private fun bind(p: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        if (mgr.bindAppWidgetIdIfAllowed(id, p.provider)) {
            configureOrAdd(id, p)
        } else {
            pendingId = id
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)
            act.startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureOrAdd(id: Int, p: AppWidgetProviderInfo) {
        if (p.configure != null) {
            pendingId = id
            try {
                host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_CONFIG, null)
            } catch (e: Exception) {
                finish(id)
            }
        } else {
            finish(id)
        }
    }

    private fun finish(id: Int) {
        val ids = load()
        ids.add(id.toString())
        save(ids)
        pendingId = -1
        attach(id, true)
    }

    fun onResult(req: Int, res: Int): Boolean {
        if (req != REQ_BIND && req != REQ_CONFIG) return false
        val id = pendingId
        if (id == -1) return true
        if (res == Activity.RESULT_OK) {
            val info = mgr.getAppWidgetInfo(id)
            if (req == REQ_BIND && info != null && info.configure != null) {
                configureOrAdd(id, info)
            } else {
                finish(id)
            }
        } else {
            try {
                host.deleteAppWidgetId(id)
            } catch (e: Exception) {
            }
            pendingId = -1
        }
        return true
    }
} startActivity(Intent(this, LockScreenActivity::class.java))
