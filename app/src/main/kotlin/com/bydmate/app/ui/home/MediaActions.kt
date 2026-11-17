package com.bydmate.app.ui.home

/** A session custom action reduced to what the logic needs (no Android types, so it tests on the JVM). */
data class MediaAction(val action: String, val name: String?, val active: Boolean?, val mode: Int?)

/** Characters after which an exclusion phrase still counts as the start of a word. */
private const val PHRASE_START = " «\"'(_-:."

/** True when [phrase] occurs in [text] at its start or right after a space or one of «"'( . */
private fun hasPhrase(text: String, phrase: String): Boolean {
    var i = text.indexOf(phrase)
    while (i >= 0) {
        if (i == 0 || text[i - 1] in PHRASE_START) return true
        i = text.indexOf(phrase, i + 1)
    }
    return false
}

/**
 * Exact (case-insensitive) [exact] action id first; then the first action whose id or name contains
 * one of [words] (also as a whole word or phrase) and none of [exclude] as a whole word or phrase («Мне нравится» holds «не нравится»
 * as a raw substring, so exclusions never match inside a word).
 */
internal fun pickAction(
    actions: List<MediaAction>,
    exact: String,
    words: List<String>,
    exclude: List<String>,
): MediaAction? {
    actions.firstOrNull { it.action.equals(exact, ignoreCase = true) }?.let { return it }
    val w = words.map { it.lowercase() }
    val x = exclude.map { it.lowercase() }
    return actions.firstOrNull { act ->
        val texts = listOfNotNull(act.action, act.name).map { it.lowercase() }
        texts.any { t -> w.any { hasPhrase(t, it) } } && texts.none { t -> x.any { hasPhrase(t, it) } }
    }
}

internal fun pickLike(actions: List<MediaAction>): MediaAction? =
    pickAction(actions, "like", listOf("like", "нравится"), listOf("dislike", "unlike", "не нравится"))

internal fun pickDislike(actions: List<MediaAction>): MediaAction? =
    pickAction(actions, "dislike", listOf("dislike", "не нравится"), emptyList())

internal fun pickShuffle(actions: List<MediaAction>): MediaAction? =
    pickAction(actions, "shuffle", listOf("shuffle"), emptyList())

internal fun pickRepeat(actions: List<MediaAction>): MediaAction? =
    pickAction(actions, "repeat", listOf("repeat"), emptyList())

/** Is the track liked: the action's `active`, else a guess from its name, else unknown (null). */
internal fun likedOf(a: MediaAction?): Boolean? {
    if (a == null) return null
    a.active?.let { return it }
    val name = a.name?.lowercase() ?: return null
    return when {
        name.startsWith("убрать") || "remove" in name -> true
        "мне нравится" in name || "like" in name -> false
        else -> null
    }
}

/** (liked, shuffleOn, repeatMode) of a session's actions; null where unknown. */
internal fun stateOf(actions: List<MediaAction>): Triple<Boolean?, Boolean?, Int?> =
    Triple(likedOf(pickLike(actions)), pickShuffle(actions)?.active, pickRepeat(actions)?.mode)

enum class OpenPlan { SESSION, LAUNCH, NONE }

internal fun openPlan(hasSessionActivity: Boolean, hasLaunchIntent: Boolean): OpenPlan = when {
    hasSessionActivity -> OpenPlan.SESSION
    hasLaunchIntent -> OpenPlan.LAUNCH
    else -> OpenPlan.NONE
}
