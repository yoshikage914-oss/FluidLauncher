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
import android.text.TextWatcher
import android.view.Gravity
import android.view.GestureDetector
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.math.abs

class AppEntry(val label: String, val pkg: String, val intent: Intent, val icon: Drawable)

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private var allApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var drawerOpen = false

    private lateinit var home: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var search: EditText
    private lateinit var detector: GestureDetector

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun glass(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(radiusDp).toFloat()
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
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(6)
        cell.addView(label, lp)
        return cell
    }

    private val gridAdapter = object : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = shown[position]
            val cell = (convertView as? LinearLayout) ?: makeCell()
            (cell.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (cell.getChildAt(1) as TextView).text = app.label
            return cell
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        home = LinearLayout(this)
        home.orientation = LinearLayout.VERTICAL
        home.gravity = Gravity.CENTER_HORIZONTAL
        home.setPadding(dp(20), dp(40), dp(20), dp(24))

        val clock = TextClock(this)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        clock.textSize = 80f
        clock.setTextColor(Color.WHITE)
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val clockLp = LinearLayout.LayoutParams(WRAP, WRAP)
        clockLp.topMargin = dp(48)
        home.addView(clock, clockLp)

        val date = TextClock(this)
        date.format12Hour = "EEEE, MMM d"
        date.format24Hour = "EEEE, MMM d"
        date.textSize = 17f
        date.setTextColor(Color.WHITE)
        home.addView(date, LinearLayout.LayoutParams(WRAP, WRAP))

        home.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))

        val hint = TextView(this)
        hint.text = "Swipe up for apps"
        hint.textSize = 12f
        hint.setTextColor(Color.parseColor("#B3FFFFFF"))
        val hintLp = LinearLayout.LayoutParams(WRAP, WRAP)
        hintLp.bottomMargin = dp(14)
        hintLp.gravity = Gravity.CENTER_HORIZONTAL
        home.addView(hint, hintLp)

        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.setPadding(dp(10), dp(14), dp(10), dp(14))
        dock.background = glass(Color.parseColor("#59FFFFFF"), 34)
        home.addView(dock, LinearLayout.LayoutParams(MATCH, WRAP))

        drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.VERTICAL
        val r = dp(36).toFloat()
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#66FFFFFF"))
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
        search.background = glass(Color.parseColor("#4DFFFFFF"), 28)
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

        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))
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
                if (abs(vy) < 900f || abs(vy) < abs(vx)) return false
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
        if (drawerOpen) setDrawer(false)
    }

    override fun onBackPressed() {
        if (drawerOpen) setDrawer(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
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
        buildDock()
    }

    private fun buildDock() {
        dock.removeAllViews()
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        val used = HashSet<String>()
        for (p in probes) {
            val pkg = packageManager.resolveActivity(p, 0)?.activityInfo?.packageName ?: continue
            if (!used.add(pkg)) continue
            val app = allApps.firstOrNull { it.pkg == pkg } ?: continue
            val iv = ImageView(this)
            iv.setImageDrawable(app.icon)
            squircle(iv)
            iv.setOnClickListener { launch(app) }
            val lp = LinearLayout.LayoutParams(dp(60), dp(60))
            lp.setMargins(dp(12), 0, dp(12), 0)
            dock.addView(iv, lp)
        }
    }

    private fun setDrawer(open: Boolean) {
        if (open == drawerOpen) return
        drawerOpen = open
        val h = drawer.height.toFloat()
        if (open) drawer.visibility = View.VISIBLE
        drawer.animate()
            .translationY(if (open) 0f else h)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction { if (!open) drawer.visibility = View.INVISIBLE }
            .start()
        home.animate()
            .alpha(if (open) 0f else 1f)
            .scaleX(if (open) 0.92f else 1f)
            .scaleY(if (open) 0.92f else 1f)
            .setDuration(320)
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

    private fun launch(app: AppEntry) {
        try {
            startActivity(app.intent)
        } catch (e: Exception) {
        }
        if (drawerOpen) setDrawer(false)
    }

    private fun openInfo(app: AppEntry) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
