package com.example.venusmonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

abstract class BaseWidget : AppWidgetProvider() {
    abstract val layoutId: Int

    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, Widgets.views(ctx, layoutId, javaClass, false))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == Widgets.ACTION_REFRESH) {
            val app = ctx.applicationContext
            val pending = goAsync()
            Widgets.updateAll(app, true)
            thread {
                try { Refresh.run(app) } finally { pending.finish() }
            }
        }
    }
}

class VenusWidgetSmall : BaseWidget() { override val layoutId: Int get() = R.layout.widget_small }
class VenusWidget : BaseWidget() { override val layoutId: Int get() = R.layout.widget }
class VenusWidgetLarge : BaseWidget() { override val layoutId: Int get() = R.layout.widget_large }

object Widgets {
    const val ACTION_REFRESH = "com.example.venusmonitor.REFRESH"

    private val kinds: List<Pair<Class<out BaseWidget>, Int>> = listOf(
        VenusWidgetSmall::class.java to R.layout.widget_small,
        VenusWidget::class.java to R.layout.widget,
        VenusWidgetLarge::class.java to R.layout.widget_large
    )

    fun views(ctx: Context, layout: Int, cls: Class<*>, busy: Boolean): RemoteViews {
        val p = Prefs(ctx)
        val rv = RemoteViews(ctx.packageName, layout)
        if (p.soc < 0) {
            rv.setTextViewText(R.id.w_main, "Venus: no data")
            rv.setTextViewText(R.id.w_grid, "")
            rv.setTextViewText(R.id.w_state, "")
            rv.setTextViewText(R.id.w_load, "")
            rv.setTextViewText(R.id.w_eta, "")
            rv.setTextViewText(R.id.w_hist, "")
        } else {
            val sb = SpannableStringBuilder("🔋 ${p.soc}% · ${Calc.remainingWh(p).roundToInt()} Wh")
            val from = sb.length
            sb.append(" (${Calc.cap(p)})")
            sb.setSpan(RelativeSizeSpan(0.7f), from, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            rv.setTextViewText(R.id.w_main, sb)
            rv.setTextViewText(R.id.w_grid, Calc.gridText(p))
            rv.setTextViewText(R.id.w_state, Calc.stateLine(p))
            rv.setTextViewText(R.id.w_load, Calc.loadLine(p))
            rv.setTextViewText(R.id.w_eta, Calc.etaLine(p))
            rv.setTextViewText(R.id.w_hist, History.todayLine(p))
        }
        val time = if (p.ts > 0) SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(p.ts)) else "—"
        rv.setTextViewText(R.id.w_upd, if (busy) "⏳ updating…" else "updated $time")

        // Tap on the widget opens the power history
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, HistoryActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        rv.setOnClickPendingIntent(R.id.w_root, open)
        val refresh = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, cls).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        rv.setOnClickPendingIntent(R.id.w_refresh, refresh)
        return rv
    }

    fun updateAll(ctx: Context, busy: Boolean = false) {
        val m = AppWidgetManager.getInstance(ctx)
        for ((cls, layout) in kinds) {
            val ids = m.getAppWidgetIds(ComponentName(ctx, cls))
            for (id in ids) m.updateAppWidget(id, views(ctx, layout, cls, busy))
        }
    }
}
