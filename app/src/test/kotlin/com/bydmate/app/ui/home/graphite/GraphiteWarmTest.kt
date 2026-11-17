package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.autoservice.Pm25
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun nullOnDeadLinkKeepsMemoAndCounter() {
        assertEquals(Pm25Memo.Reading(pm) to 1, nextPmMemo(Pm25Memo.Reading(pm), null, 1, linkUp = false))
        assertEquals(Pm25Memo.Unknown to 0, nextPmMemo(Pm25Memo.Unknown, null, 0, linkUp = false))
    }

    @Test fun twoNullsOnLiveLinkMakeNone() {
        assertEquals(Pm25Memo.None to 2, nextPmMemo(Pm25Memo.Reading(pm), null, 1, linkUp = true))
    }

    @Test fun readingOnDeadLinkStillCounts() {
        assertEquals(Pm25Memo.Reading(pm) to 0, nextPmMemo(Pm25Memo.None, pm, 3, linkUp = false))
    }

    private val card = TripCardModel(TripCardMode.LAST, 12.4, 25, 30, 80, 2.15, 0.5)
    private val day = "2026-11-17"

    @Test fun tripCodecRoundTrip() {
        val x = WarmPrefs(Pm25Memo.None, 99.0f, null, 34.5, StoredTrip(day, card))
        assertEquals(x, GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(x)))
        val partial = StoredTrip(day, card.copy(km = null, litres = null, electricShare = null))
        val y = WarmPrefs(Pm25Memo.Unknown, null, null, null, partial)
        assertEquals(y, GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(y)))
    }

    @Test fun tripFromAnotherDayIsNotUsed() {
        assertEquals(card, tripForToday(StoredTrip(day, card), day))
        assertNull(tripForToday(StoredTrip(day, card), "2026-11-18"))
        assertNull(tripForToday(null, day))
    }

    @Test fun currentTripIsReadAsLast() {
        val cur = StoredTrip(day, card.copy(mode = TripCardMode.CURRENT))
        assertEquals(card, tripForToday(cur, day))
        val back = GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(WarmPrefs(Pm25Memo.Unknown, null, null, null, cur)))
        assertEquals(card, tripForToday(back.trip, day))
    }

    @Test fun emptyCardIsNotStored() {
        val w = WarmPrefs(Pm25Memo.Unknown, null, null, null, StoredTrip(day, TripCardModel.EMPTY))
        assertNull(GraphiteWarmCodec.decode(GraphiteWarmCodec.encode(w)).trip)
    }

    @Test fun previousFormatDecodesWithoutTrip() {
        val v1 = "W1|12,35,1,2|99.0|52.0|34.5|E"
        assertEquals(WarmPrefs(Pm25Memo.Reading(pm), 99.0f, 52.0, 34.5), GraphiteWarmCodec.decode(v1))
    }

    @Test fun garbageTripDecodesEmpty() {
        assertEquals(empty, GraphiteWarmCodec.decode("W2|?|||||E".replace("|||||E", "|||||x;y|E")))
        assertEquals(empty, GraphiteWarmCodec.decode("W2|?||||not-a-date;LAST;1;1;1;1;1;1|E"))
        assertEquals(empty, GraphiteWarmCodec.decode("W2|?||||2026-11-17;LAST;1;1|E"))
    }
}
