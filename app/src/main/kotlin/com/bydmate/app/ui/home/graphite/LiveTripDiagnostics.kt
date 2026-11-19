package com.bydmate.app.ui.home.graphite

import java.time.ZoneId

/** The fork's part of the diagnostic dump: the live trip state and the trip log of the kept days. */
object LiveTripDiagnostics {
    private const val LAST_FINISHED = 10

    fun lines(): List<String> = runCatching {
        lines(LiveTrip.state.value, TripLog.readLines(), ZoneId.systemDefault()) + com.bydmate.app.fork.hud.HudCustomIcons.lines()
    }.getOrElse { listOf("--- live trip (fork) ---", "(unavailable: ${it.javaClass.simpleName})") }

    fun lines(state: LiveTripState, log: List<String>, zone: ZoneId): List<String> = runCatching {
        buildList {
            fun t(ms: Long) = TripLogFormat.clock(ms, zone)
            add("--- live trip (fork) ---")
            val o = state.open
            if (o == null) add("open: none") else {
                add("open: anchor=${t(o.anchorMs)} start=${t(o.startMs)} lastSeen=${t(o.lastSeenMs)} " + estimate(o.estimate()))
                add(
                    "  boot=${o.boot?.bootCount ?: "-"} elapsedAtLast=${o.elapsedAtLastMs / 1000}s sleepAtLast=${o.sleepAtLastMs / 1000}s " +
                        "offSince=${o.offSinceMs?.let { t(it) } ?: "-"} iceSeen=${o.iceSeen} rpmTrusted=${o.rpmTrusted()} hevCounterKm=${TripLogFormat.num(o.hevCounterKm())}",
                )
            }
            state.closed?.let { add("closed: boot=${it.bootCount ?: "-"} elapsedAtLast=${it.elapsedAtLastMs / 1000}s sleepAtLast=${it.sleepAtLastMs / 1000}s at=${t(it.atMs)}") }
            state.last?.let { l ->
                val c = l.counters
                add("last: at=${t(l.atMs)} odo=${TripLogFormat.num(c.odometerKm)} kwh=${TripLogFormat.num(c.elecKwh)} fuel=${TripLogFormat.num(c.fuelL)} hev=${TripLogFormat.num(c.hevKm)}")
            }
            val last = state.finished.takeLast(LAST_FINISHED)
            add("finished: ${state.finished.size} kept, last ${last.size}")
            last.forEach { add("  ${TripLogFormat.clock(it.startMs, zone)}-${TripLogFormat.clock(it.endMs, zone)} " + estimate(it)) }
            add("--- trip log (fork, last 3 days) ---")
            if (log.isEmpty()) add("(no data)") else addAll(log)
        }
    }.getOrElse { listOf("--- live trip (fork) ---", "(unavailable: ${it.javaClass.simpleName})") }

    private fun estimate(e: TripEstimate): String {
        val n = TripLogFormat::num
        val s = e.split
        return "km=${n(e.km)} kwh=${n(e.kwh)} l=${n(e.litres)} hev=${n(e.hevKm)} ice=${n(e.iceKm)} ev=${n(e.evKm)}" +
            (s?.let { " drive=${it.driveMs / 1000}s park=${it.parkMs / 1000}s" } ?: "")
    }
}
