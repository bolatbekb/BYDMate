package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EnergyBucketsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    // 2026-11-16 is a Monday.
    private val now = ZonedDateTime.of(2026, 11, 16, 18, 42, 0, 0, zone)

    private fun ts(h: Int, m: Int = 0, daysAgo: Long = 0) =
        now.toLocalDate().minusDays(daysAgo).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun trip(start: Long, km: Double?, kwh: Double? = null, l: Double? = null, hev: Double? = null) =
        TripEntity(startTs = start, endTs = null, distanceKm = km, kwhConsumed = kwh, fuelLiters = l, hevKm = hev)

    @Test fun weekBucketsByStartDay() {
        val s = EnergyBuckets.build(
            listOf(trip(ts(9), 10.0, 2.0), trip(ts(9, 0, 6), 10.0, 3.0)), ChartPeriod.WEEK, now)
        assertEquals(7, s.labels.size)
        assertEquals(6, s.current)
        assertEquals("Вт", s.labels[0])
        assertEquals("Пн", s.labels[6])
        assertEquals(3.0, s.kwh[0], 1e-9)
        assertEquals(2.0, s.kwh[6], 1e-9)
        assertEquals(0.0, s.kwh[3], 1e-9)
    }

    @Test fun dayHasTwelveTwoHourBuckets() {
        val s = EnergyBuckets.build(listOf(trip(ts(9, 30), 10.0, 2.0)), ChartPeriod.DAY, now)
        assertEquals(12, s.labels.size)
        assertEquals(12, s.kwh.size)
        assertEquals("00", s.labels[0])
        assertEquals("18", s.labels[9])
        assertEquals(9, s.current)
        assertEquals(2.0, s.kwh[4], 1e-9)
    }

    @Test fun monthHasThirtyDays() {
        val s = EnergyBuckets.build(emptyList(), ChartPeriod.MONTH, now)
        assertEquals(30, s.labels.size)
        assertEquals(29, s.current)
        assertEquals("16", s.labels[29])
        assertEquals("18", s.labels[0]) // 29 days before 16 Nov is 18 Oct
    }

    @Test fun tripCountsOnceInStartBucket() {
        val s = EnergyBuckets.build(
            listOf(trip(ts(23, 50, 1), 10.0, 2.0).copy(endTs = ts(0, 40))), ChartPeriod.WEEK, now)
        assertEquals(2.0, s.kwh[5], 1e-9)
        assertEquals(0.0, s.kwh[6], 1e-9)
        assertEquals(2.0, s.totalKwh, 1e-9)
    }

    @Test fun totalsAndPer100() {
        val s = EnergyBuckets.build(
            listOf(trip(ts(9), 10.0, 1.5), trip(ts(10), 20.0, 3.0, 1.0)), ChartPeriod.DAY, now)
        assertEquals(4.5, s.totalKwh, 1e-9)
        assertEquals(30.0, s.totalKm, 1e-9)
        assertEquals(15.0, s.kwhPer100!!, 1e-9)
        assertEquals(1.0, s.totalLitres!!, 1e-9)
        assertEquals(5.0, s.litresPer100!!, 1e-9)
        assertEquals(1.0, s.litres!![5], 1e-9)
        assertEquals(false, s.empty)
    }

    @Test fun electricShare() {
        val s = EnergyBuckets.build(
            listOf(trip(ts(9), 100.0, 5.0, 4.0, hev = 39.0)), ChartPeriod.DAY, now)
        assertEquals(61, s.electricShare)
    }

    @Test fun noHevKmMeansNoShare() {
        val s = EnergyBuckets.build(listOf(trip(ts(9), 100.0, 5.0, 4.0)), ChartPeriod.DAY, now)
        assertNull(s.electricShare)
    }

    @Test fun noFuelHidesLitres() {
        val s = EnergyBuckets.build(
            listOf(trip(ts(9), 100.0, 5.0, null, hev = 0.0)), ChartPeriod.DAY, now)
        assertNull(s.litres); assertNull(s.totalLitres); assertNull(s.litresPer100)
        assertNull(s.electricShare)
    }

    @Test fun negativeBucketPlotsZero() {
        val s = EnergyBuckets.build(listOf(trip(ts(9), 5.0, -1.0)), ChartPeriod.DAY, now)
        assertEquals(0.0, s.kwh[4], 0.0)
        assertEquals(0.0, s.totalKwh, 0.0)
    }

    @Test fun emptyWindow() {
        val s = EnergyBuckets.build(emptyList(), ChartPeriod.WEEK, now)
        assertTrue(s.empty)
        assertEquals(0.0, s.totalKwh, 0.0)
        assertNull(s.kwhPer100)
    }
}
