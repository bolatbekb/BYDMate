package com.bydmate.app.fork.update

import android.util.Log
import com.bydmate.app.service.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Silently downloads a release APK into the [UpdateApkStore] over any network, with no visible
 * system notification (direct streaming, not DownloadManager). Deletes the previous APK first via
 * [UpdateApkStore.begin] and promotes the finished file via [UpdateApkStore.complete], so only one
 * file is ever kept. fork-local.
 */
class BackgroundUpdateDownloader(
    private val httpClient: OkHttpClient,
    private val store: UpdateApkStore,
) {
    companion object {
        private const val TAG = "BgUpdateDownloader"
    }

    /** Downloads [update] into the cache. Throws on any failure; the caller swallows it. */
    suspend fun download(update: UpdateChecker.UpdateInfo) {
        withContext(Dispatchers.IO) {
            val part = store.begin(update.version)
            val request = Request.Builder()
                .url(update.downloadUrl)
                .header("User-Agent", "BYDMate-UpdateCheck")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    part.delete()
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: run {
                    part.delete()
                    throw IOException("empty body")
                }
                part.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            store.complete(update.version)
            Log.i(TAG, "downloaded ${update.version} into cache")
        }
    }
}
