package com.example.fluidlauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

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
}
