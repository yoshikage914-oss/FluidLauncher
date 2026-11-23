package com.example.fluidlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

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

/** Squash when pressed, spring back when released. */
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
