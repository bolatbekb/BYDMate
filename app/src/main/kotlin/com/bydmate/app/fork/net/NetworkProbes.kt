package com.bydmate.app.fork.net

import android.net.TrafficStats
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Browser-relevant network probes (all read-only, no root): the per-site cost a browser pays — unlike
 * a bulk download, which streaming already proves is fine. They target the classic "apps fine, web
 * slow" causes on a tethered head unit: high per-request latency, a broken/slow IPv6 path, and an MTU
 * blackhole that stalls large TLS packets. fork-local.
 */

private const val HTTPS_PORT = 443
private const val CONNECT_TIMEOUT_MS = 8000
private val MTU_PAYLOADS = listOf(1472, 1400, 1372, 1272) // +28 (IP+ICMP) = 1500, 1428, 1400, 1300

/** Device traffic since boot via [TrafficStats] (works without the EACCES that /proc/net/dev hits on A12). */
fun trafficReport(): String {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (rx == TrafficStats.UNSUPPORTED.toLong()) return "unsupported"
    return "since boot: rx ${rx / 1_000_000} MB  tx ${tx / 1_000_000} MB  (mobile rx ${TrafficStats.getMobileRxBytes() / 1_000_000} MB)"
}

/** TCP connect + TLS handshake time to [host]:443 — the per-site cost a browser pays on every page. */
fun handshakeReport(host: String): String {
    val addr = InetAddress.getByName(host)
    val sock = Socket()
    return try {
        val t0 = System.currentTimeMillis()
        sock.connect(InetSocketAddress(addr, HTTPS_PORT), CONNECT_TIMEOUT_MS)
        val connectMs = System.currentTimeMillis() - t0
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(sock, host, HTTPS_PORT, true) as SSLSocket
        val t1 = System.currentTimeMillis()
        ssl.startHandshake()
        val tlsMs = System.currentTimeMillis() - t1
        ssl.close()
        "$host [${addr.hostAddress}]: tcp $connectMs ms + tls $tlsMs ms"
    } finally {
        runCatching { sock.close() }
    }
}

/** Connects to [host] over IPv4 and IPv6 separately — a broken/slow IPv6 path is a classic "apps fine, web slow". */
fun ipVersionReport(host: String): String {
    val addrs = runCatching { InetAddress.getAllByName(host) }.getOrElse { return "resolve failed: ${it.message}" }
    val v4 = addrs.firstOrNull { it is Inet4Address }
    val v6 = addrs.firstOrNull { it is Inet6Address }
    return "ipv4: ${connectProbe(v4)}\nipv6: ${connectProbe(v6)}"
}

private fun connectProbe(addr: InetAddress?): String {
    if (addr == null) return "none offered"
    val sock = Socket()
    return try {
        val t0 = System.currentTimeMillis()
        sock.connect(InetSocketAddress(addr, HTTPS_PORT), CONNECT_TIMEOUT_MS)
        "ok ${System.currentTimeMillis() - t0} ms [${addr.hostAddress}]"
    } catch (e: IOException) {
        "FAILED (${e.javaClass.simpleName}) [${addr.hostAddress}]"
    } finally {
        runCatching { sock.close() }
    }
}

/** Largest unfragmented payload (ping -M do) → effective path MTU; a low MTU/blackhole stalls big TLS packets. */
fun mtuReport(host: String): String {
    val ok = MTU_PAYLOADS.filter { pingDontFragment(host, it) }
    val mtu = mtuFromPayloads(ok)
    return if (mtu == null) "no unfragmented size passed (blackhole or host unreachable)"
    else "path MTU ~$mtu (largest payload ${mtu - 28} B passed with don't-fragment)"
}

/** Effective MTU = largest successful payload + 28 (IP+ICMP headers); null if none passed. */
fun mtuFromPayloads(okPayloads: List<Int>): Int? = okPayloads.maxOrNull()?.plus(28)

@Suppress("TooGenericExceptionCaught") // a failed probe is a result, never a crash
private fun pingDontFragment(host: String, payload: Int): Boolean = runCatching {
    ProcessBuilder("/system/bin/ping", "-M", "do", "-s", "$payload", "-c", "1", "-W", "2", host)
        .redirectErrorStream(true).start().waitFor() == 0
}.getOrDefault(false)
