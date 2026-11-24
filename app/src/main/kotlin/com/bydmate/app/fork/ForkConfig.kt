package com.bydmate.app.fork

/**
 * Settings that make this build the fork's, kept out of the author's files so upstream
 * merges touch them as little as possible.
 */
object ForkConfig {
    /** GitHub "owner/repo" whose latest release the in-app updater offers. */
    const val UPDATE_REPO = "bolatbekb/BYDMate"

    /** The author's donation prompt never pops up on its own; it stays reachable from Settings. */
    const val DONATION_PROMPT = false

    /**
     * Updates download silently in the background and install is offered only when parked. When
     * true, the author's inline «download + install» dialog on app open is disabled (manual check
     * in Settings stays); the fork's `ForkUpdateGate` drives the flow instead.
     */
    const val BACKGROUND_UPDATE = true
}
