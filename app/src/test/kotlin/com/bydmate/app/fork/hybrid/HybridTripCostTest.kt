package com.bydmate.app.fork.hybrid

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.local.LocalePreferences
import com.bydmate.app.data.local.database.AppDatabase
import com.bydmate.app.data.local.entity.SettingEntity
import com.bydmate.app.data.local.entity.TariffPeriodEntity
import com.bydmate.app.data.local.entity.TariffPeriodEntity.Companion.TRIP_RULE_CHARGES
import com.bydmate.app.data.local.entity.TariffPeriodEntity.Companion.TRIP_RULE_HOME
import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.domain.cost.CostCalculator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Money of a hybrid trip: the electricity part is what the trip took out of the pack at the trip rate, never below 0;
 * the fuel part is the litres at the period's price. A pack that gained energy (regen, the engine) costs nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HybridTripCostTest {

    private lateinit var db: AppDatabase
    private lateinit var calculator: CostCalculator

    private val start = 1_795_440_000_000L // 23.11.2026

    @Before fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            db.settingsDao().set(SettingEntity(SettingsRepository.KEY_BATTERY_CAPACITY, "31.8"))
            val settings = SettingsRepository(db.settingsDao(), LocalePreferences(context))
            calculator = CostCalculator(db.tariffPeriodDao(), db.chargeDao(), db.tripDao(), settings)
        }
    }

    @After fun teardown() {
        db.close()
    }

    private suspend fun rule(tripRule: String = TRIP_RULE_HOME) = db.tariffPeriodDao().insert(
        TariffPeriodEntity(
            startTs = 0L, homeRate = HOME, dcRate = 0.50, acLossPct = 0.0, dcLossPct = 0.0,
            tripRule = tripRule, fuelPricePerLiter = FUEL,
        )
    )

    private suspend fun trip(kwh: Double?, liters: Double?, km: Double = 10.0): Long = db.tripDao().insert(
        TripEntity(
            startTs = start, endTs = start + 600_000L, distanceKm = km,
            kwhConsumed = kwh, kwhPer100km = kwh?.let { it / km * 100.0 }, fuelLiters = liters, source = "energydata",
        )
    )

    private suspend fun priced(id: Long): TripEntity {
        calculator.priceUncostedTrips(listOf(db.tripDao().getById(id)!!))
        return db.tripDao().getById(id)!!
    }

    @Test fun `downhill regen, engine off - the trip costs 0, never less`() = runBlocking {
        rule()
        val t = priced(trip(kwh = -0.9, liters = 0.0, km = 6.0))
        assertEquals(0.0, t.cost!!, 1e-9)
        assertNull(t.fuelCost)
    }

    @Test fun `the engine charged the pack - the trip costs its fuel only`() = runBlocking {
        rule()
        val t = priced(trip(kwh = -2.0, liters = 1.2, km = 40.0))
        assertEquals(1.2 * FUEL, t.fuelCost!!, 1e-9)
        assertEquals(1.2 * FUEL, t.cost!!, 1e-9)
    }

    @Test fun `mixed trip - net kWh drawn at the trip rate plus the fuel`() = runBlocking {
        rule()
        val t = priced(trip(kwh = 1.8, liters = 0.8, km = 30.0))
        assertEquals(1.8 * HOME + 0.8 * FUEL, t.cost!!, 1e-9)
        assertEquals(0.8 * FUEL, t.fuelCost!!, 1e-9)
    }

    @Test fun `pure EV trip and pure engine trip are priced as before`() = runBlocking {
        rule()
        val ev = priced(trip(kwh = 6.0, liters = null, km = 40.0))
        assertEquals(6.0 * HOME, ev.cost!!, 1e-9)
        assertNull(ev.fuelCost)
        val ice = priced(trip(kwh = null, liters = 2.0, km = 25.0))
        assertEquals(2.0 * FUEL, ice.cost!!, 1e-9)
        assertEquals(2.0 * FUEL, ice.fuelCost!!, 1e-9)
    }

    @Test fun `a re-pricing never turns a gain into a refund`() = runBlocking {
        rule()
        val regen = trip(kwh = -0.9, liters = 0.0, km = 6.0)
        val charged = trip(kwh = -2.0, liters = 1.2, km = 40.0)
        calculator.recalculate(0L, Long.MAX_VALUE)
        assertEquals(0.0, db.tripDao().getById(regen)!!.cost!!, 1e-9)
        assertEquals(1.2 * FUEL, db.tripDao().getById(charged)!!.cost!!, 1e-9)
    }

    @Test fun `the charges rule prices a gain at 0 too`() = runBlocking {
        rule(TRIP_RULE_CHARGES)
        val t = priced(trip(kwh = -0.9, liters = 0.0, km = 6.0))
        assertEquals(0.0, t.cost!!, 1e-9)
    }

    private companion object {
        const val HOME = 0.10
        const val FUEL = 2.00
    }
}
