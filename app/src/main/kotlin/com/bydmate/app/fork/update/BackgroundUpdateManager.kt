package com.bydmate.app.fork.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.service.UpdateChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** App-open decision plus the ready APK it refers to (null when nothing is ready). */
data class UpdateDecision(val offer: UpdateOffer, val ready: ReadyApk?)

/**
 * Orchestrates the background update: splits the author's «download + install» into «silently
 * download in the background» and «later offer or notify, gated by park state». Reuses the
 * author's version check ([UpdateChecker.checkForUpdate], `ForkConfig.UPDATE_REPO`) untouched —
 * only the step after it is new. @Singleton so [notifyGate] (once per launch) and the single-file
 * store live for the whole process. fork-local.
 */
@Singleton
class BackgroundUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
    httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "BgUpdateManager"
    }

    private val store = UpdateApkStore.forContext(context)
    private val downloader = BackgroundUpdateDownloader(httpClient, store)
    private val parkReader = ParkStateReader()
    private val notifyGate = LaunchNotifyGate()

    val currentVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")

    /**
     * Check GitHub (the author's throttled check) and, if a genuinely newer release exists,
     * download its APK silently into the cache over any network. Throws on failure; the caller
     * (the gate) swallows it — an update that cannot download must never surface an error.
     */
    suspend fun refresh() {
        val info = updateChecker.checkForUpdate(context) ?: return
        Log.i(TAG, "newer release ${info.version} — downloading in background")
        downloader.download(info)
    }

    /**
     * What to show at app open: reads the current ready APK (dropping an already-installed one)
     * and the live park state (gear P AND speed 0), then [UpdateOfferDecider.decide]s. In demo
     * mode a fake ready APK is used so both screens can be looked at on an emulator.
     */
    fun decide(): UpdateDecision {
        BackgroundUpdateDemo.decision(context)?.let { return it }
        val ready = store.readyApk(currentVersion)
        val parked = parkReader.isParked()
        return UpdateDecision(UpdateOfferDecider.decide(ready != null, parked), ready)
    }

    /** True at most once per launch — gates the «установить можно на стоянке» notice. */
    fun shouldNotifyThisLaunch(): Boolean = notifyGate.shouldNotify()

    /**
     * Launch the system installer for [ready] via the existing ACTION_VIEW path. The caller only
     * calls this on [UpdateOffer.OFFER], i.e. when parked; the installer itself is modal, so we
     * never force-interrupt once it is up. Demo never installs — it only draws the prompt.
     */
    fun install(ready: ReadyApk) {
        if (DemoMode.isOn(context)) {
            Log.i(TAG, "demo: skipping real install of ${ready.version}")
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
