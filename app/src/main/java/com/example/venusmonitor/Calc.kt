package com.example.venusmonitor

import kotlin.math.max
import kotlin.math.roundToInt

object Calc {
    fun cap(p: Prefs): Int = if (p.useDevCap && p.capDev > 0) p.capDev else p.cap

    /** Energy that can still be used: from the current SoC down to the reserve (100 - pct)%. */
    fun remainingWh(p: Prefs): Double = max(0.0, cap(p) * (p.soc - (100 - p.pct)) / 100.0)

    fun hours(wh: Double, w: Double): Double = if (w < 1.0) Double.POSITIVE_INFINITY else wh / w

    fun fmt(h: Double): String {
        if (h.isNaN() || h.isInfinite()) return "—"
        val m = (h * 60).roundToInt()
        val d = m / 1440
        val hh = (m % 1440) / 60
        val mm = m % 60
        return when {
            d > 0 -> "$d d $hh h"
            hh > 0 -> "$hh h $mm min"
            else -> "$mm min"
        }
    }

    fun fmtMs(ms: Long): String = fmt(ms / 3600000.0)

    fun gridText(p: Prefs): String {
        val dur = if (p.since > 0 && p.lastGrid != -1) " · for " + fmtMs(System.currentTimeMillis() - p.since) else ""
        return when (p.lastGrid) {
            1 -> "💡 Power on$dur"
            0 -> "🔌 No power$dur"
            else -> "❔ Power status unknown"
        }
    }

    /** Power drawn from the grid to charge the battery (the on-grid port reports it as negative). */
    fun chargeW(p: Prefs): Float = if (p.onW < -20f) -p.onW else 0f

    /** Power delivered to the home (on-grid + backup output). */
    fun loadW(p: Prefs): Float = max(p.onW, 0f) + max(p.offW, 0f)

    /** 1 = charging, -1 = discharging, 0 = standby. */
    fun direction(p: Prefs): Int = when {
        chargeW(p) > 0f -> 1
        p.onW > 20f -> -1
        p.lastGrid != 1 && p.offW > 20f -> -1
        else -> 0
    }

    fun toFull(p: Prefs): Double {
        val w = chargeW(p).toDouble()
        return hours(cap(p) * (100 - p.soc) / 100.0, w)
    }

    fun stateLine(p: Prefs): String {
        val t = if (p.temp.isNaN()) " · 🌡 n/a" else " · 🌡 ${p.temp.roundToInt()}°C"
        return when (direction(p)) {
            1 -> "⬆ Charging ${chargeW(p).roundToInt()} W$t"
            -1 -> "⬇ Discharging ${loadW(p).roundToInt()} W$t"
            else -> "⏸ Standby$t"
        }
    }

    fun loadLine(p: Prefs): String = "🏠 Load: ${p.ema.roundToInt()} W"

    fun etaLine(p: Prefs): String = when {
        direction(p) == 1 -> if (p.soc >= 100) "✅ Fully charged" else "⏱ Full in: " + fmt(toFull(p))
        p.soc >= 99 && p.lastGrid == 1 -> "✅ Fully charged"
        else -> "⏱ Lasts (to ${100 - p.pct}%): " + fmt(hours(remainingWh(p), p.ema.toDouble()))
    }

    fun summary(p: Prefs): String {
        val g = when (p.lastGrid) { 1 -> "💡 on"; 0 -> "🔌 off"; else -> "❔" }
        return "${p.soc}% · $g · load ${p.ema.roundToInt()} W"
    }
}
