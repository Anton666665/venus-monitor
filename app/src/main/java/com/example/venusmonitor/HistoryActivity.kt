package com.example.venusmonitor

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class HistoryActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var body: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        root.addView(TextView(this).apply {
            text = "Power history — last 7 days"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Button(this).apply {
            text = "Settings"
            setOnClickListener { startActivity(Intent(this@HistoryActivity, MainActivity::class.java)) }
        })
        bar.addView(Button(this).apply { text = "Refresh"; setOnClickListener { refresh() } })
        root.addView(bar)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        render()
        refresh()
    }

    private fun refresh() {
        thread { Refresh.run(this); runOnUiThread { render() } }
    }

    private fun tv(text: String, size: Float, bold: Boolean = false, top: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, top, 0, 0)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun render() {
        body.removeAllViews()
        val now = System.currentTimeMillis()
        val hm = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val dayFmt = SimpleDateFormat("EEE, MMM d", Locale.ENGLISH)
        body.addView(tv(Calc.gridText(p), 18f, true, 16))
        val days = History.days(p, now)
        if (days.all { it.segs.isEmpty() }) {
            body.addView(tv("No data yet. Changes are recorded while the app is monitoring the battery.", 15f, false, 16))
            return
        }
        for ((i, d) in days.withIndex()) {
            val label = (if (i == 0) "Today · " else if (i == 1) "Yesterday · " else "") + dayFmt.format(Date(d.start))
            body.addView(tv(label, 18f, true, 28))
            if (d.segs.isEmpty()) {
                body.addView(tv("no data", 14f))
                continue
            }
            body.addView(tv("💡 on ${Calc.fmtMs(d.onMs)}  ·  🔌 off ${Calc.fmtMs(d.offMs)}", 14f))
            for (s in d.segs) {
                val icon = if (s.state == 1) "💡 Power on " else "🔌 No power"
                val end = if (s.end >= now - 1000) "now" else hm.format(Date(s.end))
                body.addView(tv("$icon  ${hm.format(Date(s.start))} – $end  (${Calc.fmtMs(s.end - s.start)})", 15f, false, 6))
            }
        }
    }
}
