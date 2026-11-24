package com.bydmate.app.fork.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * Chooses which GitHub release the updater should offer, honouring the «nightly» (test builds)
 * opt-in. Pure — no Android dependencies, org.json only — so it is unit-tested directly.
 *
 * The version ordering is [Versions.isNewer], the same dotted-int rule the author's
 * `UpdateChecker.isNewer` uses, so stable and nightly builds compare identically. fork-local.
 */
object ReleasePicker {

    /** A chosen release: tag with any leading "v" stripped, the .apk asset url, and the body text. */
    data class Release(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
    )

    /**
     * Nightly OFF: the single release object returned by `GET /releases/latest` (GitHub already
     * excludes prereleases and drafts on that endpoint). Mirrors the author's original parse —
     * throws on malformed JSON or a release that carries no tag / no installable .apk asset.
     */
    fun fromLatest(json: String): Release {
        val release = JSONObject(json)
        val tag = release.optString("tag_name", "").removePrefix("v")
        if (tag.isEmpty()) error("Нет tag_name в ответе GitHub")
        val url = apkUrl(release) ?: error("Нет APK в релизе $tag")
        return Release(tag, url, release.optString("body", ""))
    }

    /**
     * Nightly ON: the array returned by `GET /releases`. Drops `draft == true`, keeps prereleases
     * and stable, and returns the newest (by [Versions.isNewer]) release that carries a .apk asset.
     * Returns null when the array is empty or nothing installable remains, so the caller treats a
     * half-published release as "no update" instead of crashing. Malformed JSON throws (as today).
     */
    fun fromList(json: String): Release? {
        val releases = JSONArray(json)
        var best: Release? = null
        for (i in 0 until releases.length()) {
            val candidate = candidateOf(releases.optJSONObject(i)) ?: continue
            val current = best
            if (current == null || Versions.isNewer(candidate.version, current.version)) {
                best = candidate
            }
        }
        return best
    }

    /** An installable non-draft release with a tag and a .apk asset, or null to skip it. */
    private fun candidateOf(release: JSONObject?): Release? {
        if (release == null || release.optBoolean("draft", false)) return null
        val tag = release.optString("tag_name", "").removePrefix("v")
        if (tag.isEmpty()) return null
        val url = apkUrl(release) ?: return null
        return Release(tag, url, release.optString("body", ""))
    }

    /** First `.apk` asset's download url, or null when the release has no assets / no .apk. */
    private fun apkUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i)
            if (asset != null && asset.optString("name", "").endsWith(".apk")) {
                val url = asset.optString("browser_download_url", "")
                if (url.isNotEmpty()) return url
            }
        }
        return null
    }
}
