package com.example.venusmonitor

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/** Marstek Open API: UDP + JSON-RPC (за замовчуванням порт 30000). */
object MarstekClient {
    data class Status(val soc: Int, val pvW: Double, val ongridW: Double, val offgridW: Double, val batW: Double)

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

    fun status(ip: String, port: Int): Status? {
        try {
            socket(port, 3000).use { s ->
                val st = call(s, ip, port, "ES.GetStatus", JSONObject().put("id", 0)) ?: return null
                var soc = st.optInt("bat_soc", -1)
                var on = st.optDouble("ongrid_power", Double.NaN)
                var off = st.optDouble("offgrid_power", Double.NaN)
                if (soc < 0 || on.isNaN() || off.isNaN()) {
                    val m = try { call(s, ip, port, "ES.GetMode", JSONObject().put("id", 0)) } catch (e: Exception) { null }
                    if (m != null) {
                        if (soc < 0) soc = m.optInt("bat_soc", -1)
                        if (on.isNaN()) on = m.optDouble("ongrid_power", Double.NaN)
                        if (off.isNaN()) off = m.optDouble("offgrid_power", Double.NaN)
                    }
                }
                if (soc < 0) return null
                return Status(
                    soc, st.optDouble("pv_power", 0.0),
                    if (on.isNaN()) 0.0 else on, if (off.isNaN()) 0.0 else off,
                    st.optDouble("bat_power", 0.0)
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
