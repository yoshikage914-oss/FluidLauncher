package com.example.fluidlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.math.abs

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

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun glass(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setStroke(dp(1), Color.parseColor("#26FFFFFF"))
        return d
    }

    private fun squircle(iv: ImageView) {
        iv.clipToOutline = true
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.30f)
            }
        }
    }

    private fun makeTile(app: AppEntry): ImageView {
        val iv = ImageView(this)
        iv.setImageDrawable(app.icon)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        squircle(iv)
        return iv
    }

    private fun makeCell(): LinearLayout {
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

    private fun bindCell(cell: LinearLayout, app: AppEntry) {
        (cell.getChildAt(0) as ImageView).setImageDrawable(app.icon)
        (cell.getChildAt(1) as TextView).text = app.label
    }

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
        grid.setOnItemClickListener { _, _, pos, _ -> launch(shown[pos]) }
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
                if (vy < 0f && !drawerOpen) {
                    setDrawer(true)
                } else if (vy > 0f && drawerOpen && !grid.canScrollVertically(-1)) {
                    setDrawer(false)
                }
                return false
            }
        })

        drawer.post { drawer.translationY = drawer.height.toFloat() }
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (folderOpen) closeFolder()
        if (drawerOpen) setDrawer(false)
        currentPage = 0
        pager.post { pager.smoothScrollTo(0, 0) }
        updatePill()
    }

    override fun onBackPressed() {
        if (folderOpen) closeFolder()
        else if (drawerOpen) setDrawer(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) startScroll = pager.scrollX
        val handled = super.dispatchTouchEvent(ev)
        val a = ev.actionMasked
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !drawerOpen && !folderOpen) {
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
        val main = Intent(Intent.ACTION_MAIN)
        main.addCategory(Intent.CATEGORY_LAUNCHER)
        val list = packageManager.queryIntentActivities(main, 0)
        val result = ArrayList<AppEntry>()
        for (ri in list) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName) continue
            val launchIntent = Intent(Intent.ACTION_MAIN)
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            launchIntent.setClassName(pkg, ri.activityInfo.name)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            result.add(AppEntry(ri.loadLabel(packageManager).toString(), pkg, launchIntent, ri.loadIcon(packageManager)))
        }
        result.sortBy { it.label.lowercase() }
        allApps = result
        shown = result
        gridAdapter.notifyDataSetChanged()
        dockPkgs = buildDock()
        rebuildHome()
        updatePill()
    }

    private fun buildDock(): Set<String> {
        dock.removeAllViews()
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        val used = HashSet<String>()
        for (p in probes) {
            val pkg = packageManager.resolveActivity(p, 0)?.activityInfo?.packageName ?: continue
            if (!used.add(pkg)) continue
            val app = allApps.firstOrNull { it.pkg == pkg } ?: continue
            val iv = makeTile(app)
            iv.setOnClickListener { launch(app) }
            val lp = LinearLayout.LayoutParams(dp(62), dp(62))
            lp.setMargins(dp(10), 0, dp(10), 0)
            dock.addView(iv, lp)
        }
        return used
    }

    private fun makeSlots(): List<Slot> {
        val pool = allApps.filter { !dockPkgs.contains(it.pkg) }
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

    private fun rebuildHome() {
        pagesBox.removeAllViews()
        val slots = makeSlots()
        val w = resources.displayMetrics.widthPixels
        val side = dp(14)
        val cellW = (w - side * 2) / 4
        val tile = (cellW * 0.78f).toInt()
        val rowH = tile + dp(22)

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
        pageCount = pages.size
        if (currentPage > pageCount - 1) currentPage = pageCount - 1

        for ((idx, pageRows) in pages.withIndex()) {
            val page = LinearLayout(this)
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(side, dp(8), side, 0)

            if (idx == 0) {
                val card = FrameLayout(this)
                card.background = glass(Color.parseColor("#59000000"), 28)
                val inner = LinearLayout(this)
                inner.orientation = LinearLayout.VERTICAL
                inner.gravity = Gravity.CENTER
                val clock = TextClock(this)
                clock.format12Hour = "h:mm"
                clock.format24Hour = "HH:mm"
                clock.textSize = 54f
                clock.setTextColor(Color.WHITE)
                clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                inner.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
                val date = TextClock(this)
                date.format12Hour = "EEEE, MMM d"
                date.format24Hour = "EEEE, MMM d"
                date.textSize = 15f
                date.setTextColor(Color.parseColor("#E6FFFFFF"))
                inner.addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
                card.addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
                val cardLp = LinearLayout.LayoutParams(MATCH, dp(130))
                cardLp.bottomMargin = dp(12)
                page.addView(card, cardLp)
            }

            for (r in pageRows) {
                val rowView = LinearLayout(this)
                rowView.orientation = LinearLayout.HORIZONTAL
                for (s in r) {
                    val cell = FrameLayout(this)
                    when (s) {
                        is AppSlot -> {
                            val iv = makeTile(s.app)
                            iv.setOnClickListener { launch(s.app) }
                            iv.setOnLongClickListener { openInfo(s.app); true }
                            cell.addView(iv, FrameLayout.LayoutParams(tile, tile, Gravity.CENTER))
                        }
                        is FolderSlot -> {
                            val fw = cellW * 2 - dp(8)
                            cell.addView(makeFolderView(s, tile), FrameLayout.LayoutParams(fw, tile, Gravity.CENTER))
                        }
                    }
                    rowView.addView(cell, LinearLayout.LayoutParams(0, rowH, s.span.toFloat()))
                }
                page.addView(rowView, LinearLayout.LayoutParams(MATCH, rowH))
            }
            pagesBox.addView(page, LinearLayout.LayoutParams(w, MATCH))
        }
        pager.post { pager.scrollTo(currentPage * w, 0) }
    }

    private fun makeFolderView(s: FolderSlot, h: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER
        box.background = glass(Color.parseColor("#40FFFFFF"), 28)
        box.setOnClickListener { openFolder(s) }

        val big = (h * 0.60f).toInt()
        for (a in s.apps.take(2)) {
            val lp = LinearLayout.LayoutParams(big, big)
            lp.setMargins(dp(5), 0, dp(5), 0)
            box.addView(makeTile(a), lp)
        }
        val mini = (h * 0.27f).toInt()
        val cluster = LinearLayout(this)
        cluster.orientation = LinearLayout.VERTICAL
        val rest = s.apps.drop(2).take(4)
        for (ri in 0..1) {
            val rr = LinearLayout(this)
            rr.orientation = LinearLayout.HORIZONTAL
            for (ci in 0..1) {
                val a = rest.getOrNull(ri * 2 + ci)
                val lp = LinearLayout.LayoutParams(mini, mini)
                lp.setMargins(dp(2), dp(2), dp(2), dp(2))
              
