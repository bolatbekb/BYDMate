package com.bydmate.app.ui.home.common

import android.content.Intent
import android.media.AudioManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeWatchTest {
    @Test fun onlyTheMusicStreamCounts() {
        val music = Intent(VOLUME_CHANGED_ACTION).putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC)
        val ring = Intent(VOLUME_CHANGED_ACTION).putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_RING)
        assertTrue(isMusicVolumeChange(music))
        assertFalse(isMusicVolumeChange(ring))
    }

    @Test fun readsAtOnceOnPingAndOnFallback() = runTest {
        val pings = Channel<Unit>(Channel.CONFLATED)
        var reads = 0
        val job = launch { readOnPingOrTimeout(pings, VOLUME_FALLBACK_MS) { reads++ } }
        runCurrent()
        assertEquals(1, reads)
        pings.trySend(Unit); runCurrent()
        assertEquals(2, reads)
        advanceTimeBy(VOLUME_FALLBACK_MS - 1); runCurrent()
        assertEquals(2, reads)
        advanceTimeBy(2); runCurrent()
        assertEquals(3, reads)
        job.cancel()
    }
}
