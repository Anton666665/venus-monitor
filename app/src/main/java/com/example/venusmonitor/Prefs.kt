package com.example.venusmonitor

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("venus", Context.MODE_PRIVATE)

    // ---- Settings ----
    var ip: String
        get() = sp.getString("ip", "") ?: ""
        set(v) = sp.edit().putString("ip", v).apply()
    var port: Int
        get() = sp.getInt("port", 30000)
        set(v) = sp.edit().putInt("port", v).apply()
    var cap: Int // manual / fallback capacity, Wh
        get() = sp.getInt("cap", 5120)
        set(v) = sp.edit().putInt("cap", v).apply()
    var useDevCap: Boolean // use the capacity reported by the battery
        get() = sp.getBoolean("useDevCap", true)
        set(v) = sp.edit().putBoolean("useDevCap", v).apply()
    var pct: Int // share of capacity that may be used (the rest is reserve)
        get() = sp.getInt("pct", 80)
        set(v) = sp.edit().putInt("pct", v).apply()
    var alertW: Int // high consumption alert, W (0 = off)
        get() = sp.getInt("alertW", 0)
        set(v) = sp.edit().putInt("alertW", v).apply()
    var pollSec: Int // polling interval of the background service
        get() = sp.getInt("pollSec", 30)
        set(v) = sp.edit().putInt("pollSec", v).apply()
    var monitor: Boolean // background service on/off
        get() = sp.getBoolean("monitor", true)
        set(v) = sp.edit().putBoolean("monitor", v).apply()
    var gridMode: Int // 0 = from battery data, 1 = phone charger
        get() = sp.getInt("gridMode", 0)
        set(v) = sp.edit().putInt("gridMode", v).apply()

    var lowPct: Int // low charge alert threshold, % (0 = off)
        get() = sp.getInt("lowPct", 40)
        set(v) = sp.edit().putInt("lowPct", v).apply()
    var lowAlerted: Boolean
        get() = sp.getBoolean("lowAlerted", false)
        set(v) = sp.edit().putBoolean("lowAlerted", v).apply()
    // ---- Latest data ----
    var soc: Int
        get() = sp.getInt("soc", -1)
        set(v) = sp.edit().putInt("soc", v).apply()
    var capDev: Int // capacity reported by the battery, Wh
        get() = sp.getInt("capDev", 0)
        set(v) = sp.edit().putInt("capDev", v).apply()
    var onW: Float
        get() = sp.getFloat("onW", 0f)
        set(v) = sp.edit().putFloat("onW", v).apply()
    var offW: Float
        get() = sp.getFloat("offW", 0f)
        set(v) = sp.edit().putFloat("offW", v).apply()
    var batW: Float
        get() = sp.getFloat("batW", 0f)
        set(v) = sp.edit().putFloat("batW", v).apply()
    var pvW: Float
        get() = sp.getFloat("pvW", 0f)
        set(v) = sp.edit().putFloat("pvW", v).apply()
    var temp: Float
        get() = sp.getFloat("temp", Float.NaN)
        set(v) = sp.edit().putFloat("temp", v).apply()
    var raw: String
        get() = sp.getString("raw", "") ?: ""
        set(v) = sp.edit().putString("raw", v).apply()
    var ema: Float // smoothed load, W
        get() = sp.getFloat("ema", 0f)
        set(v) = sp.edit().putFloat("ema", v).apply()
    var ts: Long
        get() = sp.getLong("ts", 0L)
        set(v) = sp.edit().putLong("ts", v).apply()

    var usage: String // "yyyyMMdd,Wh,seconds;..." consumption while discharging, last 5 days
        get() = sp.getString("usage", "") ?: ""
        set(v) = sp.edit().putString("usage", v).apply()

    // ---- Grid state ----
    var lastGrid: Int // -1 unknown, 0 no power, 1 power on
        get() = sp.getInt("lastGrid", -1)
        set(v) = sp.edit().putInt("lastGrid", v).apply()
    var since: Long
        get() = sp.getLong("since", 0L)
        set(v) = sp.edit().putLong("since", v).apply()
    var pend: Int
        get() = sp.getInt("pend", -2)
        set(v) = sp.edit().putInt("pend", v).apply()
    var pendCount: Int
        get() = sp.getInt("pendCount", 0)
        set(v) = sp.edit().putInt("pendCount", v).apply()
    var alerted: Boolean
        get() = sp.getBoolean("alerted", false)
        set(v) = sp.edit().putBoolean("alerted", v).apply()
    var events: String // "timestamp,state;timestamp,state;..."
        get() = sp.getString("events", "") ?: ""
        set(v) = sp.edit().putString("events", v).apply()
}
