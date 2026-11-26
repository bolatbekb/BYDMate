package com.bydmate.app.fork.net

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wi-Fi band from the channel centre frequency in MHz. */
fun bandOf(freqMhz: Int): String = when {
    freqMhz <= 0 -> "—"
    freqMhz in 2401..2499 -> "2.4 GHz"
    freqMhz in 4900..5899 -> "5 GHz"
    freqMhz in 5900..7125 -> "6 GHz"
    else -> "$freqMhz MHz"
}

data class PingStats(val sent: Int, val received: Int, val lossPct: Int, val avgMs: Double?)

/** Parses the toybox/busybox `ping` summary; null when the summary is absent. */
fun parsePing(out: String): PingStats? {
    val tx = Regex("""(\d+) packets transmitted""").find(out)?.groupValues?.get(1)?.toIntOrNull()
    val rx = Regex("""(\d+) (?:packets )?received""").find(out)?.groupValues?.get(1)?.toIntOrNull()
    val loss = Regex("""(\d+)% packet loss""").find(out)?.groupValues?.get(1)?.toIntOrNull()
    if (tx == null || rx == null || loss == null) return null
    val avg = Regex("""[=] [\d.]+/([\d.]+)/""").find(out)?.groupValues?.get(1)?.toDoubleOrNull()
    return PingStats(tx, rx, loss, avg)
}

/** Mbit/s from bytes over milliseconds. */
fun mbps(bytes: Long, millis: Long): Double = if (millis <= 0) 0.0 else bytes * 8.0 / 1000.0 / millis

/**
 * Read-only network diagnostics for the head unit, built from Android APIs only (no root, no helper
 * daemon): the Wi-Fi link (band, link speed, RSSI), the active network and DNS, ping latency/loss,
 * the wlan byte/error counters, and — on request — a real download-speed test. The point is to find
 * where a phone-tethered connection loses speed. The report body uses fixed English-style labels
 * like the main diagnostic dump; only the button/dialog chrome is localised. fork-local.
 */
object NetworkDiagnostics {

    /** A small, globally-anycast download endpoint meant for speed tests (no personal data is sent). */
    private const val SPEED_URL = "https://speed.cloudflare.com/__down?bytes="
    private const val SPEED_BYTES = 5_000_000L
    private const val PING_HOST = "8.8.8.8"
    private const val DNS_HOST = "cloudflare.com"

    /** Builds the whole report. [speedTest] adds a ~5 MB download (metered data) when true. */
    fun collect(context: Context, speedTest: Boolean): String = buildString {
        appendLine("=== BYDMate network diagnostics ===")
        appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}  android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine()
        section("active network") { activeNetwork(context) }
        section("wifi link") { wifiLink(context) }
        section("interface counters") { interfaceCounters() }
        section("ping $PING_HOST") { ping() }
        section("dns resolve $DNS_HOST") { dns() }
        if (speedTest) section("download test (~${SPEED_BYTES / 1_000_000} MB)") { downloadTest() }
        else appendLine("download test: skipped (enable it to measure real throughput)")
    }

    private inline fun StringBuilder.section(title: String, body: () -> String) {
        appendLine("--- $title ---")
        appendLine(runCatching(body).getOrElse { "(failed: ${it.javaClass.simpleName} ${it.message})" })
        appendLine()
    }

    private fun activeNetwork(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "none (not connected)"
        val caps = cm.getNetworkCapabilities(net)
        val lp = cm.getLinkProperties(net)
        val transport = when {
            caps == null -> "?"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return buildString {
            appendLine("transport: $transport")
            if (caps != null) {
                appendLine("validated: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}  " +
                    "metered: ${!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}")
                appendLine("system estimate: down ${caps.linkDownstreamBandwidthKbps} kbps  up ${caps.linkUpstreamBandwidthKbps} kbps")
            }
            if (lp != null) {
                appendLine("interface: ${lp.interfaceName ?: "?"}")
                append("dns: " + lp.dnsServers.joinToString(", ") { it.hostAddress ?: it.toString() }.ifEmpty { "?" })
            }
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds") // ACCESS_WIFI_STATE held; BSSID shown for the owner's own diag
    @Suppress("DEPRECATION") // connectionInfo is the simplest API that works on Android 12
    private fun wifiLink(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) return "wifi disabled"
        val info = wm.connectionInfo ?: return "no wifi info"
        if (info.networkId == -1 && info.frequency <= 0) return "not associated"
        val rssi = info.rssi
        val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.calculateSignalLevel(rssi) else WifiManager.calculateSignalLevel(rssi, 5)
        val maxBars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.maxSignalLevel else 4
        return buildString {
            appendLine("band: ${bandOf(info.frequency)}  (${info.frequency} MHz)")
            appendLine("link speed: ${info.linkSpeed} Mbps")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appendLine("rx/tx: ${info.rxLinkSpeedMbps}/${info.txLinkSpeedMbps} Mbps")
            }
            appendLine("rssi: $rssi dBm  signal: $bars/$maxBars")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) appendLine("standard: ${wifiStandard(info.wifiStandard)}")
            append("bssid: ${info.bssid ?: "?"}")
        }
    }

    private fun wifiStandard(std: Int): String = when (std) {
        4 -> "11n"
        5 -> "11ac"
        6 -> "11ax (Wi-Fi 6)"
        1 -> "11a/b/g (legacy)"
        else -> "unknown ($std)"
    }

    /** rx/tx bytes, errors and drops for the wlan interface from /proc/net/dev. */
    private fun interfaceCounters(): String {
        val lines = File("/proc/net/dev").readLines()
        val wlan = lines.firstOrNull { it.trimStart().startsWith("wlan") } ?: return "no wlan interface"
        val name = wlan.substringBefore(':').trim()
        val f = wlan.substringAfter(':').trim().split(Regex("\\s+"))
        // columns: rxBytes rxPackets rxErrs rxDrop ... (8) txBytes txPackets txErrs txDrop
        fun col(i: Int) = f.getOrNull(i)?.toLongOrNull() ?: -1
        return "$name  rx ${col(0)} B (err ${col(2)}, drop ${col(3)})  tx ${col(8)} B (err ${col(10)}, drop ${col(11)})"
    }

    private fun ping(): String {
        val p = ProcessBuilder("/system/bin/ping", "-c", "5", "-W", "2", PING_HOST)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        val stats = parsePing(out) ?: return out.trim().ifEmpty { "no output" }
        return "sent ${stats.sent}, received ${stats.received}, loss ${stats.lossPct}%, avg ${stats.avgMs ?: "?"} ms"
    }

    private fun dns(): String {
        val t0 = System.currentTimeMillis()
        val addrs = InetAddress.getAllByName(DNS_HOST)
        val ms = System.currentTimeMillis() - t0
        return "$ms ms -> ${addrs.joinToString(", ") { it.hostAddress ?: it.toString() }}"
    }

    private fun downloadTest(): String {
        val conn = (URL("$SPEED_URL$SPEED_BYTES").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            val t0 = System.currentTimeMillis()
            conn.connect()
            val ttfb = System.currentTimeMillis() - t0
            val total = conn.inputStream.use { drain(it) }
            val ms = System.currentTimeMillis() - t0
            return "downloaded ${total / 1000} kB in $ms ms  ->  ${"%.1f".format(mbps(total, ms))} Mbit/s  (first byte $ttfb ms)"
        } finally {
            conn.disconnect()
        }
    }

    /** Reads the whole stream, returning the byte count. */
    private fun drain(stream: InputStream): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
        }
        return total
    }
}
