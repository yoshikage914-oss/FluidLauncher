package com.example.fluidlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class ControlCenter(private val act: Activity) {

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
