package com.bydmate.app.fork.carhost

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarNotifProbeTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val pkg = "org.example.navi"
    private val lines = mutableListOf<Pair<String, List<Pair<String, String>>>>()
    private var now = 1_000L

    @Before fun setUp() {
        CarNotifProbe.sink = { n, f -> lines += n to f }
        CarNotifProbe.clock = { now }
        CarNotifProbe.targets = setOf(pkg)
        CarNotifProbe.labels = mapOf(pkg to "Navi")
        CarNotifProbe.enabled = true
        CarNotifProbe.resetForTest()
    }

    @After fun tearDown() = CarNotifProbe.resetForTest(full = true)

    private fun sbn(p: String, n: Notification) =
        StatusBarNotification(p, p, 1, null, 0, 0, 0, n, Process.myUserHandle(), now)

    private fun plain(title: String = "T", text: String = "x") =
        NotificationCompat.Builder(ctx, "ch").setContentTitle(title).setContentText(text).build()

    private fun field(i: Int, k: String) = lines[i].second.first { it.first == k }.second

    @Test fun `car extender sets car_ext yes`() {
        val n = NotificationCompat.Builder(ctx, "ch").setContentTitle("T")
            .extend(NotificationCompat.CarExtender()).build()
        val d = CarNotifProbe.digest(n).toMap()
        assertEquals("yes", d["car_ext"])
        assertEquals("no", d["carapp_ext"])
        assertEquals("ch", d["channel"])
    }

    @Test fun `plain notification has no car_ext`() {
        assertEquals("no", CarNotifProbe.digest(plain()).toMap()["car_ext"])
    }

    @Test fun `cyrillic title is ascii`() {
        val d = CarNotifProbe.digest(plain(title = "Поворот направо")).toMap()
        assertTrue(d["title"]!!.all { it.code < 128 })
    }

    @Test fun `a post is logged with the label not the package`() {
        CarNotifProbe.onPosted(sbn(pkg, plain(text = "go $pkg now")))
        assertEquals(1, lines.size)
        assertEquals("carhost_notif", lines[0].first)
        assertEquals("Navi", field(0, "pkg"))
        assertFalse(lines[0].second.any { it.second.contains("example") })
    }

    @Test fun `disabled logs nothing`() {
        CarNotifProbe.enabled = false
        CarNotifProbe.onPosted(sbn(pkg, plain()))
        assertTrue(lines.isEmpty())
    }

    @Test fun `non target package logs nothing`() {
        CarNotifProbe.onPosted(sbn("org.other.app", plain()))
        assertTrue(lines.isEmpty())
    }

    @Test fun `rate limit one line per two seconds unless text changed`() {
        CarNotifProbe.onPosted(sbn(pkg, plain()))
        now += 500
        CarNotifProbe.onPosted(sbn(pkg, plain()))
        assertEquals(1, lines.size)
        CarNotifProbe.onPosted(sbn(pkg, plain(text = "y")))
        assertEquals(2, lines.size)
        now += 2_500
        CarNotifProbe.onPosted(sbn(pkg, plain(text = "y")))
        assertEquals(3, lines.size)
    }

    @Test fun `a throwing sink never escapes`() {
        CarNotifProbe.sink = { _, _ -> error("boom") }
        CarNotifProbe.onPosted(sbn(pkg, plain()))
    }
}
