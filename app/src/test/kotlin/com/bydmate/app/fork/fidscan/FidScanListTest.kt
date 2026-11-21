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

    private val serviceCatalog = """
        BYDAutoConstants.BYDAUTO_DEVICE_ADAS=1038
        BYDAutoConstants.BYDAUTO_DEVICE_LOCATION=1017
        BYDAutoConstants.BYDAUTO_DEVICE_OTA=1032
        BYDAutoConstants.BYDAUTO_DEVICE_POWER=1005
        BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1014
        BYDAutoConstants.BYDAUTO_DEVICE_SETTING=1023
        Adas.ADAS_CMD_ADAS_ESP_KEY=1250951194
        Location.LOCATION_LATITUDE_VALUE=311087
        Ota.OTA_CMD_ECU_VER=157571
        Ota.OTA_RECOVERY_STATE_RESET=-1442840302
        Ota.OTA_RECOVERY_STATE_QUERY=-1442840303
        Power.POWER_FREESCALE_RESET=775583
        Statistic.STATISTICS_MCU_LAST_REBOOT_REASON=-1728052709
        Setting.SET_RESTART_STATE=4660
        Setting.SET_STARTUP_SOUND=4661
    """.trimIndent()

    private fun listAll(dump: String) = planFromList(
        dump.lines().filterNot { it.startsWith("BYDAutoConstants.") || it.isBlank() }.joinToString("\n"),
        dump,
    )

    @Test
    fun commandClassAndActionNamedServiceFidsAreSkippedAsCommand() {
        val plan = listAll(serviceCatalog)
        assertEquals(
            listOf(
                "Ota.OTA_CMD_ECU_VER",
                "Ota.OTA_RECOVERY_STATE_RESET",
                "Ota.OTA_RECOVERY_STATE_QUERY",
                "Power.POWER_FREESCALE_RESET",
                "Statistic.STATISTICS_MCU_LAST_REBOOT_REASON",
            ).sorted(),
            plan.skipped.filter { it.reason == ListSkip.COMMAND }.map { it.text }.sorted(),
        )
    }

    @Test
    fun canSignalsAndPlainServiceFidsAreStillRead() {
        val read = listAll(serviceCatalog).items.map { it.symbol }
        assertEquals(
            listOf(true, true, true, false),
            listOf(
                "Location.LOCATION_LATITUDE_VALUE",
                "Adas.ADAS_CMD_ADAS_ESP_KEY", // a CAN signal: its name does not matter
                "Setting.SET_STARTUP_SOUND", // STARTUP is not the word START
                "Power.POWER_FREESCALE_RESET",
            ).map { it in read },
        )
        assertEquals(true, "Setting.SET_RESTART_STATE" in read) // RESTART is not the word RESET
    }

    @Test
    fun setterVariantsAreNotReadAndGetVariantsAre() {
        val dump = """
            BYDAutoConstants.BYDAUTO_DEVICE_AC=1000
            BYDAutoConstants.BYDAUTO_DEVICE_AUDIO=1002
            Ac.AC_COMPRESSOR_MODE_SET_lrse_hal_only=357568570
            Ac.AC_COOL_HOT_SIGNAL_SET_hal_only=411074582
            Ac.AC_SEAT_SET_TV_SCREEN_hal_only=411074583
            Ac.AC_COMPRESSOR_MODE=1077936137
            Ac.AC_LF_SEAT_HEATING_CONFIG_2E4_hal_only=411074584
            Audio.AUDIO_CURRENT_VOLUME_MIN_SET_GET=1078984712
        """.trimIndent()
        val plan = listAll(dump)
        assertEquals(
            listOf("Ac.AC_COMPRESSOR_MODE", "Ac.AC_LF_SEAT_HEATING_CONFIG_2E4_hal_only", "Audio.AUDIO_CURRENT_VOLUME_MIN_SET_GET"),
            plan.items.map { it.symbol },
        )
        assertEquals(
            listOf("Ac.AC_COMPRESSOR_MODE_SET_lrse_hal_only", "Ac.AC_COOL_HOT_SIGNAL_SET_hal_only", "Ac.AC_SEAT_SET_TV_SCREEN_hal_only"),
            plan.skipped.filter { it.reason == ListSkip.SETTER }.map { it.text },
        )
    }

    @Test
    fun anUnsafeAliasLeavesTheFidOutEvenForASafeName() {
        val dump = """
            BYDAutoConstants.BYDAUTO_DEVICE_OTA=1032
            Ota.OTA_ECU_VERSION_TEXT=157571
            Ota.OTA_CMD_ECU_VER=157571
            BYDAutoFeatureIds.OTA_CMD_ECU_VER=157571
            Ota.OTA_PROGRESS=157572
        """.trimIndent()
        val plan = planFromList("Ota.OTA_ECU_VERSION_TEXT=157571\nOta.OTA_PROGRESS=157572", dump)
        assertEquals(listOf(ScanItem(1032, 157572, "Ota.OTA_PROGRESS")), plan.items)
        assertEquals(listOf(Triple(1, "Ota.OTA_ECU_VERSION_TEXT", ListSkip.COMMAND)), skipped(plan))
    }

    @Test
    fun deviceConstantsAreNotFids() {
        val dump = "BYDAutoConstants.BYDAUTO_DEVICE_OTA=1032\nBYDAutoConstants.SOME_START=157572\nOta.OTA_PROGRESS=157572"
        val plan = planFromList("BYDAutoConstants.SOME_START=157572\nOta.OTA_PROGRESS=157572", dump)
        assertEquals(listOf(ScanItem(1032, 157572, "Ota.OTA_PROGRESS")), plan.items)
        assertEquals(listOf(Triple(1, "BYDAutoConstants.SOME_START", ListSkip.UNKNOWN_GROUP)), skipped(plan))
    }

    @Test
    fun setterAliasDoesNotHideItsGetter() {
        val dump = """
            BYDAutoConstants.BYDAUTO_DEVICE_ENGINE=1012
            Engine.ENGINE_X_SET=9
            Engine.ENGINE_X=9
        """.trimIndent()
        val plan = planFromList("Engine.ENGINE_X_SET=9\nEngine.ENGINE_X=9", dump)
        assertEquals(listOf(ScanItem(1012, 9, "Engine.ENGINE_X")), plan.items)
        assertEquals(listOf(Triple(1, "Engine.ENGINE_X_SET", ListSkip.SETTER)), skipped(plan))
    }
}
