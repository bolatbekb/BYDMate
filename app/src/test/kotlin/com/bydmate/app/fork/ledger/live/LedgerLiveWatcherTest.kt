package com.bydmate.app.fork.ledger.live

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.data.LedgerDatabase
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelDao
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.car
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LedgerLiveWatcherTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    private val data = MutableStateFlow<DiParsData?>(null)
    private val kind = MutableStateFlow(VehicleKind.HYBRID)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val time = AtomicLong(1000L)

    /** Counting / failure-injecting repository: plain subclass, no mocking of Room. */
    private inner class TestRepo : LedgerRepository(db) {
        val setStateCalls = java.util.concurrent.atomic.AtomicInteger()
        val getStateCalls = java.util.concurrent.atomic.AtomicInteger()
        val thresholdCalls = java.util.concurrent.atomic.AtomicInteger()
        @Volatile var failThreshold = false
        @Volatile var failInsertOnce = false
        override suspend fun setDetectorState(json: String?) { setStateCalls.incrementAndGet(); super.setDetectorState(json) }
        override suspend fun detectorState(): String? { getStateCalls.incrementAndGet(); return super.detectorState() }
        override suspend fun detectThresholdPp(): Int {
            thresholdCalls.incrementAndGet()
            check(!failThreshold) { "db down" }
            return super.detectThresholdPp()
        }
        override val refuels: RefuelDao get() {
            val real = super.refuels
            return object : RefuelDao by real {
                override suspend fun insert(e: RefuelEntity): Long {
                    if (failInsertOnce) {
                        failInsertOnce = false
                        error("disk full")
                    }
                    return real.insert(e)
                }
            }
        }
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LedgerDatabase::class.java)
            .allowMainThreadQueries().addCallback(LedgerDatabase.seedCallback()).build()
        repo = LedgerRepository(db)
    }

    @After fun tearDown() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        db.close()
    }

    private fun watcher(r: LedgerRepository = repo) = LedgerLiveWatcher(r, data, kind, scope) { time.addAndGet(1000L) }.also { it.start() }

    private fun until(what: String, cond: suspend () -> Boolean) = runBlocking {
        repeat(100) {
            if (cond()) return@runBlocking
            delay(50)
        }
        throw AssertionError("timeout: $what")
    }

    private fun emit(pct: Int, odo: Double = 100.0, total: Double = 10.0) {
        data.value = car(mileage = odo, fuelPercent = pct, fuelTotalL = total)
    }

    @Test fun evIsIgnored() {
        kind.value = VehicleKind.EV
        watcher()
        emit(30); runBlocking { delay(200) }
        emit(80); runBlocking { delay(300) }
        assertEquals(0, runBlocking { db.refuelDao().getAllAsc().size })
        assertNull(runBlocking { repo.detectorState() })
    }

    @Test fun draftCreatedOnceThenUpdated() {
        runBlocking { repo.setTankCapacityL(60.0) }
        watcher()
        emit(30)
        until("baseline persisted") { repo.detectorState() != null }
        emit(80)
        until("draft") { db.refuelDao().openDraft() != null }
        val d = runBlocking { db.refuelDao().openDraft()!! }
        assertEquals("detected", d.source)
        assertEquals(30, d.tankPercentBefore)
        assertEquals(10.0, d.carFuelTotalL!!, 0.0)
        assertEquals(100.0, d.odometerKm!!, 0.0)
        emit(85)
        until("updated") { db.refuelDao().openDraft()?.tankPercentAfter == 85 }
        emit(90)
        until("updated again") { db.refuelDao().openDraft()?.tankPercentAfter == 90 }
        assertEquals(1, runBlocking { db.refuelDao().getAllAsc().size })
    }

    @Test fun estimateUsesTankCapacity() {
        runBlocking { repo.setTankCapacityL(60.0) }
        watcher()
        emit(30)
        until("baseline") { repo.detectorState() != null }
        emit(80)
        until("draft") { db.refuelDao().openDraft() != null }
        assertEquals(30.0, runBlocking { db.refuelDao().openDraft()!!.liters }, 0.001)
    }

    @Test fun deletedDraftIsNotRecreated() {
        watcher()
        emit(30)
        until("baseline") { repo.detectorState() != null }
        emit(80)
        until("draft") { db.refuelDao().openDraft() != null }
        runBlocking { db.refuelDao().delete(db.refuelDao().openDraft()!!) }
        emit(85)
        until("dismissed") { repo.detectorState()?.contains("\"dismissed\":true") == true }
        emit(88)
        runBlocking { delay(300) }
        assertNull(runBlocking { db.refuelDao().openDraft() })
    }

    @Test fun baselineIsReloadedAfterRestart() {
        runBlocking { repo.setDetectorState(com.bydmate.app.fork.ledger.domain.DetectorState(30, 10.0, 100.0, 1L, null).toJson()) }
        watcher()
        emit(80)
        until("draft from persisted baseline") { db.refuelDao().openDraft() != null }
        assertNotNull(runBlocking { db.refuelDao().openDraft() })
    }

    private fun state(draftId: Long? = null) =
        com.bydmate.app.fork.ledger.domain.DetectorState(30, 10.0, 100.0, 1L, draftId)

    private fun seedDraft(odo: Double = 100.0, liters: Double = 0.0, status: String = "draft") = runBlocking {
        db.refuelDao().insert(
            com.bydmate.app.fork.ledger.data.RefuelEntity(
                date = 5L, odometerKm = odo, liters = liters, totalCost = null, pricePerLiter = null,
                fullTank = false, carFuelTotalL = 10.0, tankPercentBefore = 30, tankPercentAfter = 80,
                lat = null, lon = null, place = null, note = null,
                source = "detected", status = status, importKey = null,
            )
        )
    }

    @Test fun throttleLimitsWritesWhenNothingChanges() {
        val spy = TestRepo()
        watcher(spy)
        emit(30)
        until("first write") { repo.detectorState() != null }
        // same baseline, tiny gauge-neutral changes (fuel counter only) for 10 s of injected clock
        for (i in 1..9) { data.value = car(mileage = 100.0, fuelPercent = 30, fuelTotalL = 10.0 + i / 100.0); runBlocking { delay(30) } }
        runBlocking { delay(200) }
        assertEquals(1, spy.setStateCalls.get())
        // the baseline did not move: even much later nothing is written again
        time.addAndGet(40_000L)
        emit(30, total = 11.0)
        runBlocking { delay(200) }
        assertEquals(1, spy.setStateCalls.get())
    }

    @Test fun unchangedStreamDoesNoReadsAndNoWrites() {
        val spy = TestRepo()
        watcher(spy)
        emit(30)
        until("first write") { spy.setStateCalls.get() == 1 }
        val thresholds = spy.thresholdCalls.get()
        repeat(20) { data.value = car(mileage = 100.0, fuelPercent = 30, fuelTotalL = 10.0); runBlocking { delay(10) } }
        runBlocking { delay(200) }
        assertEquals(thresholds, spy.thresholdCalls.get())
        assertEquals(1, spy.setStateCalls.get())
        assertEquals(1, spy.getStateCalls.get())
    }

    @Test fun thresholdIsCachedForTenMinutesThenRefreshed() {
        val spy = TestRepo()
        watcher(spy)
        emit(30, odo = 100.0)
        until("first") { spy.thresholdCalls.get() == 1 }
        emit(30, odo = 100.1); emit(30, odo = 100.2)
        runBlocking { delay(300) }
        assertEquals(1, spy.thresholdCalls.get())
        time.addAndGet(601_000L)
        emit(30, odo = 100.3)
        until("refreshed") { spy.thresholdCalls.get() == 2 }
    }

    @Test fun loweredBaselineReachesDiskImmediately() {
        runBlocking { repo.setDetectorState(state().toJson()) }
        watcher()
        emit(30)
        until("persisted") { repo.detectorState() != null }
        emit(22)
        until("lowered on disk") { repo.detectorState()?.contains("\"baselinePercent\":22") == true }
    }

    @Test fun confirmedDraftIsDismissedNotRecreated() {
        val id = seedDraft(status = "confirmed")
        runBlocking { repo.setDetectorState(state(draftId = id).toJson()) }
        watcher()
        emit(85)
        until("dismissed") { repo.detectorState()?.contains("\"dismissed\":true") == true }
        emit(90)
        runBlocking { delay(300) }
        assertEquals(1, runBlocking { db.refuelDao().getAllAsc().size })
    }

    @Test fun restartMidRiseWithDraftIdMakesNoSecondDraft() {
        val id = seedDraft()
        runBlocking { repo.setDetectorState(state(draftId = id).toJson()) }
        watcher()
        emit(88)
        until("updated") { db.refuelDao().getById(id)?.tankPercentAfter == 88 }
        assertEquals(1, runBlocking { db.refuelDao().getAllAsc().size })
    }

    @Test fun restartInInsertPersistWindowAdoptsOpenDraft() {
        val id = seedDraft()
        runBlocking { repo.setDetectorState(state().toJson()) }
        watcher()
        emit(88)
        until("adopted") { db.refuelDao().getById(id)?.tankPercentAfter == 88 }
        until("draftId persisted") { repo.detectorState()?.contains("\"draftId\":$id") == true }
        assertEquals(1, runBlocking { db.refuelDao().getAllAsc().size })
    }

    @Test fun dbFailureThenRecoveryKeepsCollecting() {
        val spy = TestRepo().also { it.failThreshold = true }
        watcher(spy)
        emit(30)
        runBlocking { delay(200) }
        assertNull(runBlocking { repo.detectorState() })
        spy.failThreshold = false
        emit(31)
        until("recovered") { repo.detectorState() != null }
    }

    @Test fun secondStartDoesNotDoubleCollect() {
        val spy = TestRepo()
        val w = watcher(spy)
        w.start()
        emit(30)
        until("first write") { repo.detectorState() != null }
        runBlocking { delay(200) }
        assertEquals(1, spy.getStateCalls.get())
    }

    @Test fun userTypedLitersSurviveLaterGaugeRise() {
        runBlocking { repo.setTankCapacityL(60.0) }
        watcher()
        emit(30)
        until("baseline") { repo.detectorState() != null }
        emit(80)
        until("draft") { db.refuelDao().openDraft() != null }
        val d = runBlocking { db.refuelDao().openDraft()!! }
        runBlocking { db.refuelDao().update(d.copy(liters = 41.5)) }
        emit(85)
        until("percent updated") { db.refuelDao().openDraft()?.tankPercentAfter == 85 }
        assertEquals(41.5, runBlocking { db.refuelDao().openDraft()!!.liters }, 0.0)
    }

    @Test fun failedInsertIsRetriedOnNextSample() {
        val spy = TestRepo().also { it.failInsertOnce = true }
        watcher(spy)
        emit(30)
        until("baseline") { repo.detectorState() != null }
        emit(80)
        runBlocking { delay(200) }
        assertNull(runBlocking { db.refuelDao().openDraft() })
        emit(81)
        until("draft after retry") { db.refuelDao().openDraft() != null }
        assertEquals(1, runBlocking { db.refuelDao().getAllAsc().size })
    }

    @Test fun unrelatedOpenDraftIsNotAdopted() {
        seedDraft(odo = 5000.0)
        runBlocking { repo.setDetectorState(state().toJson()) }
        watcher()
        emit(88)
        until("second draft") { runBlocking { db.refuelDao().getAllAsc().size } == 2 }
    }
}
