package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TripLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(day: String, time: String = "12:00:00") =
        java.time.LocalDateTime.parse("${day}T$time").toInstant(ZoneOffset.UTC).toEpochMilli()

    private val anchor = at("2026-11-18", "08:00:00")

    @Test
    fun tickLineIsCompactKeyValue() {
        val d = diParsData(mileage = 17000.25, totalElecConsumption = 2650.1).copy(engineRpm = 1500, gear = 4, speed = 45, powerState = 2)
        val open = LiveSession(anchor, anchor, anchor, TripCounters(17000.0, 2650.0, null, null), TripCounters(17000.25, 2650.1, null, null),
            lastMode = Mode.DRIVE, iceKm = 0.25, evKm = 0.0)
        val line = TripLogFormat.tick(at("2026-11-18", "08:00:10"), TripTick.of(anchor, d, open), zone)
        println(line)
        assertTrue(line, line.startsWith("08:00:10 a=08:00:00 g=4 v=45 rpm=1500 ps=2 odo=17000.25 kwh=2650.1 fuel=- hev=- m=D km=0.25 ice=0.25 ev=0 "))
    }

    @Test
    fun tickLineWithoutSessionOrReadingUsesDashes() {
        val line = TripLogFormat.tick(at("2026-11-18", "09:01:02"), TripTick.of(null, null, null), zone)
        println(line)
        assertEquals("09:01:02 a=- g=- v=- rpm=- ps=- odo=- kwh=- fuel=- hev=- m=- km=- ice=- ev=- dr=- pk=-", line)
    }

    @Test
    fun tickLineSecondSample() {
        val line = TripLogFormat.tick(at("2026-11-18", "08:05:00"), TripTick(anchor, 1, 0, 0, 1, 17010.0, 2651.5, 12.3, 5, Mode.PARK, 10.0, 8.0, 2.0, 300000, 120000), zone)
        println(line)
        assertEquals("08:05:00 a=08:00:00 g=1 v=0 rpm=0 ps=1 odo=17010 kwh=2651.5 fuel=12.3 hev=5 m=P km=10 ice=8 ev=2 dr=300000 pk=120000", line)
    }

    @Test
    fun eventLine() {
        val line = TripLogFormat.event(at("2026-11-18", "08:00:00"), "open", zone, listOf("anchor" to 5L, "odo" to 17000.5, "x" to null))
        println(line)
        assertEquals("# EVENT open t=08:00:00 anchor=5 odo=17000.5 x=-", line)
        println(TripLogFormat.event(at("2026-11-18", "08:10:00"), "finish", zone, listOf("reason" to "gap", "kept" to true)))
        println(TripLogFormat.event(at("2026-11-18", "08:10:01"), "merge", zone, listOf("row" to 1L, "share" to 0.5, "took" to "l+ice")))
    }

    @Test
    fun headerAndRotationKeepThreeDays() {
        var now = at("2026-11-18")
        val dir = tmp.newFolder("log")
        val store = TripLogStore(dir, { now }, zone)
        listOf("2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17").forEach { java.io.File(dir, "$it.log").writeText("old\n") }
        java.io.File(dir, "notes.txt").writeText("keep")
        store.prune()
        assertEquals(listOf("2026-11-16.log", "2026-11-17.log", "notes.txt"), dir.list()!!.sorted())
        store.append(now, "L1")
        store.append(now, "L2")
        val lines = java.io.File(dir, "2026-11-18.log").readLines()
        assertEquals(listOf(TripLogFormat.header(LocalDate.parse("2026-11-18")), "L1", "L2"), lines)
        // day change: the oldest goes on the first line of the new day
        now = at("2026-11-19", "00:00:05")
        store.append(now, "L3")
        assertFalse(java.io.File(dir, "2026-11-16.log").exists())
        assertEquals(listOf("2026-11-17.log", "2026-11-18.log", "2026-11-19.log"), dir.list()!!.filter { it.endsWith(".log") }.sorted())
        val all = store.readLines()
        assertEquals(listOf("old"), all.take(1))
        assertEquals("L3", all.last())
    }

    @Test
    fun startPrunesAndSurvivesEmptyDir() {
        val dir = tmp.newFolder("fresh")
        java.io.File(dir, "2026-10-01.log").writeText("x\n")
        val store = TripLogStore(dir, { at("2026-11-18") }, zone)
        assertTrue(store.readLines().isEmpty())
        assertFalse(java.io.File(dir, "2026-10-01.log").exists())
    }

    @Test
    fun diagnosticsEmptyState() {
        val out = LiveTripDiagnostics.lines(LiveTripState(), emptyList(), emptyList(), zone)
        assertEquals("--- live trip (fork) ---", out.first())
        assertTrue(out.contains("open: none"))
        assertTrue(out.contains("--- trip log (fork, last 3 days) ---"))
        assertEquals("(no data)", out.last())
    }

    @Test
    fun diagnosticsWithOpenAndFinished() {
        val c = TripCounters(17000.0, 2650.0, 10.0, null)
        val open = LiveSession(anchor, anchor, anchor + 60_000, c, TripCounters(17005.0, 2651.0, 10.5, null), iceKm = 3.0, evKm = 2.0, rpmSeen = true)
        val fin = (1..12).map { TripEstimate(anchor - it * 3_600_000L, anchor - it * 3_600_000L + 600_000, it.toDouble(), 1.0, null, null) }
        val out = LiveTripDiagnostics.lines(LiveTripState(open, fin), listOf("row=1 took=l"), listOf("a", "b"), zone)
        assertTrue(out.any { it.startsWith("open: anchor=08:00:00 start=08:00:00 lastSeen=08:01:00 km=5 kwh=1 l=0.5") })
        assertTrue(out.contains("finished: 12 kept, last 10"))
        assertEquals(10, out.count { it.startsWith("  ") && it.contains("km=") })
        assertTrue(out.contains("  row=1 took=l"))
        assertEquals(listOf("a", "b"), out.takeLast(2))
    }

    // ---- event detection against the real tracker ----

    private fun reading(odo: Double?, kwh: Double? = 2650.0) = diParsData(mileage = odo, totalElecConsumption = kwh).copy(engineRpm = 0, gear = 4, speed = 40)

    private fun events(tr: LiveTripTracker, anc: Long?, d: com.bydmate.app.data.remote.DiParsData?, now: Long): List<String> {
        val before = tr.state
        tr.onTick(anc, d, now)
        return TripLogEvents.diff(before, tr.state, anc, now).map { it.name + ":" + it.fields.joinToString(",") { f -> "${f.first}=${f.second}" } }
    }

    @Test
    fun openFinishRebaseAndDroppedEnergy() {
        val t0 = at("2026-11-18", "08:00:00")
        val tr = LiveTripTracker(LiveTripState(), t0)
        assertTrue(events(tr, anchor, reading(17000.0), t0)[0].startsWith("open:"))
        assertTrue(events(tr, anchor, reading(17000.2), t0 + 10_000).isEmpty())
        // odometer goes backwards: rebase
        assertEquals("rebase", events(tr, anchor, reading(16990.0), t0 + 20_000).first().substringBefore(':'))
        // a counter step no car can make: energy dropped
        val e = events(tr, anchor, reading(16990.1, kwh = 2900.0), t0 + 30_000)
        assertTrue(e.toString(), e.any { it.startsWith("energy_dropped:what=kwh") })
        // anchor switched off
        val fin = events(tr, null, null, t0 + 40_000)
        assertTrue(fin.toString(), fin.single().startsWith("finish:reason=anchor_null"))
    }

    @Test
    fun gapAndAnchorChangeReasons() {
        val t0 = at("2026-11-18", "08:00:00")
        val tr = LiveTripTracker(LiveTripState(), t0)
        events(tr, anchor, reading(17000.0), t0)
        events(tr, anchor, reading(17000.5), t0 + 10_000)
        val gap = events(tr, anchor, reading(17001.0), t0 + 10_000 + LiveTripTracker.GAP_MS + 1_000)
        assertTrue(gap.toString(), gap[0].startsWith("finish:reason=gap") && gap[1].startsWith("open:"))
        val tr2 = LiveTripTracker(LiveTripState(), t0)
        events(tr2, anchor, reading(17000.0), t0)
        events(tr2, anchor, reading(17001.0), t0 + 10_000)
        val change = events(tr2, anchor + 1, reading(17001.1), t0 + 20_000)
        assertTrue(change.toString(), change[0].startsWith("finish:reason=anchor_change") && change[0].contains("kept=true"))
    }

    @Test
    fun mergeDecisionsNameTheFieldsTaken() {
        val r = TripRow(1_000_000, 1_600_000, 5.0, null, null, null)
        val e = TripEstimate(900_000, 2_000_000, 10.0, 2.0, 1.0, 8.0, iceKm = 8.0, evKm = 2.0)
        val merged = TripMatch.merge(listOf(r), listOf(e))
        val d = TripLogEvents.mergeDecisions(listOf(r), listOf(e), merged).single()
        assertEquals(0.5, d.share, 1e-9)
        assertEquals(listOf("kwh", "l", "ice"), d.taken)
        assertTrue(TripLogEvents.mergeDecisions(listOf(r), emptyList(), listOf(r)).isEmpty())
    }
}
