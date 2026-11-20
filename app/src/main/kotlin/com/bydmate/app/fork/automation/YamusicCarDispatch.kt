package com.bydmate.app.fork.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.bydmate.app.R
import com.bydmate.app.data.automation.DispatchResult
import com.bydmate.app.util.AppStrings

// Fork: running the «Я.Музыка Car» action.

/** Runs the action: not installed or a bad command/ID is a failed result, the rest goes through [start]. */
internal fun dispatchYamusicCar(
    payload: String?,
    context: Context,
    strings: AppStrings,
    start: (Intent, String) -> DispatchResult,
): DispatchResult {
    val parsed = YamusicCarPayload.fromPayload(payload)
    val uri = parsed?.uri() ?: return DispatchResult(false, strings.get(R.string.yamusic_car_invalid))
    if (!isYamusicCarInstalled(context)) return DispatchResult(false, strings.get(R.string.yamusic_car_not_installed))
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .setPackage(YAMUSIC_CAR_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // The label never carries an ID value: only the command.
    return start(intent, "yamusic_car:${parsed.command}")
}

private fun isYamusicCarInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(YAMUSIC_CAR_PACKAGE, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
