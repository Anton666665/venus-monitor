package com.example.venusmonitor

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.math.abs
import kotlin.math.roundToInt

/** Marstek Open API: UDP + JSON-RPC (default port 30000). */
object MarstekClient {
    data class Status(
        val soc: Int, val pvW: Double, val ongridW: Double, val offgridW: Double,
        val batW: Double, val tempC: Double, val capWh: Double, val raw: String
    )

    // The battery replies to the same port, so the local socket is bound to it as well
    private fun socket(port: Int, timeoutMs: Int): DatagramSocket {
        val s = DatagramSocket(null)
        s.reuseAddress = true
        s.bind(InetSocketAddress(port))
        s.soTimeout = timeoutMs
        return s
    }

    private fun call(s: DatagramSocket, ip: String, port: Int, method: String, params: JSONObject): JSONObject? {
        val req = JSONObject().put("id", 1).put("method", method).put("params", params).toString().toByteArray()
        s.send(DatagramPacket(req, req.size, InetAddress.getByName(ip), port))
        val p = DatagramPacket(ByteArray(4096), 4096)
        s.receive(p)
        return JSONObject(String(p.data, 0, p.length)).optJSONObject("result")
    }

    /** Some firmwares send negative power as an unsigned number (e.g. 64466 instead of -1070). */
    private fun norm(v: Double): Double {
        if (v.isNaN()) return 0.0
        var x = v
        if (x >= 2147483648.0) x -= 4294967296.0
        else if (x in 32768.0..65535.0) x -= 65536.0
        return if (abs(x) > 20000.0) 0.0 else x
    }

    fun status(ip: String, port: Int): Status? {
        try {
            socket(port, 3000).use { s ->
                val sb = StringBuilder()
                fun q(method: String): JSONObject? {
                    return try {
                        val r = call(s, ip, port, method, JSONObject().put("id", 0))
                        sb.append(method).append(": ").append(r?.toString() ?: "null").append("\n")
                        r
                    } catch (e: Exception) {
                        sb.append(method).append(": ").append(e.javaClass.simpleName).append("\n")
                        null
                    }
                }
                val st = q("ES.GetStatus") ?: return null
                val mode = q("ES.GetMode")
                val bat = q("Bat.GetStatus")
                val all = listOfNotNull(st, mode, bat)
                fun num(vararg keys: String): Double {
                    for (j in all) for (k in keys) {
                        if (j.has(k)) {
                            val v = j.optDouble(k, Double.NaN)
                            if (!v.isNaN()) return v
                        }
                    }
                    return Double.NaN
                }
                fun findTemp(): Double {
                    for (j in all) {
                        val ks = j.keys()
                        while (ks.hasNext()) {
                            val k = ks.next()
                            if (k.contains("temp", true)) {
                                val v = j.optDouble(k, Double.NaN)
                                if (!v.isNaN()) return v
                            }
                        }
                    }
                    return Double.NaN
                }
                val soc = num("bat_soc", "soc")
                if (soc.isNaN()) return null
                val rated = num("rated_capacity")
                val bc = num("bat_cap")
                val cap = if (!rated.isNaN() && rated > 500) rated else if (!bc.isNaN() && bc > 500) bc else Double.NaN
                return Status(
                    soc.roundToInt(), norm(num("pv_power")),
                    norm(num("ongrid_power")), norm(num("offgrid_power")),
                    norm(num("bat_power")), findTemp(), cap, sb.toString()
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    /** Find the battery on the local network (broadcast). */
    fun discover(port: Int): String? {
        try {
            socket(port, 2500).use { s ->
                s.broadcast = true
                val req = JSONObject().put("id", 0).put("method", "Marstek.GetDevice")
                    .put("params", JSONObject().put("ble_mac", "0")).toString().toByteArray()
                s.send(DatagramPacket(req, req.size, InetAddress.getByName("255.255.255.255"), port))
                repeat(5) {
                    val p = DatagramPacket(ByteArray(4096), 4096)
                    s.receive(p)
                    val ip = JSONObject(String(p.data, 0, p.length)).optJSONObject("result")?.optString("ip", "") ?: ""
                    if (ip.isNotEmpty()) return ip
                }
            }
        } catch (e: Exception) { }
        return null
    }
}
