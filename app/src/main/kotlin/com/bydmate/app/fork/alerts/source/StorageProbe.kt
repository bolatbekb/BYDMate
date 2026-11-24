package com.bydmate.app.fork.alerts.source

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager

/**
 * One mounted volume as the alert needs it: a stable [id], a human [label] (the system description,
 * e.g. «Внутренняя память» or the card's name), whether it is [isRemovable] (a card — the dashcam's
 * when the head unit says so), and its size and free space in bytes.
 */
data class VolumeInfo(
    val id: String,
    val label: String,
    val isRemovable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    /** Free space as a percentage of the total; 0 when the total is unknown. */
    val freePercent: Double get() = if (totalBytes > 0) freeBytes.toDouble() / totalBytes * PERCENT else 0.0

    private companion object {
        const val PERCENT = 100.0
    }
}

/** Lists the mounted volumes. A seam so the source can be tested without the framework. */
interface StorageProbe {
    fun volumes(): List<VolumeInfo>
}

/**
 * Reads the real volumes. Enumerates them through [Context.getExternalFilesDirs] (every mounted
 * volume, internal and removable, since API 19) and takes each one's size with [StatFs]; the
 * matching [android.os.storage.StorageVolume] gives the label and the removable flag. Each volume
 * is read defensively — one that throws is skipped, never the whole list.
 */
class AndroidStorageProbe(private val context: Context) : StorageProbe {
    override fun volumes(): List<VolumeInfo> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return emptyList()
        return context.getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { dir -> runCatching { read(sm, dir) }.getOrNull() }
            .distinctBy { it.id }
    }

    private fun read(sm: StorageManager, dir: java.io.File): VolumeInfo? {
        val stat = StatFs(dir.path)
        val total = stat.totalBytes
        if (total <= 0) return null
        val volume = runCatching { sm.getStorageVolume(dir) }.getOrNull()
        val id = volume?.uuid ?: if (volume?.isPrimary == true) "primary" else dir.path
        val label = runCatching { volume?.getDescription(context) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: if (volume?.isRemovable == true) "SD" else "storage"
        return VolumeInfo(
            id = id,
            label = label,
            isRemovable = volume?.isRemovable ?: false,
            totalBytes = total,
            freeBytes = stat.availableBytes,
        )
    }
}
