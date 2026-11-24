package com.bydmate.app.fork.carhost

import androidx.car.app.AppInfo
import androidx.car.app.model.CarText
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import com.bydmate.app.fork.naviprobe.AsciiText
import java.util.Locale

/**
 * Flat trace fields for what the navigator sends through the car-app library. Text goes through
 * [AsciiText] (the journal drops Cyrillic), empty values are left out, a value is at most
 * [MAX_VALUE] chars. Field names are read by the trace parser on our side.
 */
internal object CarHostDigest {
    const val MAX_VALUE = 120
    const val MAX_STEPS = 10
    private const val SECONDS_IN_HOUR = 3600
    private const val SECONDS_IN_MINUTE = 60
    private const val HOURS_IN_DAY = 24
    private const val MILLIS = 1000L

    fun text(c: CarText?): String = AsciiText.encode(c?.toCharSequence(), MAX_VALUE) ?: ""

    fun trip(t: Trip): List<Pair<String, String>> = buildList {
        val l = Fields(this)
        l.put("loading", t.isLoading.toString())
        l.put("road", text(t.currentRoad))
        val steps = t.steps
        l.put("steps", steps.size.toString())
        val estimates = t.stepTravelEstimates
        for (i in 0 until minOf(steps.size, MAX_STEPS)) {
            val s = steps[i]
            val m = s.maneuver
            l.put("s${i}_type", m?.type?.toString())
            l.put("s${i}_exit", m?.roundaboutExitNumber?.takeIf { it != 0 }?.toString())
            l.put("s${i}_angle", m?.roundaboutExitAngle?.takeIf { it != 0 }?.toString())
            l.put("s${i}_cue", text(s.cue))
            l.put("s${i}_road", text(s.road))
            l.put("s${i}_lanes", s.lanes.size.takeIf { it != 0 }?.toString())
            val e = estimates.getOrNull(i)
            l.put("s${i}_dist", e?.let { distance(it.remainingDistance) })
            l.put("s${i}_sec", e?.let { seconds(it) })
        }
        destination(l, t.destinationTravelEstimates.firstOrNull(), eta = true)
    }

    fun template(t: Template?): List<Pair<String, String>> {
        if (t == null) return listOf("kind" to "none")
        return buildList {
            val l = Fields(this)
            l.put("kind", t.javaClass.simpleName)
            if (t is NavigationTemplate) {
                when (val info = t.navigationInfo) {
                    is RoutingInfo -> {
                        l.put("nav", "routing")
                        routing(l, info)
                    }
                    is MessageInfo -> {
                        l.put("nav", "message")
                        l.put("msg_title", text(info.title))
                        l.put("msg_text", text(info.text))
                    }
                    else -> l.put("nav", "none")
                }
                destination(l, t.destinationTravelEstimate, eta = false)
            }
        }
    }

    fun appInfo(i: AppInfo): List<Pair<String, String>> = buildList {
        val l = Fields(this)
        l.put("min_api", i.minCarAppApiLevel.toString())
        l.put("latest_api", i.latestCarAppApiLevel.toString())
        l.put("lib", AsciiText.encode(i.libraryDisplayVersion, MAX_VALUE))
    }

    private fun routing(l: Fields, info: RoutingInfo) {
        val cur: Step? = info.currentStep
        l.put("cur_type", cur?.maneuver?.type?.toString())
        l.put("cur_exit", cur?.maneuver?.roundaboutExitNumber?.takeIf { it != 0 }?.toString())
        l.put("cur_cue", text(cur?.cue))
        l.put("cur_road", text(cur?.road))
        l.put("cur_dist", info.currentDistance?.let { distance(it) })
        val next: Step? = info.nextStep
        l.put("next_type", next?.maneuver?.type?.toString())
        l.put("next_cue", text(next?.cue))
        l.put("lanes_img", yesNo(cur?.lanesImage != null))
        l.put("junction_img", yesNo(info.junctionImage != null))
        l.put("loading", info.isLoading.toString())
    }

    private fun destination(l: Fields, e: TravelEstimate?, eta: Boolean) {
        if (e == null) return
        l.put("dest_dist", distance(e.remainingDistance))
        l.put("dest_sec", seconds(e))
        if (eta) l.put("dest_eta", e.arrivalTimeAtDestination?.let { clock(it) })
    }

    private fun yesNo(b: Boolean) = if (b) "yes" else "no"

    private fun seconds(e: TravelEstimate): String? =
        e.remainingTimeSeconds.takeIf { it >= 0 }?.toString()

    private fun distance(d: Distance?): String? {
        if (d == null) return null
        val unit = when (d.displayUnit) {
            Distance.UNIT_METERS -> "m"
            Distance.UNIT_KILOMETERS, Distance.UNIT_KILOMETERS_P1 -> "km"
            Distance.UNIT_MILES, Distance.UNIT_MILES_P1 -> "mi"
            Distance.UNIT_FEET -> "ft"
            Distance.UNIT_YARDS -> "yd"
            else -> "u${d.displayUnit}"
        }
        val v = d.displayDistance
        val num = if (v == Math.rint(v) && Math.abs(v) < 1e12) v.toLong().toString() else v.toString()
        return num + unit
    }

    private fun clock(t: DateTimeWithZone): String {
        val local = t.timeSinceEpochMillis / MILLIS + t.zoneOffsetSeconds
        val day = HOURS_IN_DAY * SECONDS_IN_HOUR
        val sec = ((local % day) + day) % day
        return String.format(
            Locale.US, "%02d:%02d",
            sec / SECONDS_IN_HOUR, (sec % SECONDS_IN_HOUR) / SECONDS_IN_MINUTE,
        )
    }

    /** Adds a field unless the value is null or empty; the value is capped. */
    private class Fields(private val out: MutableList<Pair<String, String>>) {
        fun put(key: String, value: String?) {
            if (value.isNullOrEmpty()) return
            out.add(key to value.take(MAX_VALUE))
        }
    }
}
