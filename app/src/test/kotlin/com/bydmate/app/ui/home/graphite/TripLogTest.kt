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
        val line = TripLogFormat.tick(at("2026-11-18", "08:00:10"), TripTick.of(anchor, d, open, fresh = true), zone)
        println(line)
        assertTrue(line, line.startsWith("08:00:10 a=08:00:00 g=4 v=45 rpm=1500 ps=2 f=1 odo=17000.25 kwh=2650.1 fuel=- hev=- m=D km=0.25 ice=0.25 ev=0 "))
    }

    @Test
    fun tickLineWithoutSessionOrReadingUsesDashes() {
        val line = TripLogFormat.tick(at("2026-11-18", "09:01:02"), TripTick.of(null, null, null), zone)
        println(line)
        assertEquals("09:01:02 a=- g=- v=- rpm=- ps=- f=- odo=- kwh=- fuel=- hev=- m=- km=- ice=- ev=- dr=- pk=-", line)
    }

    @Test
    fun tickLineSecondSample() {
        val line = TripLogFormat.tick(at("2026-11-18", "08:05:00"), TripTick(anchor, 1, 0, 0, 1, 17010.0, 2651.5, 12.3, 5, Mode.PARK, 10.0, 8.0, 2.0, 300000, 120000, false), zone)
        println(line)
        assertEquals("08:05:00 a=08:00:00 g=1 v=0 rpm=0 ps=1 f=0 odo=17010 kwh=2651.5 fuel=12.3 hev=5 m=P km=10 ice=8 ev=2 dr=300000 pk=120000", line)
    }

    @Test
    fun eventLine() {
        val line = TripLogFormat.event(at("2026-11-18", "08:00:00"), "open", zone, listOf("anchor" to 5L, "odo" to 17000.5, "x" to null))
        println(line)
        assertEquals("# EVENT open t=08:00:00 anchor=5 odo=17000.5 x=-", line)
        println(TripLogFormat.event(at("2026-11-18", "08:10:00"), "finish", zone, listOf("reason" to "gap", "kept" to true)))
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
        val out = LiveTripDiagnostics.lines(LiveTripState(), emptyList(), zone)
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
        val out = LiveTripDiagnostics.lines(LiveTripState(open, fin), listOf("a", "b"), zone)
        assertTrue(out.any { it.startsWith("open: anchor=08:00:00 start=08:00:00 lastSeen=08:01:00 km=5 kwh=1 l=0.5") })
        assertTrue(out.contains("finished: 12 kept, last 10"))
        assertEquals(10, out.count { it.startsWith("  ") && it.contains("km=") })
        assertEquals(listOf("a", "b"), out.takeLast(2))
    }

    // ---- event detection against the real tracker ----

    private fun reading(odo: Double?, kwh: Double? = 2650.0) = diParsData(mileage = odo, totalElecConsumption = kwh).copy(engineRpm = 0, gear = 4, speed = 40)

    private fun events(tr: LiveTripTracker, anc: Long?, d: com.bydmate.app.data.remote.DiParsData?, now: Long, boot: BootInfo = TEST_BOOT): List<String> {
        val before = tr.state
        tr.onTick(boot, anc, d, now)
        return (tr.drainEvents() + TripLogEvents.diff(before, tr.state)).map { it.name + ":" + it.fields.joinToString(",") { f -> "${f.first}=${f.second}" } }
    }

    @Test
    fun openFinishRebaseAndDroppedEnergy() {
        val t0 = at("2026-11-18", "08:00:00")
        val tr = LiveTripTracker(LiveTripState(), t0)
        assertTrue(events(tr, anchor, reading(17000.0), t0).any { it.startsWith("open:") })
        assertTrue(events(tr, anchor, reading(17000.2), t0 + 10_000).isEmpty())
        // odometer goes backwards: rebase
        assertEquals("rebase", events(tr, anchor, reading(16990.0), t0 + 20_000).first().substringBefore(':'))
        // a counter step no car can make: energy dropped
        val e = events(tr, anchor, reading(16990.1, kwh = 2900.0), t0 + 30_000)
        assertTrue(e.toString(), e.any { it.startsWith("energy_dropped:what=kwh") })
        // a new boot
        val fin = events(tr, null, null, t0 + 40_000, TEST_BOOT.copy(bootCount = 8))
        assertTrue(fin.toString(), fin.any { it.startsWith("cycle_end:reason=boot") } && fin.any { it.startsWith("finish:start=") })
    }

    @Test
    fun cycleEvents() {
        val t0 = at("2026-11-18", "08:00:00")
        val tr = LiveTripTracker(LiveTripState(), t0)
        val first = events(tr, anchor, reading(17000.0), t0)
        assertTrue(first.toString(), first[0].startsWith("cycle_new:reason=first,boot=7"))
        events(tr, anchor, reading(17000.5), t0 + 10_000)
        val gap = events(tr, anchor, reading(17001.0), t0 + 10_000 + 90_000)
        assertTrue(gap.toString(), gap.single().startsWith("cycle_continue:gapS=90,moved="))
        val boot = events(tr, anchor, reading(17001.0), t0 + 110_000 + 10_000, TEST_BOOT.copy(bootCount = 8))
        assertTrue(boot.toString(), boot[0].startsWith("cycle_end:reason=boot,sleepDeltaS=0") && boot[1].startsWith("cycle_new:reason=boot,boot=8") && boot[2].startsWith("catch_up:") && boot[3].startsWith("finish:") && boot[4].startsWith("open:"))
        val on = reading(17001.0).copy(powerState = 2)
        val off = on.copy(powerState = 0, gear = 1, speed = 0) // a real switch-off stands in P
        events(tr, anchor, off, t0 + 130_000, TEST_BOOT.copy(bootCount = 8))
        val end = events(tr, anchor, off, t0 + 140_000, TEST_BOOT.copy(bootCount = 8))
        assertTrue(end.toString(), end[0].startsWith("off:since=") && end[1].startsWith("finish:"))
    }

    @Test
    fun idleTicksAreThrottledAndChangesPassThrough() {
        val th = TickThrottle(300_000)
        val idle = TripTick.of(null, null, null)
        assertTrue(th.allow(idle, 0, false))
        assertFalse(th.allow(idle, 10_000, false))
        assertFalse(th.allow(idle, 299_000, false))
        assertTrue(th.allow(idle, 300_000, false))
        assertTrue(th.allow(idle.copy(speed = 5), 310_000, false))
    }

    private val drive = TripTick(anchor, 4, 40, 0, 1, 17000.0, 2650.0, 10.0, 5, Mode.DRIVE, 1.0, 0.0, 1.0, 1000, 0)

    @Test
    fun openSessionWritesOncePerTwoSecondsAndAtOnceOnDiscreteChange() {
        val th = TickThrottle()
        assertTrue(th.allow(drive, 0, true))
        // a changing odometer or dr= alone does not pass inside 2 s
        assertFalse(th.allow(drive.copy(odoKm = 17000.1, driveMs = 1200), 500, true))
        assertFalse(th.allow(drive.copy(speed = 55, odoKm = 17000.2), 1_900, true))
        assertTrue(th.allow(drive, 2_000, true))
        assertFalse(th.allow(drive, 3_999, true))
        assertTrue(th.allow(drive, 4_000, true))
        // discrete changes pass at once: gear, mode, powerState, rpm 0 <-> >0, speed 0 <-> >0, anchor
        assertTrue(th.allow(drive.copy(gear = 3), 4_100, true))
        assertTrue(th.allow(drive.copy(gear = 3, mode = Mode.PARK), 4_200, true))
        assertTrue(th.allow(drive.copy(gear = 3, mode = Mode.PARK, powerState = 2), 4_300, true))
        val p = drive.copy(gear = 3, mode = Mode.PARK, powerState = 2)
        assertTrue(th.allow(p.copy(rpm = 1500), 4_400, true))
        assertFalse(th.allow(p.copy(rpm = 1600), 4_500, true))
        assertTrue(th.allow(p.copy(rpm = 0), 4_600, true))
        assertTrue(th.allow(p.copy(rpm = 0, speed = 0), 4_700, true))
        assertTrue(th.allow(p.copy(rpm = 0, speed = 0, anchorMs = anchor + 1), 4_800, true))
    }

    @Test
    fun throttleKeysOnTheOpenSessionNotTheAnchor() {
        val th = TickThrottle(300_000)
        val t = drive.copy(mode = null)
        assertTrue(th.allow(t, 0, false))
        assertFalse(th.allow(t, 10_000, false))
        assertFalse(th.allow(t, 20_000, false))
    }

    @Test
    fun dayFileCapKeepsEventsAndMarksOnce() {
        val now = at("2026-11-18")
        val dir = tmp.newFolder("cap")
        val store = TripLogStore(dir, { now }, zone, maxDayBytes = 200)
        repeat(10) { store.append(now, "x".repeat(40) + it) }
        store.append(now, "tick-after-cap")
        store.append(now, "# EVENT open t=08:00:00")
        store.append(now, "tick-after-cap2")
        val lines = java.io.File(dir, "2026-11-18.log").readLines()
        assertEquals(1, lines.count { it.startsWith("# EVENT tick_cap") })
        assertFalse(lines.contains("tick-after-cap"))
        assertFalse(lines.contains("tick-after-cap2"))
        assertTrue(lines.contains("# EVENT open t=08:00:00"))
    }

    @Test
    fun dumpReadIsBoundedAndKeepsTheNewest() {
        var now = at("2026-11-17")
        val dir = tmp.newFolder("dump")
        val store = TripLogStore(dir, { now }, zone)
        java.io.File(dir, "2026-11-16.log").writeText((1..100).joinToString("") { "old$it\n" })
        java.io.File(dir, "2026-11-17.log").writeText((1..100).joinToString("") { "mid$it\n" })
        now = at("2026-11-18")
        store.append(now, "new1"); store.append(now, "new2")
        val all = store.readLines(Long.MAX_VALUE)
        assertEquals("old1", all.first())
        val small = store.readLines(300)
        assertEquals("(truncated: older log lines omitted)", small.first())
        assertEquals("new2", small.last())
        assertTrue(small.contains("new1"))
        assertFalse(small.any { it.startsWith("old") })
        assertTrue(small.drop(1).all { it.startsWith("mid") || it.startsWith("new") || it.startsWith("#") })
        assertTrue(small.drop(1).sumOf { it.length + 1 } <= 300)
    }

    @Test
    fun aMicroRebaseIsNotLogged() {
        val t0 = at("2026-11-18", "08:00:00")
        val tr = LiveTripTracker(LiveTripState(), t0)
        events(tr, anchor, reading(17057.4004), t0)
        assertTrue(events(tr, anchor, reading(17057.4), t0 + 10_000).isEmpty())
    }

    @Test
    fun streamStaysOpenAcrossLinesAndMovesWithTheDay() {
        var now = at("2026-11-18", "23:59:00")
        val dir = tmp.newFolder("open")
        val store = TripLogStore(dir, { now }, zone)
        store.append(now, "a")
        store.append(now + 1000, "b")
        val d1 = java.io.File(dir, "2026-11-18.log")
        assertEquals(listOf("a", "b"), d1.readLines().drop(1))
        now = at("2026-11-19", "00:00:30")
        store.append(now, "c")
        assertEquals(listOf("a", "b"), d1.readLines().drop(1))
        assertEquals(listOf("c"), java.io.File(dir, "2026-11-19.log").readLines().drop(1))
        store.close()
        store.append(now + 1000, "d")
        assertEquals(listOf("c", "d"), java.io.File(dir, "2026-11-19.log").readLines().drop(1))
    }
}
