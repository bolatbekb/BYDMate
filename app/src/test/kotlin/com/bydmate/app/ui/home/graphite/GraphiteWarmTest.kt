package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.autoservice.Pm25
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphiteWarmTest {
    private val empty = WarmPrefs(Pm25Memo.Unknown, null, null, null)

    @Test fun codecRoundTrip() {
        val x = WarmPrefs(Pm25Memo.Reading(Pm25(12, 35, 1, 2)), 99.0f, 52.0, 34.5)
        assertEquals(x, GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(x)))
    }

    @Test fun codecNoneAndUnknown() {
        val none = WarmPrefs(Pm25Memo.None, 98.5f, null, 60.0)
        assertEquals(none, GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(none)))
        assertEquals(empty, GraphiteWarmCodec.decode(null))
        assertEquals(empty, GraphiteWarmCodec.decode(""))
        assertEquals(empty, GraphiteWarmCodec.decode("garbage"))
        val x = WarmPrefs(Pm25Memo.Reading(Pm25(12, 35, 1, 2)), 99.0f, 52.0, 34.5)
        assertEquals(empty, GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(x).dropLast(2)))
    }

    @Test fun pmPartialReadingRoundTrips() {
        val x = WarmPrefs(Pm25Memo.Reading(Pm25(12, null, 1, null)), null, null, null)
        assertEquals(x, GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(x)))
    }

    private val pm = Pm25(12, 35, 1, 2)

    @Test fun oneNullKeepsReading() {
        assertEquals(Pm25Memo.Reading(pm) to 1, nextPmMemo(Pm25Memo.Reading(pm), null, 0))
    }

    @Test fun twoNullsMakeNone() {
        assertEquals(Pm25Memo.None to 2, nextPmMemo(Pm25Memo.Reading(pm), null, 1))
    }

    @Test fun oneNullKeepsUnknown() {
        assertEquals(Pm25Memo.Unknown to 1, nextPmMemo(Pm25Memo.Unknown, null, 0))
    }

    @Test fun readingAfterNoneAndResetsCounter() {
        assertEquals(Pm25Memo.Reading(pm) to 0, nextPmMemo(Pm25Memo.None, pm, 5))
        assertEquals(Pm25Memo.Reading(pm) to 1, nextPmMemo(Pm25Memo.Reading(pm), null, 0))
    }
}
