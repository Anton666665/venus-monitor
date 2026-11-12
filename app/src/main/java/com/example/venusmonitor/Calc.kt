package com.example.venusmonitor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object Calc {
    /** Скільки Вт·год ще можна витратити: від SoC вниз до резерву (100 − pct)%. */
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

    /** 1 = заряд, -1 = розряд, 0 = очікування. */
    fun direction(p: Prefs): Int {
        val b = p.batW
        if (abs(b) < 20f) return 0
        val plusIsCharge = p.signMode == 1 || (p.signMode == 0 && p.dischargeSign != 1)
        return if ((b > 0) == plusIsCharge) 1 else -1
    }

    /** Час до 100%: до 90% з поточною потужністю, далі з потужністю taperW. */
    fun toFull(p: Prefs): Double {
        val pw = abs(p.batW).toDouble()
        if (pw < 50.0) return Double.POSITIVE_INFINITY
        val tp = 90
        val c = p.cap.toDouble()
        return if (p.soc >= tp) c * (100 - p.soc) / 100.0 / pw
        else c * (tp - p.soc) / 100.0 / pw + c * (100 - tp) / 100.0 / p.taperW
    }

    fun stateLine(p: Prefs): String {
        val t = if (p.temp.isNaN()) "" else " · 🌡 ${p.temp.roundToInt()}°C"
        val w = abs(p.batW).roundToInt()
        return when (direction(p)) {
            1 -> "⬆ Заряд $w Вт$t"
            -1 -> "⬇ Розряд $w Вт$t"
            else -> "⏸ Очікування$t"
        }
    }

    fun etaLine(p: Prefs): String = when {
        direction(p) == 1 -> "⏱ до повного заряду: " + fmt(toFull(p))
        p.soc >= 99 && p.lastGrid == 1 -> "✅ Заряджено"
        else -> "⏱ вистачить (до ${100 - p.pct}%): " + fmt(hours(remainingWh(p), p.ema.toDouble()))
    }
}
