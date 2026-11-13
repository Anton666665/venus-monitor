package com.example.venusmonitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object Refresh {
    private val lock = Any()

    /** Blocking call — run it off the main thread. Calls are serialized (one UDP socket at a time). */
    fun run(ctx: Context): Boolean = synchronized(lock) { runInner(ctx) }

    private fun runInner(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val prevTs = p.ts
        val st = if (p.ip.isBlank()) null else MarstekClient.status(p.ip, p.port)
        if (st != null) {
            val now = System.currentTimeMillis()
            val load = (max(st.ongridW, 0.0) + max(st.offgridW, 0.0)).toFloat()
            // After a long gap take the new value as is instead of smoothing
            val fresh = p.ts == 0L || p.ema <= 0f || now - p.ts > 120_000
            p.ema = if (fresh) load else 0.3f * load + 0.7f * p.ema
            p.soc = st.soc
            p.onW = st.ongridW.toFloat()
            p.offW = st.offgridW.toFloat()
            p.batW = st.batW.toFloat()
            p.pvW = st.pvW.toFloat()
            p.temp = st.tempC.toFloat()
            p.raw = st.raw
            if (!st.capWh.isNaN() && st.capWh > 500) p.capDev = st.capWh.roundToInt()
            p.ts = now
        }
        updateGrid(ctx, p, st)
        if (st != null && prevTs > 0) {
            val dt = (p.ts - prevTs) / 1000.0
            if (dt >= 1.0 && dt <= 300.0 && Calc.direction(p) == -1) Usage.add(p, Calc.loadW(p).toDouble(), dt, p.ts)
        }
        checkLoad(ctx, p)
        checkLow(ctx, p)
        Widgets.updateAll(ctx)
        return st != null
    }

    private fun updateGrid(ctx: Context, p: Prefs, st: MarstekClient.Status?) {
        val cand: Int = if (p.gridMode == 1) {
            // Phone charging from a wall outlet (not from the battery) means power is on
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if ((i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0) 1 else 0
        } else if (st == null) {
            -1
        } else {
            // Heuristic: the load is fed from the backup output and nothing comes from the grid → no power
            val on = abs(st.ongridW)
            val off = abs(st.offgridW)
            if (off > 15 && on < 15) 0 else if (on >= 15) 1 else -1
        }
        if (cand == -1) { p.pend = -2; p.pendCount = 0; return }
        if (cand == p.lastGrid) { p.pendCount = 0; p.pend = -2; return }
        if (p.lastGrid != -1) {
            // A change must be seen twice in a row (once if background monitoring is off) to avoid false alerts
            if (p.pend == cand) p.pendCount += 1 else { p.pend = cand; p.pendCount = 1 }
            if (p.pendCount < (if (p.monitor) 2 else 1)) return
        }
        switchGrid(ctx, p, st, cand)
    }

    private fun switchGrid(ctx: Context, p: Prefs, st: MarstekClient.Status?, cand: Int) {
        val now = System.currentTimeMillis()
        if (p.lastGrid != -1 && p.since > 0) {
            val dur = Calc.fmtMs(now - p.since)
            if (cand == 1) Notifier.alert(ctx, 1, "💡 Power is back", "It was off for $dur")
            else Notifier.alert(ctx, 1, "🔌 Power lost", "It was on for $dur")
        }
        // At the start of an outage start from the real load, not from the "charging" value
        if (cand == 0 && st != null) p.ema = (max(st.ongridW, 0.0) + max(st.offgridW, 0.0)).toFloat()
        History.add(p, now, cand)
        if (cand == 1) p.lowAlerted = false
        p.lastGrid = cand
        p.since = now
        p.pend = -2
        p.pendCount = 0
    }

    private fun checkLow(ctx: Context, p: Prefs) {
        val th = p.lowPct
        if (th <= 0 || p.soc < 0) { p.lowAlerted = false; return }
        val onBattery = p.lastGrid == 0 || Calc.direction(p) == -1
        if (!p.lowAlerted && onBattery && p.soc <= th) {
            val h = Calc.hours(Calc.remainingWh(p), p.ema.toDouble())
            Notifier.alert(ctx, 4, "🔋 Battery at ${p.soc}%",
                "Lasts about ${Calc.fmt(h)}${Calc.until(h)} (down to ${100 - p.pct}%)")
            p.lowAlerted = true
        } else if (p.lowAlerted && p.soc > th + 3) {
            p.lowAlerted = false
        }
    }

    private fun checkLoad(ctx: Context, p: Prefs) {
        val th = p.alertW
        if (th <= 0) { p.alerted = false; return }
        if (!p.alerted && p.ema > th) {
            Notifier.alert(ctx, 3, "⚠ High consumption", "${p.ema.roundToInt()} W (limit $th W)")
            p.alerted = true
        } else if (p.alerted && p.ema < th * 0.9f) {
            p.alerted = false
        }
    }
}
