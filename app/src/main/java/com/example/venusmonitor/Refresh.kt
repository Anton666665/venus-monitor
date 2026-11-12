package com.example.venusmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

object Refresh {
    /** Блокуючий виклик — запускати не з головного потоку. */
    fun run(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val st = if (p.ip.isBlank()) null else MarstekClient.status(p.ip, p.port)
        if (st != null) {
            val now = System.currentTimeMillis()
            val load = abs(st.ongridW) + abs(st.offgridW)
            // Якщо дані давно не оновлювались — беремо нове значення без згладжування
            val fresh = p.ts == 0L || p.ema <= 0f || now - p.ts > 120_000
            p.ema = if (fresh) load.toFloat() else (0.3 * load + 0.7 * p.ema).toFloat()
            p.soc = st.soc
            p.onW = st.ongridW.toFloat()
            p.offW = st.offgridW.toFloat()
            p.batW = st.batW.toFloat()
            p.pvW = st.pvW.toFloat()
            p.temp = st.tempC.toFloat()
            p.ts = now
        }
        updateGrid(ctx, p, st)
        // Коли світла точно немає, розряд батареї показує, який знак у "bat_power" означає розряд
        if (st != null && p.lastGrid == 0 && abs(st.batW) > 30) p.dischargeSign = if (st.batW > 0) 1 else -1
        VenusWidget.updateAll(ctx)
        return st != null
    }

    private fun updateGrid(ctx: Context, p: Prefs, st: MarstekClient.Status?) {
        val now: Int = if (p.gridMode == 1) {
            // Телефон заряджається від розетки (не від EPS) → є світло
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if ((i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0) 1 else 0
        } else {
            // Евристика: навантаження живиться з off-grid виходу, а з мережі нічого нема → світла немає
            if (st == null) p.lastGrid else {
                val on = abs(st.ongridW)
                val off = abs(st.offgridW)
                if (off > 15 && on < 15) 0 else if (on >= 15) 1 else p.lastGrid
            }
        }
        if (now == -1 || now == p.lastGrid) return
        if (p.lastGrid != -1) notify(ctx, if (now == 1) "💡 Світло з'явилось" else "🔌 Світло зникло")
        // На початку відключення стартуємо з реального навантаження, а не з "зарядного" значення
        if (now == 0 && st != null) p.ema = (abs(st.ongridW) + abs(st.offgridW)).toFloat()
        p.lastGrid = now
        p.since = System.currentTimeMillis()
    }

    private fun notify(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("grid", "Світло", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(1, Notification.Builder(ctx, "grid")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(text).build())
    }
}
