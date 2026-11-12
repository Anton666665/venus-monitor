package com.example.venusmonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class VenusWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, views(ctx, false))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            val app = ctx.applicationContext
            val pending = goAsync()
            updateAll(app, true)
            thread {
                try { Refresh.run(app) } finally { pending.finish() }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.venusmonitor.REFRESH"

        fun views(ctx: Context, busy: Boolean): RemoteViews {
            val p = Prefs(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            if (p.soc < 0) {
                rv.setTextViewText(R.id.w_main, "Venus: немає даних")
                rv.setTextViewText(R.id.w_grid, "")
                rv.setTextViewText(R.id.w_state, "")
                rv.setTextViewText(R.id.w_load, "")
                rv.setTextViewText(R.id.w_eta, "")
            } else {
                rv.setTextViewText(R.id.w_main, "🔋 ${p.soc}% · ${Calc.remainingWh(p).roundToInt()} Вт·год")
                rv.setTextViewText(R.id.w_grid, Calc.gridText(p))
                rv.setTextViewText(R.id.w_state, Calc.stateLine(p))
                rv.setTextViewText(R.id.w_load, Calc.loadLine(p))
                rv.setTextViewText(R.id.w_eta, Calc.etaLine(p))
            }
            val time = if (p.ts > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.ts)) else "—"
            rv.setTextViewText(R.id.w_upd, if (busy) "⏳ оновлення…" else "оновлено $time")

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            val refresh = PendingIntent.getBroadcast(
                ctx, 1, Intent(ctx, VenusWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_refresh, refresh)
            return rv
        }

        fun updateAll(ctx: Context, busy: Boolean = false) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, VenusWidget::class.java))
            for (id in ids) m.updateAppWidget(id, views(ctx, busy))
        }
    }
}
