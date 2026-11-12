package com.example.venusmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import kotlin.concurrent.thread

/** Foreground service: polls the battery every few seconds so alerts arrive without opening the app. */
class MonitorService : Service() {
    @Volatile private var running = false
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build()
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(2, n)
        if (!running) {
            running = true
            wl = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "venus:monitor")
                .apply { setReferenceCounted(false); acquire() }
            thread { loop() }
        }
        return START_STICKY
    }

    private fun loop() {
        while (running) {
            try { Refresh.run(this) } catch (e: Exception) { }
            try { getSystemService(NotificationManager::class.java).notify(2, build()) } catch (e: Exception) { }
            val sec = Prefs(this).pollSec.coerceAtLeast(10)
            try { Thread.sleep(sec * 1000L) } catch (e: InterruptedException) { }
        }
    }

    private fun build(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("monitor", "Monitoring", NotificationManager.IMPORTANCE_LOW))
        val p = Prefs(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, HistoryActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Venus Monitor")
            .setContentText(if (p.soc < 0) "Waiting for data…" else Calc.summary(p))
            .setOngoing(true).setContentIntent(open).build()
    }

    override fun onDestroy() {
        running = false
        wl?.release()
        super.onDestroy()
    }
}
