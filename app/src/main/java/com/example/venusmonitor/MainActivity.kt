package com.example.venusmonitor

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var status: TextView
    private lateinit var diag: TextView
    private lateinit var etIp: EditText
    private lateinit var etCap: EditText
    private lateinit var etPct: EditText
    private lateinit var etAlert: EditText
    private lateinit var etPoll: EditText
    private lateinit var etLow: EditText
    private lateinit var cbDevCap: CheckBox
    private lateinit var cbMonitor: CheckBox
    private lateinit var spGrid: Spinner
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            thread { Refresh.run(this@MainActivity); runOnUiThread { render() } }
            h.postDelayed(this, 5000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        status = TextView(this).apply { textSize = 18f }
        root.addView(status)
        diag = TextView(this).apply { textSize = 10f; typeface = Typeface.MONOSPACE; setPadding(0, 16, 0, 0) }
        root.addView(diag)

        root.addView(Button(this).apply {
            text = "Power history"
            setOnClickListener { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) }
        })

        etIp = field(root, "Battery IP (Open API must be enabled in the Marstek app)", p.ip, false)
        root.addView(Button(this).apply {
            text = "Find the battery on the network"
            setOnClickListener {
                thread {
                    val ip = MarstekClient.discover(p.port)
                    runOnUiThread {
                        if (ip != null) etIp.setText(ip) else Toast.makeText(this@MainActivity, "Not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
        cbDevCap = CheckBox(this).apply { text = "Use the capacity reported by the battery"; isChecked = p.useDevCap }
        root.addView(cbDevCap)
        etCap = field(root, "Manual capacity, Wh (used if the box above is off or the battery doesn't report it)", p.cap.toString(), true)
        etPct = field(root, "Usable share of capacity, % (the rest is kept as reserve)", p.pct.toString(), true)
        etAlert = field(root, "Alert when load is above, W (0 = off)", p.alertW.toString(), true)
        etPoll = field(root, "Background check interval, seconds (min 10)", p.pollSec.toString(), true)
        etLow = field(root, "Low charge alert at, % (0 = off)", p.lowPct.toString(), true)
        cbMonitor = CheckBox(this).apply { text = "Background monitoring (alerts without opening the app)"; isChecked = p.monitor }
        root.addView(cbMonitor)

        root.addView(TextView(this).apply { text = "How to detect that power is on"; setPadding(0, 24, 0, 0) })
        spGrid = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("From battery data", "Phone on charger = power is on"))
            setSelection(p.gridMode)
        }
        root.addView(spGrid)

        root.addView(Button(this).apply { text = "Save"; setOnClickListener { save() } })

        setContentView(ScrollView(this).apply { addView(root) })
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        )
        applyService()
        render()
    }

    private fun field(root: LinearLayout, label: String, value: String, number: Boolean): EditText {
        root.addView(TextView(this).apply { text = label; setPadding(0, 24, 0, 0) })
        val e = EditText(this).apply {
            setText(value)
            inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        root.addView(e)
        return e
    }

    private fun applyService() {
        val i = Intent(this, MonitorService::class.java)
        if (p.monitor) startForegroundService(i) else stopService(i)
    }

    private fun save() {
        p.ip = etIp.text.toString().trim()
        p.useDevCap = cbDevCap.isChecked
        p.cap = (etCap.text.toString().toIntOrNull() ?: 5120).coerceAtLeast(100)
        p.pct = (etPct.text.toString().toIntOrNull() ?: 80).coerceIn(1, 100)
        p.alertW = (etAlert.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
        p.pollSec = (etPoll.text.toString().toIntOrNull() ?: 30).coerceAtLeast(10)
        p.monitor = cbMonitor.isChecked
        p.lowPct = (etLow.text.toString().toIntOrNull() ?: 40).coerceIn(0, 100)
        val mode = spGrid.selectedItemPosition
        if (mode != p.gridMode) { p.gridMode = mode; p.lastGrid = -1 }
        applyService()
        render()
        thread { Refresh.run(this); runOnUiThread { render() } }
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    private fun render() {
        if (p.soc < 0) {
            status.text = "No data yet. Enter the battery IP and press Save."
            diag.text = ""
            return
        }
        val age = (System.currentTimeMillis() - p.ts) / 1000
        val time = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date(p.ts))
        status.text = buildString {
            append("🔋 ${p.soc}% · ${Calc.remainingWh(p).roundToInt()} Wh usable (of ${Calc.cap(p)} Wh)\n")
            append(Calc.gridText(p)).append("\n")
            append(Calc.stateLine(p)).append("\n")
            append(Calc.loadLine(p)).append("\n")
            append(Calc.etaLine(p)).append("\n")
            append("\n")
            append("ongrid=${p.onW} offgrid=${p.offW} bat=${p.batW} pv=${p.pvW}\n")
            append(if (age > 120) "⚠ no connection for $age s (last data $time)" else "updated $time")
        }
        diag.text = "Diagnostics:\n" + p.raw
    }
}
