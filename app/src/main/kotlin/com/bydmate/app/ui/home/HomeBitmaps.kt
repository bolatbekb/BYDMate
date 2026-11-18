package com.bydmate.app.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The big retro-home images (the C-Drive backplate, the cassette faces and hubs) decoded once on
 * Dispatchers.IO and kept for the chosen home style only. Nothing here reads their pixels, so they
 * are decoded as HARDWARE bitmaps: no native copy of the 11.8 MB backplate. Until an image has
 * arrived the screen shows a plain frame (see CdHome, CassetteDoor).
 */
object HomeBitmaps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = mutableStateMapOf<Int, ImageBitmap>()

    /** The images [style] draws; every other style needs none of these. */
    fun idsFor(style: HomeStyle): Set<Int> = when (style) {
        HomeStyle.CDRIVE -> setOf(com.bydmate.app.R.drawable.cd_base)
        HomeStyle.KENWOOD, HomeStyle.ONKYO -> cassetteIds(CassetteRotation.current)
        else -> emptySet()
    }

    internal fun cassetteIds(c: PhotoCassette): Set<Int> =
        setOfNotNull(c.face, c.hubLImage, c.hubRImage, c.layers?.back)

    /** Drops what [style] does not need and starts decoding what it does. */
    fun preload(context: Context, style: HomeStyle) {
        val app = context.applicationContext
        val want = idsFor(style)
        cache.keys.toList().filter { it !in want }.forEach { cache.remove(it) }
        want.forEach { id -> if (id !in cache) scope.launch { load(app, id) } }
    }

    internal fun peek(@DrawableRes id: Int): ImageBitmap? = cache[id]

    internal suspend fun load(context: Context, @DrawableRes id: Int) {
        if (id in cache) return
        val bmp = decodeWithRetry(RETRY_MS) { withContext(Dispatchers.IO) { decode(context, id) } } ?: return
        // A style change while decoding must not resurrect an evicted image.
        if (id in idsFor(HomeStyleState.current)) cache[id] = bmp
    }

    private const val RETRY_MS = 1_000L

    private fun decode(context: Context, id: Int): ImageBitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.HARDWARE }
        (BitmapFactory.decodeResource(context.resources, id, o)
            ?: BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false }))
            ?.asImageBitmap()
    }.getOrNull()
}

/** The cached image for [id], or null while it is being decoded off the main thread. */
@Composable
internal fun rememberHomeBitmap(@DrawableRes id: Int): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val hit = HomeBitmaps.peek(id)
    if (hit == null) LaunchedEffect(id) { HomeBitmaps.load(context, id) }
    return hit
}

/** A failed decode (native allocation under memory pressure) is tried once more after [retryDelayMs]. */
internal suspend fun <T : Any> decodeWithRetry(retryDelayMs: Long, decode: suspend () -> T?): T? =
    decode() ?: run {
        delay(retryDelayMs)
        decode()
    }
