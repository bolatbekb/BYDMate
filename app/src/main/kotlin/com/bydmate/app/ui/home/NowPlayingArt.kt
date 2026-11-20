package com.bydmate.app.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.net.Uri
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/*
 * Album art a player publishes only as a URI (Media3 `setArtworkUri` reaches the framework
 * metadata as ALBUM_ART_URI / ART_URI / DISPLAY_ICON_URI): which source to use, how to load it,
 * and what to log about it without ever logging the URI itself (it may carry tokens).
 */

private val BITMAP_KEYS = listOf(
    MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON,
)
private val URI_KEYS = listOf(
    MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
)

/** The art source of a track: the first bitmap key present, else the first URI; plus what was there (for the log). */
internal data class ArtPick(
    val bitmapKey: String?,
    val uriKey: String?,
    val uri: String?,
    val bitmapKeys: List<String>,
    /** URI key to its scheme only. */
    val uriSchemes: List<Pair<String, String>>,
)

internal fun pickArt(md: MediaMetadata): ArtPick {
    val bitmaps = BITMAP_KEYS.filter { md.getBitmap(it) != null }
    val uris = URI_KEYS.mapNotNull { k -> md.getString(k)?.takeIf { it.isNotBlank() }?.let { k to it } }
    val bitmapKey = bitmaps.firstOrNull()
    val first = if (bitmapKey == null) uris.firstOrNull() else null
    return ArtPick(bitmapKey, first?.first, first?.second, bitmaps, uris.map { (k, u) -> k to schemeOf(u) })
}

/** Besides letters and digits, what a URI scheme may contain (RFC 3986). */
private const val SCHEME_MARKS = "+.-"

/** The scheme of [uri], lower case; "?" when it has none. */
internal fun schemeOf(uri: String): String {
    val i = uri.indexOf(':')
    if (i <= 0) return "?"
    val s = uri.substring(0, i).lowercase()
    return if (s.first().isLetter() && s.all { it.isLetterOrDigit() || it in SCHEME_MARKS }) s else "?"
}

internal enum class UriKind { LOCAL, HTTP, UNSUPPORTED }

internal fun uriKind(uri: String): UriKind = when (schemeOf(uri)) {
    "content", "android.resource", "file" -> UriKind.LOCAL
    "http", "https" -> UriKind.HTTP
    else -> UriKind.UNSUPPORTED
}

private fun shortKey(key: String) = key.removePrefix("android.media.metadata.")

/** One diagnostic line: the package, the art keys present (URIs by scheme only) and the outcome. */
internal fun artLogLine(pkg: String, pick: ArtPick, outcome: String): String =
    "art pkg=$pkg bitmaps=${pick.bitmapKeys.map(::shortKey)} " +
        "uris=${pick.uriSchemes.map { (k, s) -> "${shortKey(k)}:$s" }} -> $outcome"

/**
 * URI art of the current track only: [request] says whether to start a load (once per key),
 * wait, use the result or give up (a failed load is not retried for that key; a new key is).
 * A result for a key that is no longer current is dropped.
 */
internal class UriArtState {
    enum class Step { START, PENDING, READY, FAILED }

    private var key: String? = null
    private var step = Step.PENDING
    private var bitmap: Bitmap? = null

    fun request(key: String): Step {
        if (key != this.key) {
            this.key = key
            bitmap = null
            step = Step.PENDING
            return Step.START
        }
        return step
    }

    fun done(key: String, result: Bitmap?) {
        if (key != this.key) return
        bitmap = result
        step = if (result != null) Step.READY else Step.FAILED
    }

    /** A load for [key] was abandoned (not failed): the next [request] starts it again. */
    fun forget(key: String) {
        if (key == this.key) this.key = null
    }

    fun bitmap(key: String): Bitmap? = if (key == this.key && step == Step.READY) bitmap else null
}

/** A loaded cover, or why there is none (a short reason, never the URI or an exception message). */
internal class ArtLoad(val bitmap: Bitmap?, val reason: String)

private const val MAX_BYTES = 4 * 1024 * 1024
private const val TIMEOUT_MS = 5_000
private const val MAX_REDIRECTS = 3

/** Loads [uri] and scales it to ≤ [ART_MAX_PX]. Blocking: call it off the main thread. */
@Suppress("TooGenericExceptionCaught") // why: the URI comes from any third-party player; whatever its provider throws must end as «uri failed», never a crash
internal fun loadArtUri(ctx: Context, uri: String): ArtLoad = try {
    val bytes = when (uriKind(uri)) {
        UriKind.LOCAL -> ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { readCapped(it) }
        UriKind.HTTP -> fetch(uri)
        UriKind.UNSUPPORTED -> return ArtLoad(null, "uri failed: scheme ${schemeOf(uri)}")
    }
    when {
        bytes == null -> ArtLoad(null, "uri failed: no data")
        bytes.isEmpty() -> ArtLoad(null, "uri failed: too large")
        else -> decodeScaled(bytes)?.let { ArtLoad(it, "uri ok") } ?: ArtLoad(null, "uri failed: decode")
    }
} catch (e: HttpStatus) {
    ArtLoad(null, "uri failed: ${e.message}")
} catch (e: Exception) {
    ArtLoad(null, "uri failed: ${e.javaClass.simpleName}")
}

/** The bytes of [stream], or an empty array when it is over [MAX_BYTES]. */
private fun readCapped(stream: InputStream): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (true) {
        val n = stream.read(buf)
        if (n < 0) break
        if (out.size() + n > MAX_BYTES) return ByteArray(0)
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** GET with 5 s timeouts, following up to 3 redirects (also http ⇄ https, which the JDK will not). */
private fun fetch(uri: String): ByteArray? {
    var url = URL(uri)
    repeat(MAX_REDIRECTS + 1) {
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.instanceFollowRedirects = true
            val code = c.responseCode
            if (code in 300..399) {
                url = redirectTarget(c, url)
                return@repeat
            }
            if (code != HttpURLConnection.HTTP_OK) throw HttpStatus(code)
            if (c.contentLengthLong > MAX_BYTES) return ByteArray(0)
            return c.inputStream.use { readCapped(it) }
        } finally {
            c.disconnect()
        }
    }
    throw java.io.IOException("redirects")
}

/** Where the 3xx answer of [c] points, resolved against [url]. */
private fun redirectTarget(c: HttpURLConnection, url: URL): URL =
    URL(url, c.getHeaderField("Location") ?: throw java.io.IOException("redirect"))

/** A non-200 answer; the log shows only its code. */
private class HttpStatus(code: Int) : java.io.IOException("http $code")

/** Decodes [bytes] sampled down close to [ART_MAX_PX] first (bounds pass), then [scaleArt]. */
private fun decodeScaled(bytes: ByteArray, max: Int = ART_MAX_PX): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / (sample * 2) >= max) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    return scaleArt(bmp, max)
}
