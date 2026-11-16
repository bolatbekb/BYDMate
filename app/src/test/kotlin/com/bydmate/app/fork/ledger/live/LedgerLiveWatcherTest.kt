package com.bydmate.app.fork.ledger.live

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.data.LedgerDatabase
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.domain.car
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LedgerDatabase::class.java)
            .allowMainThreadQueries().addCallback(LedgerDatabase.seedCallback()).build()
        repo = LedgerRepository(db)
    }

    @After fun tearDown() { scope.cancel(); db.close() }

    private fun watcher() = LedgerLiveWatcher(repo, data, kind, scope) { time.addAndGet(1000L) }.also { it.start() }

    private fun until(what: String, cond: suspend () -> Boolean) = runBlocking {
        repeat(100) { if (cond()) return@runBlocking; delay(50) }
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
}
