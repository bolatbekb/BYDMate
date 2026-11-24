package com.bydmate.app.fork.carhost

import androidx.car.app.AppInfo
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import com.bydmate.app.fork.naviprobe.AsciiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarHostDigestTest {
    private val icon = CarIcon.APP_ICON

    private fun estimate(d: Double = 350.0, unit: Int = Distance.UNIT_METERS) =
        TravelEstimate.Builder(
            Distance.create(d, unit),
            DateTimeWithZone.create(0L, 5 * 3600, "X"),
        ).setRemainingTimeSeconds(90).build()

    private fun plainStep(cue: String = "Turn", road: String? = null): Step {
        val b = Step.Builder(cue).setManeuver(Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_LEFT).setIcon(icon).build())
        if (road != null) b.setRoad(road)
        return b.build()
    }

    @Test fun `trip with a roundabout step`() {
        val step = Step.Builder("Съезд на 2-й").setManeuver(
            Maneuver.Builder(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE)
                .setRoundaboutExitNumber(2).setRoundaboutExitAngle(90).setIcon(icon).build(),
        ).setRoad("Абая").build()
        val trip = Trip.Builder().addStep(step, estimate())
            .addDestination(androidx.car.app.navigation.model.Destination.Builder().setName("D").build(), estimate(12.5, Distance.UNIT_KILOMETERS))
            .build()
        val m = CarHostDigest.trip(trip).toMap()
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE.toString(), m["s0_type"])
        assertEquals("2", m["s0_exit"])
        assertEquals("90", m["s0_angle"])
        assertEquals(AsciiText.encode("Съезд на 2-й"), m["s0_cue"])
        assertEquals(AsciiText.encode("Абая"), m["s0_road"])
        assertEquals("350m", m["s0_dist"])
        assertEquals("90", m["s0_sec"])
        assertEquals("12.5km", m["dest_dist"])
        assertEquals("05:00", m["dest_eta"])
        assertEquals("1", m["steps"])
    }

    @Test fun `trip caps steps and values`() {
        val b = Trip.Builder()
        repeat(30) { b.addStep(plainStep("x".repeat(500)), estimate()) }
        val l = CarHostDigest.trip(b.build())
        val m = l.toMap()
        assertEquals("30", m["steps"])
        assertTrue(m.containsKey("s9_type"))
        assertFalse(m.keys.any { it.startsWith("s10_") })
        assertTrue(l.all { it.second.length <= CarHostDigest.MAX_VALUE })
    }

    @Test fun `null CarText gives empty string`() {
        assertEquals("", CarHostDigest.text(null))
        assertEquals("", CarHostDigest.text(CarText.create("")))
        val m = CarHostDigest.trip(Trip.Builder().addStep(plainStep(), estimate()).build()).toMap()
        assertFalse(m.containsKey("s0_road"))
        assertTrue(l(m).none { it.isEmpty() })
    }

    private fun l(m: Map<String, String>) = m.values

    @Test fun `navigation template with routing info`() {
        val strip = ActionStrip.Builder().addAction(Action.APP_ICON).build()
        val t = NavigationTemplate.Builder().setNavigationInfo(
            RoutingInfo.Builder().setCurrentStep(plainStep("Налево", "Абая"), Distance.create(1.2, Distance.UNIT_KILOMETERS))
                .setNextStep(plainStep("Прямо")).build(),
        ).setDestinationTravelEstimate(estimate(5.0, Distance.UNIT_MILES)).setActionStrip(strip).build()
        val m = CarHostDigest.template(t).toMap()
        assertEquals("NavigationTemplate", m["kind"])
        assertEquals("routing", m["nav"])
        assertEquals("1.2km", m["cur_dist"])
        assertEquals(AsciiText.encode("Налево"), m["cur_cue"])
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT.toString(), m["next_type"])
        assertEquals("no", m["lanes_img"])
        assertEquals("no", m["junction_img"])
        assertEquals("5mi", m["dest_dist"])
        assertEquals("90", m["dest_sec"])
    }

    @Test fun `navigation template with a message`() {
        val strip = ActionStrip.Builder().addAction(Action.APP_ICON).build()
        val t = NavigationTemplate.Builder().setNavigationInfo(
            MessageInfo.Builder("Маршрут").setText("Ждите").build(),
        ).setActionStrip(strip).build()
        val m = CarHostDigest.template(t).toMap()
        assertEquals("message", m["nav"])
        assertEquals(AsciiText.encode("Маршрут"), m["msg_title"])
        assertEquals(AsciiText.encode("Ждите"), m["msg_text"])
    }

    @Test fun `null template`() {
        assertEquals(listOf("kind" to "none"), CarHostDigest.template(null))
    }

    @Test fun `app info`() {
        val m = CarHostDigest.appInfo(AppInfo(2, 7, "1.4.0")).toMap()
        assertEquals("2", m["min_api"])
        assertEquals("7", m["latest_api"])
        assertEquals("1.4.0", m["lib"])
        assertNull(m["x"])
    }
}
