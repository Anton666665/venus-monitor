package com.example.venusmonitor

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Log of power on/off changes, kept in preferences ("timestamp,state;..."). */
object History {
    data class Seg(val start: Long, val end: Long, val state: Int)
    data class Day(val start: Long, val onMs: Long, val offMs: Long, val segs: List<Seg>)

    private fun events(p: Prefs): MutableList<Pair<Long, Int>> {
        val out = mutableListOf<Pair<Long, Int>>()
        for (part in p.events.split(";")) {
            val a = part.split(",")
            if (a.size == 2) {
                val t = a[0].toLongOrNull()
                val s = a[1].toIntOrNull()
                if (t != null && s != null) out.add(t to s)
            }
        }
        return out
    }

    private fun write(p: Prefs, ev: List<Pair<Long, Int>>, newest: Long) {
        val cutoff = newest - 9L * 24 * 3600 * 1000
        var firstKeep = 0
        for (i in ev.indices) if (ev[i].first < cutoff) firstKeep = i
        p.events = ev.drop(firstKeep).joinToString(";") { "${it.first},${it.second}" }
    }

    fun add(p: Prefs, ts: Long, state: Int) {
        val ev = events(p)
        ev.add(ts to state)
        write(p, ev, ts)
    }

    /**
     * Manually adds "the state became `state` at `ts`" (e.g. power went off at 18:00 while the phone was away).
     * Neighbouring events with the same state are merged, and the current state is synced with the newest event.
     */
    fun addManual(p: Prefs, ts: Long, state: Int) {
        val sorted = events(p).apply { add(ts to state) }.sortedBy { it.first }
        val ev = mutableListOf<Pair<Long, Int>>()
        for (e in sorted) if (ev.isEmpty() || ev.last().second != e.second) ev.add(e)
        write(p, ev, System.currentTimeMillis())
        val last = ev.last()
        p.lastGrid = last.second
        p.since = last.first
        p.pend = -2
        p.pendCount = 0
    }

    /** Battery level change over a segment, e.g. "  · ~100% → 89%" ("~" = the samples are not right at the boundaries). */
    fun socText(p: Prefs, s: Seg, now: Long): String {
        val log = SocLog.all(p)
        if (log.isEmpty() || p.soc < 0) return ""
        val a = log.lastOrNull { it.first <= s.start } ?: log.first()
        val b = if (s.end >= now - 1000) (now to p.soc) else (log.firstOrNull { it.first >= s.end } ?: log.last())
        val approx = abs(a.first - s.start) > 600_000 || abs(b.first - s.end) > 600_000
        val t = if (approx) "~" else ""
        return "  · $t${a.second}% → $t${b.second}%"
    }

    private fun segments(p: Prefs, now: Long): List<Seg> {
        val ev = events(p)
        val out = mutableListOf<Seg>()
        for (i in ev.indices) {
            val end = if (i + 1 < ev.size) ev[i + 1].first else now
            if (end > ev[i].first) out.add(Seg(ev[i].first, end, ev[i].second))
        }
        return out
    }

    fun dayStart(now: Long, daysAgo: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return c.timeInMillis
    }

    /** The last 7 days (today first), each with its power-on/off segments, newest first. */
    fun days(p: Prefs, now: Long): List<Day> {
        val segs = segments(p, now)
        val out = mutableListOf<Day>()
        for (d in 0..6) {
            val ds = dayStart(now, d)
            val de = if (d == 0) now else dayStart(now, d - 1)
            val clipped = mutableListOf<Seg>()
            var on = 0L
            var off = 0L
            for (s in segs) {
                val a = max(s.start, ds)
                val b = min(s.end, de)
                if (b > a) {
                    clipped.add(Seg(a, b, s.state))
                    if (s.state == 1) on += b - a else off += b - a
                }
            }
            clipped.sortByDescending { it.start }
            out.add(Day(ds, on, off, clipped))
        }
        return out
    }

    fun todayLine(p: Prefs): String {
        val d = days(p, System.currentTimeMillis())[0]
        val n = d.segs.count { it.state == 0 }
        return if (n > 0) "📅 Today: 🔌 ${Calc.fmtMs(d.offMs)} without power · $n outage(s)" else "📅 Today: no outages"
    }
}
