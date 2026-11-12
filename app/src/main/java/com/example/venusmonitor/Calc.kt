package com.example.venusmonitor

import kotlin.math.max
import kotlin.math.roundToInt

object Calc {
    /** Скільки Вт·год ще можна витратити: від SoC вниз до резерву (100 − pct)%.
     *  Для 5120 Вт·год і 80% → при 100% заряду це рівно 4096 Вт·год. */
    fun remainingWh(p: Prefs): Double = max(0.0, p.cap * (p.soc - (100 - p.pct)) / 100.0)

    fun hours(wh: Double, w: Double): Double = if (w < 1.0) Double.POSITIVE_INFINITY else wh / w

    fun fmt(h: Double): String {
        if (h.isNaN() || h.isInfinite()) return "—"
        val m = (h * 60).roundToInt()
        val d = m / 1440
        val hh = (m % 1440) / 60
        val mm = m % 60
        return if (d > 0) "$d д $hh г" else "$hh г $mm хв"
    }

    fun gridText(p: Prefs): String {
        val dur = if (p.since > 0) " · вже " + fmt((System.currentTimeMillis() - p.since) / 3600000.0) else ""
        return when (p.lastGrid) {
            1 -> "💡 Світло є$dur"
            0 -> "🔌 Світла немає$dur"
            else -> "❔ Світло: невідомо"
        }
    }
}
