package com.example.venusmonitor

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Average consumption over the last 5 days, counted only while the battery was discharging. */
object Usage {
    private const val DAYS = 5
    private class Day(val key: String, var wh: Double, var sec: Double)

    private fun key(ts: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    private fun load(p: Prefs): MutableList<Day> {
        val out = mutableListOf<Day>()
        for (part in p.usage.split(";")) {
            val a = part.split(",")
            if (a.size == 3) {
                val wh = a[1].toDoubleOrNull()
                val sec = a[2].toDoubleOrNull()
                if (wh != null && sec != null) out.add(Day(a[0], wh, sec))
            }
        }
        return out
    }

    /** Adds "loadW watts for sec seconds" to today's totals and drops days older than 5 days. */
    fun add(p: Prefs, loadW: Double, sec: Double, now: Long) {
        val list = load(p)
        val today = key(now)
        var d = list.lastOrNull { it.key == today }
        if (d == null) {
            d = Day(today, 0.0, 0.0)
            list.add(d)
        }
        d.wh += loadW * sec / 3600.0
        d.sec += sec
        val cutoff = key(now - (DAYS - 1) * 24L * 3600 * 1000)
        list.removeAll { it.key < cutoff }
        p.usage = list.joinToString(";") {
            "${it.key},${String.format(Locale.US, "%.1f", it.wh)},${String.format(Locale.US, "%.0f", it.sec)}"
        }
    }

    /** Average load in W, or 0 if there is less than 10 minutes of discharge data. */
    fun avgW(p: Prefs): Double {
        val list = load(p)
        val sec = list.sumOf { it.sec }
        val wh = list.sumOf { it.wh }
        return if (sec < 600.0) 0.0 else wh * 3600.0 / sec
    }
}
