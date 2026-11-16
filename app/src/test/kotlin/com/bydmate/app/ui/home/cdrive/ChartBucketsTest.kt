package com.bydmate.app.ui.home.cdrive

import com.bydmate.app.data.local.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ChartBucketsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val now = ZonedDateTime.of(2026, 11, 16, 14, 30, 0, 0, zone)

    private fun ts(h: Int, m: Int = 0, daysAgo: Long = 0) =
        now.toLocalDate().minusDays(daysAgo).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun trip(start: Long, km: Double?, kwh: Double? = null, l: Double? = null) =
        TripEntity(startTs = start, distanceKm = km, kwhConsumed = kwh, fuelLiters = l)

    @Test fun dayHasTwentyFourBuckets() {
        val s = ChartBuckets.build(emptyList(), ChartPeriod.DAY, now)
        assertEquals(24, s.labels.size)
        assertEquals(24, s.energy.size)
        assertEquals("00:00", s.labels[0]); assertEquals("", s.labels[1]); assertEquals("03:00", s.labels[3])
    }

    @Test fun valuesAreSumOverSum() {
        val s = ChartBuckets.build(
            listOf(trip(ts(9, 5), 10.0, 2.0), trip(ts(9, 40), 30.0, 4.0)), ChartPeriod.DAY, now)
        assertEquals(15.0, s.energy[9]!!, 1e-9)
    }

    @Test fun emptyBucketIsGap() {
        val s = ChartBuckets.build(listOf(trip(ts(9), 10.0, 2.0)), ChartPeriod.DAY, now)
        assertNull(s.energy[8]); assertNotNull(s.energy[9])
    }

    @Test fun tripCountsOnceInStartBucket() {
        val s = ChartBuckets.build(listOf(trip(ts(23, 30), 10.0, 2.0)), ChartPeriod.DAY, now)
        assertEquals(1, s.energy.count { it != null })
        assertNotNull(s.energy[23]); assertNull(s.energy[0])
    }

    @Test fun noFuelHidesFuelSeries() {
        val s = ChartBuckets.build(listOf(trip(ts(9), 10.0, 2.0)), ChartPeriod.DAY, now)
        assertNull(s.fuel)
        val f = ChartBuckets.build(listOf(trip(ts(9), 10.0, 2.0, 1.0)), ChartPeriod.DAY, now)
        assertEquals(10.0, f.fuel!![9]!!, 1e-9)
    }

    @Test fun axisMaxNeverBelowFloor() {
        assertEquals(30.0, ChartBuckets.build(listOf(trip(ts(9), 10.0, 2.0)), ChartPeriod.DAY, now).energyMax, 0.0)
        assertEquals(40.0, ChartBuckets.build(listOf(trip(ts(9), 100.0, 37.0)), ChartPeriod.DAY, now).energyMax, 0.0)
        assertEquals(12.0, ChartBuckets.build(emptyList(), ChartPeriod.DAY, now).fuelMax, 0.0)
    }

    @Test fun weekEndsToday() {
        val s = ChartBuckets.build(listOf(trip(ts(9), 10.0, 2.0), trip(ts(9, 0, 6), 10.0, 3.0)), ChartPeriod.WEEK, now)
        assertEquals(7, s.labels.size)
        assertEquals("пн", s.labels[6]) // 2026-11-16 is a Monday
        assertEquals("вт", s.labels[0])
        assertEquals(20.0, s.energy[6]!!, 1e-9)
        assertEquals(30.0, s.energy[0]!!, 1e-9)
    }

    @Test fun monthLabelsEveryFiveDays() {
        val s = ChartBuckets.build(emptyList(), ChartPeriod.MONTH, now)
        assertEquals(30, s.labels.size)
        assertEquals("16.11", s.labels[29])
        assertEquals("11.11", s.labels[24])
        assertEquals("22.10", s.labels[4])
        assertEquals(6, s.labels.count { it.isNotEmpty() })
    }

    @Test fun negativeBucketClampsToZero() {
        val s = ChartBuckets.build(listOf(trip(ts(9), 10.0, -1.0)), ChartPeriod.DAY, now)
        assertEquals(0.0, s.energy[9]!!, 0.0)
    }

    @Test fun nanKmTripIsIgnored() {
        val s = ChartBuckets.build(
            listOf(trip(ts(9), Double.NaN, 2.0), trip(ts(9), 10.0, 2.0)), ChartPeriod.DAY, now)
        assertEquals(20.0, s.energy[9]!!, 1e-9)
    }

    @Test fun kwhWithoutLitresDoesNotAffectFuel() {
        val s = ChartBuckets.build(
            listOf(trip(ts(9), 10.0, 2.0, 1.0), trip(ts(9), 90.0, 9.0)), ChartPeriod.DAY, now)
        assertEquals(10.0, s.fuel!![9]!!, 1e-9)
        assertEquals(11.0, s.energy[9]!!, 1e-9)
    }

    @Test fun tripAtTomorrowMidnightExcluded() {
        val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val s = ChartBuckets.build(listOf(trip(tomorrow, 10.0, 2.0)), ChartPeriod.WEEK, now)
        assertEquals(0, s.energy.count { it != null })
    }

    @Test fun nullAndZeroDistanceIgnored() {
        val s = ChartBuckets.build(
            listOf(trip(ts(9), null, 2.0), trip(ts(9), 0.0, 2.0)), ChartPeriod.DAY, now)
        assertEquals(0, s.energy.count { it != null })
    }

    @Test fun weekAndMonthTripsLandInRightDay() {
        val late = trip(ts(23, 59), 10.0, 2.0)
        val early = trip(ts(0, 1), 10.0, 3.0)
        val w = ChartBuckets.build(listOf(late, early), ChartPeriod.WEEK, now)
        assertEquals(25.0, w.energy[6]!!, 1e-9)
        val yLate = trip(ts(23, 59, 1), 10.0, 4.0)
        val m = ChartBuckets.build(listOf(yLate, early), ChartPeriod.MONTH, now)
        assertEquals(40.0, m.energy[28]!!, 1e-9)
        assertEquals(30.0, m.energy[29]!!, 1e-9)
    }

    @Test fun dstDayKeepsClockHours() {
        val berlin = ZoneId.of("Europe/Berlin")
        for (day in listOf(java.time.LocalDate.of(2026, 3, 29), java.time.LocalDate.of(2026, 10, 25))) {
            val n = day.atTime(18, 0).atZone(berlin)
            val t = trip(day.atTime(15, 30).atZone(berlin).toInstant().toEpochMilli(), 10.0, 2.0)
            val s = ChartBuckets.build(listOf(t), ChartPeriod.DAY, n)
            assertEquals(24, s.labels.size)
            assertEquals("15:00", s.labels[15])
            assertEquals(20.0, s.energy[15]!!, 1e-9)
            assertEquals(1, s.energy.count { it != null })
        }
    }
}
