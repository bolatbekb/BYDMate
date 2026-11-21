package com.bydmate.app.fork.fidscan

import com.bydmate.app.helper.push.FID_PUSH_DEVICE_CLASSES
import com.bydmate.app.helper.push.groupCatalogByDevice

// fork: the fid scan plan from a list file the owner picks: exactly the listed `Group.NAME=fid`
// lines are read (fid and name from the file), minus what is never read (setters, command-like
// fids, the diagnostic device) and, when the car's catalog dump is known, what the car lacks.
// Pure: no Android, no IO. Every skipped line carries its number and reason for the log.

internal const val MAX_LIST_BYTES = 1024 * 1024
private const val BAD_LINE_TEXT_MAX = 80
private const val DEVICE_LINE = "BYDAutoConstants.BYDAUTO_DEVICE_"
private val LIST_NAME = Regex("^[A-Za-z][A-Za-z0-9]*\\.[A-Za-z0-9_]+$")

enum class ListSkip { BAD_LINE, SETTER, UNKNOWN_GROUP, DIAG_DEVICE, COMMAND, NOT_IN_CAR }

/** [line] is 1-based; [text] is `Group.NAME` (a bad line: the trimmed raw line, cut to 80 chars). */
data class ListSkipped(val line: Int, val text: String, val reason: ListSkip)

internal data class ListPlan(
    val items: List<ScanItem>,
    val skipped: List<ListSkipped>,
    val lines: Int,
    val withCatalog: Boolean,
) {
    /** Devices the push recorder has to listen to. */
    val devices: List<Int> get() = items.map { it.dev }.distinct().sorted()
}

internal fun planFromList(listText: String, catalogDump: String?): ListPlan {
    val rules = ListRules(catalogDump)
    val items = LinkedHashMap<Pair<Int, Int>, ScanItem>()
    val skipped = ArrayList<ListSkipped>()
    var lines = 0
    listText.removePrefix("\uFEFF").lines().forEachIndexed { index, raw ->
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith("#")) return@forEachIndexed
        lines++
        val name = text.substringBeforeLast('=', "").trim()
        val fid = text.substringAfterLast('=', "").trim().toIntOrNull()
        val bad = fid == null || !LIST_NAME.matches(name)
        val dev = if (bad) null else rules.device(name.substringBefore('.'))
        val reason = if (bad) ListSkip.BAD_LINE else rules.reason(name, fid!!, dev)
        if (reason != null) {
            skipped += ListSkipped(index + 1, if (bad) text.take(BAD_LINE_TEXT_MAX) else name, reason)
        } else {
            items.putIfAbsent(dev!! to fid!!, ScanItem(dev, fid, name))
        }
    }
    return ListPlan(items.values.toList(), skipped, lines, catalogDump != null)
}

/** The checks of one list against the car's catalog dump (null: the built-in device table only). */
private class ListRules(private val catalogDump: String?) {
    private val deviceLines = catalogDump?.lineSequence()?.map { it.trim() }?.filter { it.startsWith(DEVICE_LINE) }
        ?.joinToString("\n") ?: builtInDeviceLines()
    private val inCar = catalogDump?.let { dump ->
        groupCatalogByDevice(dump).mapValues { e -> e.value.map { it.fid }.toSet() }
    }
    private val unsafe = catalogDump?.let(::unsafeFids).orEmpty()
    private val groupDevice = HashMap<String, Int?>()

    fun device(group: String): Int? = groupDevice.getOrPut(group) { resolveDevice(group, deviceLines) }

    /** Why a well-formed line is not read, or null when it is. */
    fun reason(name: String, fid: Int, dev: Int?): ListSkip? = when {
        isSetterName(name) -> ListSkip.SETTER
        dev == null -> ListSkip.UNKNOWN_GROUP
        dev == EXCLUDED_DEVICE -> ListSkip.DIAG_DEVICE
        isUnsafeRead(fid, name.substringAfter('.')) || fid in unsafe -> ListSkip.COMMAND
        inCar != null && fid !in inCar[dev].orEmpty() -> ListSkip.NOT_IN_CAR
        else -> null
    }
}

/** The device of [group] by the author's rule (groupCatalogByDevice), or null for an unknown group. */
private fun resolveDevice(group: String, deviceLines: String): Int? =
    if (group == "BYDAutoConstants" || group == "BYDAutoFeatureIds") null
    else groupCatalogByDevice("$deviceLines\n$group.X=1").keys.singleOrNull()

/** `BYDAutoConstants.BYDAUTO_DEVICE_<NAME>=<id>` for every push device, when the car's dump is unknown. */
private fun builtInDeviceLines(): String = FID_PUSH_DEVICE_CLASSES.entries.joinToString("\n") { (dev, cls) ->
    val short = cls.substringAfterLast('.').removePrefix("BYDAuto").removeSuffix("Device").uppercase()
    "$DEVICE_LINE$short=$dev"
}

/** One log line per reason present, in enum order: `# list skipped <reason>: <count> <text>,...`. */
internal fun listSkipLines(plan: ListPlan): List<String> =
    plan.skipped.groupBy { it.reason }.toSortedMap().map { (reason, list) ->
        val texts = list.joinToString(",") { if (reason == ListSkip.BAD_LINE) "${it.line}:${it.text}" else it.text }
        "# list skipped ${reason.name.lowercase()}: ${list.size} $texts"
    }
