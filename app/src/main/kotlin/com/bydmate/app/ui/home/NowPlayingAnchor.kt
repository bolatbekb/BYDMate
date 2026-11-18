package com.bydmate.app.ui.home

/**
 * Position anchor math (fork, P5 tier 2): the source publishes the player's own anchor
 * ([NowPlaying.positionMs] at [NowPlaying.positionAtMs] on the elapsedRealtime clock) only when it
 * changes; whoever draws the position runs it forward itself.
 *
 * Playing: anchor + (now − at) × speed. Paused: the anchor, frozen. Clamped to 0..duration
 * (a clock that went backwards never rewinds the track). Null when the player reports no position.
 */
@Suppress("LongParameterList") // a pure function of the anchor: position, time, state, speed, duration
internal fun anchoredPosition(
    positionMs: Long?,
    atMs: Long,
    nowMs: Long,
    playing: Boolean,
    speed: Float,
    durationMs: Long?,
): Long? {
    val pos = positionMs ?: return null
    val run = if (playing) ((nowMs - atMs).coerceAtLeast(0L) * speed).toLong() else 0L
    val live = (pos + run).coerceAtLeast(0L)
    return if (durationMs != null && durationMs > 0) live.coerceAtMost(durationMs) else live
}

/** The position now, run from this anchor (null: unknown). */
internal fun NowPlaying.livePosition(nowMs: Long): Long? =
    anchoredPosition(positionMs, positionAtMs, nowMs, isPlaying, playbackSpeed, durationMs)

/** This state with its position run forward to [nowMs], for the retro homes' counters. */
internal fun NowPlaying.advancedTo(nowMs: Long): NowPlaying =
    if (!isPlaying || positionMs == null) this else copy(positionMs = livePosition(nowMs), positionAtMs = nowMs)

/**
 * F3: holds the metadata of one controller and fetches it (a binder call that unparcels the cover)
 * only when the controller changes. A MediaController callback pushes later changes with [set].
 */
internal class MetadataGate<T : Any> {
    private var owner: Any? = null
    private var value: T? = null
    private var has = false

    fun get(owner: Any, fetch: () -> T?): T? {
        if (!has || owner != this.owner) {
            this.owner = owner
            value = fetch()
            has = true
        }
        return value
    }

    fun set(v: T?) {
        value = v
        has = true
    }

    fun reset() {
        owner = null
        value = null
        has = false
    }
}

/**
 * One pending refresh for a burst of callbacks: the first scheduled one wins ([schedule]); a session
 * destroy moves it back ([pushBack]) so a refresh queued by the player's STOPPED does not land in the
 * gap before the new session exists.
 */
internal class RefreshScheduler(private val post: (Long) -> Unit, private val cancel: () -> Unit) {
    private var pending = false

    fun schedule(delayMs: Long) {
        if (pending) return
        pending = true
        post(delayMs)
    }

    fun pushBack(delayMs: Long) {
        clear()
        schedule(delayMs)
    }

    /** The queued refresh has started. */
    fun fired() { pending = false }

    fun clear() {
        cancel()
        pending = false
    }
}

/** Wall-clock wait until [positionMs] reaches its next whole second at [speed]; a plain second when unknown or stopped. */
internal fun nextWholeSecondDelayMs(positionMs: Long?, speed: Float): Long {
    if (positionMs == null || speed <= 0f) return SECOND_MS
    val toBoundary = SECOND_MS - positionMs % SECOND_MS
    // +1 ms: waking a hair early would floor to the same second again.
    return (toBoundary / speed).toLong().plus(1).coerceIn(MIN_TICK_MS, MAX_TICK_MS)
}

private const val SECOND_MS = 1_000L
private const val MIN_TICK_MS = 50L
private const val MAX_TICK_MS = 4_000L
