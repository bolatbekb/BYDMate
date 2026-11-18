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
