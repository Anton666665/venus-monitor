package com.example.venusmonitor

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.math.roundToInt

/** Marstek Open API: UDP + JSON-RPC (за замовчуванням порт 30000). */
object MarstekClient {
    data class Status(val soc: Int, val pvW: Double, val ongridW: Double, val offgridW: Double, val batW: Double, val tempC: Double, val raw: String)

    // Батарея відповідає на той самий порт, тому локальний сокет теж прив'язуємо до нього
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

    private fun nz(d: Double): Double = if (d.isNaN()) 0.0 else d

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
                return Status(
                    soc.roundToInt(), nz(num("pv_power")),
                    nz(num("ongrid_power")), nz(num("offgrid_power")),
                    nz(num("bat_power")), findTemp(), sb.toString()
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    /** Пошук батареї в локальній мережі (broadcast). */
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
