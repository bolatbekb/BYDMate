package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * A real (anonymized) car log through the tracker: phase A = parked 14 min then a short drive in one process,
 * phase B = a confirmed off, phase C = a new process after the quickboot with a new boot.
 */
class LiveTripReplayTest {
    private class Line(val phase: Int, val atMs: Long, val boot: BootInfo, val data: DiParsData?)

    private val base = java.time.LocalDate.of(2030, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private val min = 60_000L

    private fun clock(s: String): Long = base + LocalTime.parse(s).toSecondOfDay() * 1000L

    private fun lines(): List<Line> {
        val text = javaClass.classLoader!!.getResourceAsStream("trip/replay_cycle.txt")!!.bufferedReader().readText()
        var phase = 0
        val out = ArrayList<Line>()
        for (raw in text.lines()) {
            if (raw.startsWith("# Phase B")) phase = 1
            if (raw.startsWith("# Phase C")) phase = 2
            if (raw.isBlank() || raw.startsWith("#")) continue
            val parts = raw.trim().split(' ')
            val f = parts.drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
            fun num(k: String) = f[k]?.takeIf { it != "-" }
            val at = clock(parts[0])
            // phase C has the quickboot: the sleep time grows, the boot count changes
            val sleepMs = if (phase == 2) 180_000L else f.getValue("sleepS").toLong() * 1000L
            val boot = BootInfo(f.getValue("boot").toInt(), at - clock(f.getValue("bootAt")) + sleepMs, sleepMs)
            val odo = num("odo")?.toDouble()
            val data = odo?.let {
                diParsData(mileage = it, totalElecConsumption = num("kwh")?.toDouble(), gear = num("g")?.toInt(), powerState = num("ps")?.toInt())
                    .copy(
                        speed = num("v")?.toInt(), engineRpm = num("rpm")?.toInt(),
                        fuelTotalL = num("fuel")?.toDouble(), mileageHevKm = num("hev")?.toInt(),
                    )
            }
            out.add(Line(phase, at, boot, data))
        }
        return out
    }

    private fun card(t: LiveTripTracker) = TripCardCalc.from(t.state, emptyList(), base)

    /** Feeds [ls] into [t]: a line with data is a fresh reading taken at its time, a ticker beat re-feeds the last one. */
    private fun feed(t: LiveTripTracker, ls: List<Line>, onTick: (Line, TripCardModel) -> Unit = { _, _ -> }) {
        var last: Pair<DiParsData, Long>? = null
        for (l in ls) {
            if (l.data != null) last = l.data to l.atMs
            t.onTick(l.boot, 500L, last?.first, l.atMs, last?.second ?: l.atMs)
            onTick(l, card(t))
        }
    }

    /**
     * Phase C': the realistic switch-off. The quickboot kills the process right after phase A, with no off seen; the
     * same BOOT_COUNT, the elapsed clock moves forward, the sleep time grows by 600 s.
     */
    private fun quickbootKillAfterA(a: List<Line>, c: List<Line>, killedAfterA: LiveTripState) {
        val sleptMs = a.last().boot.sleepMs + 600_000L
        val woken = c.map { Line(it.phase, it.atMs, BootInfo(a.last().boot.bootCount, it.atMs - clock("19:42:40") + sleptMs, sleptMs), it.data) }
        assertTrue(woken.first().boot.elapsedMs > a.last().boot.elapsedMs)
        val killExit = ExitInfo(reasonCode = 10, description = "stop com.bydmate.app due to quickboot", atMs = a.last().atMs + 1_000)
        val tracker3 = LiveTripTracker(killedAfterA, woken.first().atMs - 2_000, killExit)
        var firstCp: TripCardModel? = null
        feed(tracker3, woken) { _, m -> if (firstCp == null) firstCp = m }
        println("REPLAY C' first=$firstCp end=${card(tracker3)}")
        assertEquals(0.5, tracker3.state.finished.single().km, 0.1)
        assertEquals(TripCardMode.CURRENT, firstCp!!.mode)
        assertEquals(0.0, firstCp!!.km!!, 1e-9)
        assertTrue(tracker3.state.open!!.startMs != killedAfterA.open!!.startMs)
        assertEquals(0.5, card(tracker3).km!!, 0.1)
    }

    @Test
    fun parkedThenDriveThenOffThenNewBootFollowsTheCar() {
        val all = lines()
        val a = all.filter { it.phase == 0 }
        val b = all.filter { it.phase == 1 }
        val c = all.filter { it.phase == 2 }
        assertTrue(a.size > 400 && b.size == 3 && c.size == 5)

        // Phase A: one process from the first beat; the car stands on P for 14 minutes, then R -> D -> P
        val tracker = LiveTripTracker(LiveTripState(), a.first().atMs)
        val standingEnd = clock("20:02:12")
        var maxStandingKm = 0.0
        var parkAtStandingEnd = 0
        var driveAtStandingEnd = -1
        feed(tracker, a) { l, m ->
            if (l.data == null) return@feed
            assertEquals("card from power-on at ${l.atMs}", TripCardMode.CURRENT, m.mode)
            if (l.atMs < standingEnd) {
                maxStandingKm = maxOf(maxStandingKm, m.km!!)
                parkAtStandingEnd = m.parkMinutes!!
                driveAtStandingEnd = m.driveMinutes!!
            }
        }
        println("REPLAY A standing: maxKm=$maxStandingKm park=$parkAtStandingEnd drive=$driveAtStandingEnd")
        assertEquals(0.0, maxStandingKm, 1e-9)
        assertTrue("park grows to ~14 min: $parkAtStandingEnd", parkAtStandingEnd in 13..14)
        assertEquals(0, driveAtStandingEnd)
        val afterDrive = card(tracker)
        println("REPLAY A end: $afterDrive")
        assertEquals(0.5, afterDrive.km!!, 0.1)
        assertTrue(tracker.state.finished.isEmpty())

        val killedAfterA = LiveTripCodec.decode(LiveTripCodec.encode(tracker.state))

        quickbootKillAfterA(a, c, killedAfterA)

        // Phase B: two ps 0 readings 10 s apart confirm the off
        feed(tracker, b)
        val finished = tracker.state.finished.single()
        println("REPLAY B: finished km=${finished.km} open=${tracker.state.open}")
        assertNull(tracker.state.open)
        assertEquals(0.5, finished.km, 0.1)
        val last = TripCardCalc.from(tracker.state, emptyList(), base)
        assertEquals(TripCardMode.LAST, last.mode)
        assertEquals(0.5, last.km!!, 0.1)

        // Phase C: a new process after the quickboot, a new boot, a new trip
        val restored = LiveTripCodec.decode(LiveTripCodec.encode(tracker.state))
        val exit = ExitInfo(reasonCode = 10, description = "stop com.bydmate.app due to quickboot", atMs = b.last().atMs + 3_000)
        val tracker2 = LiveTripTracker(restored, c.first().atMs, exit)
        var firstC: TripCardModel? = null
        feed(tracker2, c) { _, m -> if (firstC == null) firstC = m }
        println("REPLAY C first=$firstC end=${card(tracker2)}")
        assertEquals(TripCardMode.CURRENT, firstC!!.mode)
        assertEquals(0.0, firstC!!.km!!, 0.1)
        val end = card(tracker2)
        assertEquals(TripCardMode.CURRENT, end.mode)
        assertEquals(0.5, end.km!!, 0.1)
        assertEquals(finished.km, tracker2.state.finished.single().km, 1e-9)
        assertNotNull(tracker2.state.open)
        assertTrue(tracker2.state.open!!.startMs >= c.first().atMs - min)
    }
}
