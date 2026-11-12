package com.example.venusmonitor

import android.app.Activity
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
    private lateinit var etTaper: EditText
    private lateinit var spGrid: Spinner
    private lateinit var spSign: Spinner
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
        diag = TextView(this).apply { textSize = 10f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0, 16, 0, 0) }
        root.addView(diag)

        etIp = field(root, "IP батареї (Open API має бути увімкнений у додатку Marstek)", p.ip, false)
        root.addView(Button(this).apply {
            text = "Знайти батарею в мережі"
            setOnClickListener {
                thread {
                    val ip = MarstekClient.discover(p.port)
                    runOnUiThread {
                        if (ip != null) etIp.setText(ip) else Toast.makeText(this@MainActivity, "Не знайдено", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
        etCap = field(root, "Повна ємність, Вт·год", p.cap.toString(), true)
        etPct = field(root, "Скільки % ємності можна витратити (решта — запас)", p.pct.toString(), true)
        etTaper = field(root, "Потужність зарядки вище 90%, Вт", p.taperW.toString(), true)

        root.addView(TextView(this).apply { text = "Як визначати наявність світла"; setPadding(0, 24, 0, 0) })
        spGrid = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("За даними батареї", "Телефон на зарядці = світло є"))
            setSelection(p.gridMode)
        }
        root.addView(spGrid)

        root.addView(TextView(this).apply { text = "Знак потужності батареї (якщо заряд/розряд показує навпаки)"; setPadding(0, 24, 0, 0) })
        spSign = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Авто", "Плюс = заряд", "Плюс = розряд"))
            setSelection(p.signMode)
        }
        root.addView(spSign)

        root.addView(Button(this).apply { text = "Зберегти"; setOnClickListener { save() } })

        setContentView(ScrollView(this).apply { addView(root) })
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        )
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

    private fun save() {
        p.ip = etIp.text.toString().trim()
        p.cap = (etCap.text.toString().toIntOrNull() ?: 5120).coerceAtLeast(100)
        p.pct = (etPct.text.toString().toIntOrNull() ?: 80).coerceIn(1, 100)
        p.taperW = (etTaper.text.toString().toIntOrNull() ?: 1070).coerceAtLeast(100)
        p.gridMode = spGrid.selectedItemPosition
        p.signMode = spSign.selectedItemPosition
        p.dischargeSign = 0
        p.lastGrid = -1
        render()
        thread { Refresh.run(this); runOnUiThread { render() } }
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    private fun render() {
        if (p.soc < 0) {
            status.text = "Даних ще немає. Вкажіть IP батареї та натисніть «Зберегти»."
            return
        }
        val age = (System.currentTimeMillis() - p.ts) / 1000
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(p.ts))
        status.text = buildString {
            append("🔋 ${p.soc}% · можна витратити ${Calc.remainingWh(p).roundToInt()} Вт·год (з ${p.cap * p.pct / 100})\n")
            append(Calc.gridText(p)).append("\n")
            append(Calc.stateLine(p)).append("\n")
            append(Calc.loadLine(p)).append("\n")
            append(Calc.etaLine(p)).append("\n\n")
            append("ongrid=${p.onW} offgrid=${p.offW} bat=${p.batW} pv=${p.pvW}\n")
            append(if (age > 120) "⚠ немає зв'язку вже $age с (останні дані $time)" else "оновлено $time")
        }
        diag.text = "Діагностика:\n" + p.raw
    }
}
