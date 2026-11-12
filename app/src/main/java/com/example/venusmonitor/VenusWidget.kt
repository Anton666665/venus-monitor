package com.example.venusmonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlin.math.roundToInt

class VenusWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, views(ctx))
    }

    companion object {
        fun views(ctx: Context): RemoteViews {
            val p = Prefs(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            if (p.soc < 0) {
                rv.setTextViewText(R.id.w_main, "Venus: немає даних")
            } else {
                val rem = Calc.remainingWh(p)
                rv.setTextViewText(R.id.w_main, "🔋 ${p.soc}% · ${rem.roundToInt()} Вт·год")
                rv.setTextViewText(R.id.w_grid, Calc.gridText(p))
                rv.setTextViewText(R.id.w_avg, "⏱ серед. ${p.avg} Вт: " + Calc.fmt(Calc.hours(rem, p.avg.toDouble())))
                rv.setTextViewText(R.id.w_real, "⏱ реал. ${p.ema.roundToInt()} Вт: " + Calc.fmt(Calc.hours(rem, p.ema.toDouble())))
            }
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, pi)
            return rv
        }

        fun updateAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, VenusWidget::class.java))
            for (id in ids) m.updateAppWidget(id, views(ctx))
        }
    }
}
