package com.example.venusmonitor

/** Samples of the battery level ("timestamp,soc;..."), used to show how much it changed during an outage. */
object SocLog {
    private const val KEEP_MS = 9L * 24 * 3600 * 1000

    fun all(p: Prefs): List<Pair<Long, Int>> {
        val out = mutableListOf<Pair<Long, Int>>()
        for (part in p.socLog.split(";")) {
            val a = part.split(",")
            if (a.size == 2) {
                val t = a[0].toLongOrNull()
                val s = a[1].toIntOrNull()
                if (t != null && s != null) out.add(t to s)
            }
        }
        return out
    }

    /** Adds a sample if the level changed, or 30+ minutes passed since the last one, or force is set. */
    fun add(p: Prefs, ts: Long, soc: Int, force: Boolean = false) {
        if (soc < 0) return
        val list = all(p).toMutableList()
        val last = list.lastOrNull()
        if (!force && last != null && last.second == soc && ts - last.first < 30 * 60 * 1000) return
        if (last != null && ts <= last.first) return
        list.add(ts to soc)
        p.socLog = list.filter { it.first >= ts - KEEP_MS }.joinToString(";") { "${it.first},${it.second}" }
    }
}
