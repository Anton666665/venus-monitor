package com.example.venusmonitor

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Average consumption over the last 5 days, counted only while the battery was discharging,
 * kept separately for three parts of the day: 00-08, 08-16, 16-24.
 */
object Usage {
    private const val DAYS = 5
    private const val SEG_MS = 8L * 3600 * 1000
    private const val MIN_SEC = 600.0
    private class Row(val key: String, val seg: Int, var wh: Double, var sec: Double)

    private fun key(ts: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    fun segOf(ts: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        return c.get(Calendar.HOUR_OF_DAY) / 8
    }

    /** Start of the next 8-hour part of the day after ts (local time). */
    private fun nextBoundary(ts: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.HOUR_OF_DAY, (c.get(Calendar.HOUR_OF_DAY) / 8) * 8)
        c.add(Calendar.HOUR_OF_DAY, 8)
        return c.timeInMillis
    }

    private fun load(p: Prefs): MutableList<Row> {
        val out = mutableListOf<Row>()
        for (part in p.usage.split(";")) {
            val a = part.split(",")
            if (a.size == 4) {
                val seg = a[1].toIntOrNull()
                val wh = a[2].toDoubleOrNull()
                val sec = a[3].toDoubleOrNull()
                if (seg != null && seg in 0..2 && wh != null && sec != null) out.add(Row(a[0], seg, wh, sec))
            }
        }
        return out
    }

    /** Adds "loadW watts for sec seconds" to today's totals of the current part of the day; drops days older than 5. */
    fun add(p: Prefs, loadW: Double, sec: Double, now: Long) {
        val list = load(p)
        val today = key(now)
        val seg = segOf(now)
        var d = list.lastOrNull { it.key == today && it.seg == seg }
        if (d == null) {
            d = Row(today, seg, 0.0, 0.0)
            list.add(d)
        }
        d.wh += loadW * sec / 3600.0
        d.sec += sec
        val cutoff = key(now - (DAYS - 1) * 24L * 3600 * 1000)
        list.removeAll { it.key < cutoff }
        p.usage = list.joinToString(";") {
            "${it.key},${it.seg},${String.format(Locale.US, "%.1f", it.wh)},${String.format(Locale.US, "%.0f", it.sec)}"
        }
    }

    /** Average load in W over all parts of the day, or 0 if there is less than 10 minutes of discharge data. */
    fun avgW(p: Prefs): Double {
        val list = load(p)
        val sec = list.sumOf { it.sec }
        val wh = list.sumOf { it.wh }
        return if (sec < MIN_SEC) 0.0 else wh * 3600.0 / sec
    }

    /** Average load in W for one part of the day (0..2); falls back to the overall average if that part has < 10 min of data. */
    fun segAvgW(p: Prefs, seg: Int): Double {
        val list = load(p).filter { it.seg == seg }
        val sec = list.sumOf { it.sec }
        val wh = list.sumOf { it.wh }
        return if (sec < MIN_SEC) avgW(p) else wh * 3600.0 / sec
    }

    /** True if the given part of the day has its own average (>= 10 min of data). */
    fun hasSeg(p: Prefs, seg: Int): Boolean = load(p).filter { it.seg == seg }.sumOf { it.sec } >= MIN_SEC

    /**
     * Hours until remainingWh is used up, walking forward through the parts of the day
     * with the average load of each part. NaN if there is no data at all; infinity if longer than 30 days.
     */
    fun hoursLeft(p: Prefs, remainingWh: Double, now: Long): Double {
        if (avgW(p) <= 0.0) return Double.NaN
        var r = remainingWh
        if (r <= 0.0) return 0.0
        var cur = now
        var total = 0.0
        for (i in 0 until 90) {
            val end = nextBoundary(cur)
            val w = segAvgW(p, segOf(cur))
            val sliceH = (end - cur) / 3600000.0
            val e = w * sliceH
            if (w > 0.0 && e >= r) return total + r / w
            r -= e
            total += sliceH
            cur = end
        }
        return Double.POSITIVE_INFINITY
    }
}
