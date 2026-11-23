package com.example.fluidlauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock

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
}
