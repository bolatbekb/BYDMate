package com.bydmate.app.fork.fidscan

import org.junit.Assert.assertEquals
import org.junit.Test

class FidScanListTest {

    private val catalog = """
        BYDAutoConstants.BYDAUTO_DEVICE_OTA=1032
        BYDAutoConstants.BYDAUTO_DEVICE_POWER=1005
        BYDAutoConstants.BYDAUTO_DEVICE_MQTT=1033
        Power.POWER_BATTERY_VOLTAGE_1=1137704976
        Power.POWER_OTHER=775000
        Ota.OTA_ECU_VERSION_TEXT=157571
        Ota.OTA_CMD_ECU_VER=157571
        Mqtt.MQTT_X=7
    """.trimIndent()

    private fun skipped(p: ListPlan) = p.skipped.map { Triple(it.line, it.text, it.reason) }

    @Test
    fun readsExactLinesInFileOrderWithFileNames() {
        val plan = planFromList(
            "Gb.GB_DYNAMIC_DATA_CALLBACK=-1728053217\n# c\nPower.MY_NAME=42\nPower.POWER_BATTERY_VOLTAGE_1=1137704976\n",
            null,
        )
        assertEquals(
            listOf(
                ScanItem(1039, -1728053217, "Gb.GB_DYNAMIC_DATA_CALLBACK"),
                ScanItem(1005, 42, "Power.MY_NAME"),
                ScanItem(1005, 1137704976, "Power.POWER_BATTERY_VOLTAGE_1"),
            ),
            plan.items,
        )
        assertEquals(emptyList<ListSkipped>(), plan.skipped)
        assertEquals(3, plan.lines)
    }

    @Test
    fun commentsBlanksCrLfBomAndSpacesAroundEqualsAreFine() {
        val plan = planFromList("﻿# c\r\n\r\n  Power.POWER_BATTERY_VOLTAGE_1 = 1137704976 \r\n", null)
        assertEquals(listOf(ScanItem(1005, 1137704976, "Power.POWER_BATTERY_VOLTAGE_1")), plan.items)
        assertEquals(1, plan.lines)
        assertEquals(emptyList<ListSkipped>(), plan.skipped)
    }

    @Test
    fun badLinesAreReportedWithTheirNumber() {
        val plan = planFromList("Power.X\nPower.X=abc\nPOWERX=5\n*.X=5\nGb.*=1", null)
        assertEquals(emptyList<ScanItem>(), plan.items)
        assertEquals(
            listOf(
                Triple(1, "Power.X", ListSkip.BAD_LINE),
                Triple(2, "Power.X=abc", ListSkip.BAD_LINE),
                Triple(3, "POWERX=5", ListSkip.BAD_LINE),
                Triple(4, "*.X=5", ListSkip.BAD_LINE),
                Triple(5, "Gb.*=1", ListSkip.BAD_LINE),
            ),
            skipped(plan),
        )
        assertEquals(5, plan.lines)
    }

    @Test
    fun settersCommandsDiagAndUnknownGroupsAreSkippedWithReasons() {
        val plan = planFromList(
            """
            Ac.AC_COMPRESSOR_MODE_SET_lrse_hal_only=357568570
            Ota.OTA_RECOVERY_STATE_RESET=-1442840302
            Mqtt.MQTT_X=7
            Nope.X=5
            BYDAutoFeatureIds.X=5
            """.trimIndent(),
            null,
        )
        assertEquals(emptyList<ScanItem>(), plan.items)
        assertEquals(
            listOf(
                Triple(1, "Ac.AC_COMPRESSOR_MODE_SET_lrse_hal_only", ListSkip.SETTER),
                Triple(2, "Ota.OTA_RECOVERY_STATE_RESET", ListSkip.COMMAND),
                Triple(3, "Mqtt.MQTT_X", ListSkip.DIAG_DEVICE),
                Triple(4, "Nope.X", ListSkip.UNKNOWN_GROUP),
                Triple(5, "BYDAutoFeatureIds.X", ListSkip.UNKNOWN_GROUP),
            ),
            skipped(plan),
        )
    }

    @Test
    fun withCatalogAFidMissingFromTheCarIsNotInCar() {
        val text = "Power.POWER_BATTERY_VOLTAGE_1=1137704976\nPower.POWER_GONE=999"
        val with = planFromList(text, catalog)
        assertEquals(listOf(ScanItem(1005, 1137704976, "Power.POWER_BATTERY_VOLTAGE_1")), with.items)
        assertEquals(listOf(Triple(2, "Power.POWER_GONE", ListSkip.NOT_IN_CAR)), skipped(with))
        assertEquals(true, with.withCatalog)
    }

    @Test
    fun withoutCatalogItIsRead() {
        val text = "Power.POWER_BATTERY_VOLTAGE_1=1137704976\nPower.POWER_GONE=999"
        val without = planFromList(text, null)
        assertEquals(2, without.items.size)
        assertEquals(emptyList<ListSkipped>(), without.skipped)
        assertEquals(false, without.withCatalog)
    }

    @Test
    fun anUnsafeCatalogAliasMakesTheLineCommand() {
        val plan = planFromList("Ota.OTA_ECU_VERSION_TEXT=157571", catalog)
        assertEquals(emptyList<ScanItem>(), plan.items)
        assertEquals(listOf(Triple(1, "Ota.OTA_ECU_VERSION_TEXT", ListSkip.COMMAND)), skipped(plan))
    }

    @Test
    fun duplicateFidIsReadOnce() {
        val plan = planFromList("Power.A_NAME=5\nPower.B_NAME=5", null)
        assertEquals(listOf(ScanItem(1005, 5, "Power.A_NAME")), plan.items)
        assertEquals(emptyList<ListSkipped>(), plan.skipped)
        assertEquals(2, plan.lines)
    }

    @Test
    fun groupsResolveLikeTheAuthor() {
        val groups = listOf("Bigdata" to 1061, "Gb" to 1039, "Door" to 1041, "Rear" to 1047, "Pm2p5" to 1008, "Safety" to 1042)
        val plan = planFromList(groups.joinToString("\n") { "${it.first}.X_NAME=1" }, null)
        assertEquals(emptyList<ListSkipped>(), plan.skipped)
        assertEquals(groups.map { it.second }, plan.items.map { it.dev })
    }

    @Test
    fun devicesAreDistinctAscending() {
        val plan = planFromList("Gb.A_X=1\nPower.A_X=2\nGb.B_X=3\nBigdata.A_X=4", null)
        assertEquals(listOf(1005, 1039, 1061), plan.devices)
    }

    @Test
    fun listSkipLinesFormat() {
        val plan = planFromList(
            "Power.X\nPower.X=abc\nAc.AC_COMPRESSOR_MODE_SET_lrse_hal_only=357568570\n" +
                "Ota.OTA_RECOVERY_STATE_RESET=-1442840302\nMqtt.MQTT_X=7\nNope.X=5\n",
            null,
        )
        assertEquals(
            listOf(
                "# list skipped bad_line: 2 1:Power.X,2:Power.X=abc",
                "# list skipped setter: 1 Ac.AC_COMPRESSOR_MODE_SET_lrse_hal_only",
                "# list skipped unknown_group: 1 Nope.X",
                "# list skipped diag_device: 1 Mqtt.MQTT_X",
                "# list skipped command: 1 Ota.OTA_RECOVERY_STATE_RESET",
            ),
            listSkipLines(plan),
        )
    }
}
