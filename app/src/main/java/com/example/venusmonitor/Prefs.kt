package com.example.venusmonitor

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("venus", Context.MODE_PRIVATE)

    // Налаштування користувача
    var ip: String
        get() = sp.getString("ip", "") ?: ""
        set(v) = sp.edit().putString("ip", v).apply()
    var port: Int
        get() = sp.getInt("port", 30000)
        set(v) = sp.edit().putInt("port", v).apply()
    var cap: Int // повна ємність, Вт·год
        get() = sp.getInt("cap", 5120)
        set(v) = sp.edit().putInt("cap", v).apply()
    var pct: Int // який % ємності можна витратити
        get() = sp.getInt("pct", 80)
        set(v) = sp.edit().putInt("pct", v).apply()
    var avg: Int // середнє споживання, Вт (задає користувач)
        get() = sp.getInt("avg", 300)
        set(v) = sp.edit().putInt("avg", v).apply()
    var gridMode: Int // 0 = за даними батареї, 1 = за зарядкою телефона
        get() = sp.getInt("gridMode", 0)
        set(v) = sp.edit().putInt("gridMode", v).apply()

    // Останні дані
    var soc: Int
        get() = sp.getInt("soc", -1)
        set(v) = sp.edit().putInt("soc", v).apply()
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
    var ema: Float // згладжене реальне навантаження, Вт
        get() = sp.getFloat("ema", 0f)
        set(v) = sp.edit().putFloat("ema", v).apply()
    var ts: Long
        get() = sp.getLong("ts", 0L)
        set(v) = sp.edit().putLong("ts", v).apply()
    var lastGrid: Int // -1 невідомо, 0 немає, 1 є
        get() = sp.getInt("lastGrid", -1)
        set(v) = sp.edit().putInt("lastGrid", v).apply()
    var since: Long
        get() = sp.getLong("since", 0L)
        set(v) = sp.edit().putLong("since", v).apply()
}
