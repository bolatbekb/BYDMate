package com.bydmate.app.fork.fidscan

import com.bydmate.app.helper.push.FID_PUSH_DEVICE_CLASSES
import com.bydmate.app.helper.push.MAX_REC_DEVICES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FidScanCoreTest {

    private val catalog = """
        BYDAutoConstants.BYDAUTO_DEVICE_ENGINE=1012
        BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1014
        BYDAutoConstants.BYDAUTO_DEVICE_MQTT=1033
        Engine.ENGINE_ENGINE_COOLANT_TEMPERATURE=1320181824
        Engine.ENGINE_DRIVE_FORCE_ADJUST_SET=5
        Statistic.STATISTIC_WATER_TEMPERATURE=839263
        Mqtt.MQTT_X=7
        BYDAutoFeatureIds.ENGINE_ENGINE_COOLANT_TEMPERATURE=1320181824
    """.trimIndent()

    @Test
    fun scanListKeepsReadableSymbolsOnly() {
        assertEquals(
            listOf(
                ScanItem(1012, 1320181824, "Engine.ENGINE_ENGINE_COOLANT_TEMPERATURE"),
                ScanItem(1014, 839263, "Statistic.STATISTIC_WATER_TEMPERATURE"),
            ),
            scanList(catalog),
        )
    }

    /** Real names and fids of the owner's Leopard 5 dump (2026-11-19). */
    private val safetyCatalog = """
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

    @Test
    fun commandClassAndActionNamedServiceFidsAreNotRead() {
        val plan = scanPlan(safetyCatalog)
        assertEquals(
            listOf(
                "Ota.OTA_CMD_ECU_VER",
                "Ota.OTA_RECOVERY_STATE_RESET",
                "Ota.OTA_RECOVERY_STATE_QUERY",
                "Power.POWER_FREESCALE_RESET",
                "Statistic.STATISTICS_MCU_LAST_REBOOT_REASON",
            ).sorted(),
            plan.unsafe.map { it.symbol }.sorted(),
        )
        assertEquals(plan.items, scanList(safetyCatalog))
    }

    @Test
    fun canSignalsAndPlainServiceFidsAreStillRead() {
        assertEquals(
            listOf(
                "Power.POWER_FREESCALE_RESET" to false,
                "Location.LOCATION_LATITUDE_VALUE" to true,
                "Adas.ADAS_CMD_ADAS_ESP_KEY" to true, // a CAN signal: its name does not matter
                "Setting.SET_RESTART_STATE" to true, // RESTART is not the word RESET
                "Setting.SET_STARTUP_SOUND" to true, // STARTUP is not the word START
            ),
            listOf(
                "Power.POWER_FREESCALE_RESET",
                "Location.LOCATION_LATITUDE_VALUE",
                "Adas.ADAS_CMD_ADAS_ESP_KEY",
                "Setting.SET_RESTART_STATE",
                "Setting.SET_STARTUP_SOUND",
            ).map { name -> name to scanList(safetyCatalog).any { it.symbol == name } },
        )
    }

    @Test
    fun unsafeReadRule() {
        assertTrue(isUnsafeRead(0xAA401018.toInt(), "CHARGING_HEV_VTOV_CONFIG")) // command class, any name
        assertTrue(isUnsafeRead(0x99000006.toInt(), "YUN_SEND_CMD"))
        assertTrue(isUnsafeRead(0x20328, "SET_MMS_START_F"))
        assertTrue(isUnsafeRead(0xCAF3B, "OTA_CMD_ECU_GET_FAULTCODE"))
        assertFalse(isUnsafeRead(0x99000021.toInt(), "YUN_DYNAMIC_DATA_CALLBACK"))
        assertFalse(isUnsafeRead(CAN_SIGNAL_MIN_FID, "X_RESET"))
        assertTrue(isUnsafeRead(CAN_SIGNAL_MIN_FID - 1, "X_RESET"))
        assertFalse(isUnsafeRead(311087, "LOCATION_LATITUDE_VALUE"))
        assertTrue(isUnsafeRead(0x99000171.toInt(), "AUDIO_IVI_SET_MUSIC_INFO"))
        assertFalse(isUnsafeRead(0xC0700041.toInt(), "SET_HAS_OVERSPEED_LOCKING"))
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
        val plan = scanPlan(dump)
        assertEquals(
            listOf("Ac.AC_COMPRESSOR_MODE", "Ac.AC_LF_SEAT_HEATING_CONFIG_2E4_hal_only", "Audio.AUDIO_CURRENT_VOLUME_MIN_SET_GET"),
            plan.items.map { it.symbol },
        )
        assertEquals(emptyList<ScanItem>(), plan.unsafe)
        assertTrue(isSetterName("Ac.AC_X_SET"))
        assertFalse(isSetterName("Ac.AC_X_SETTING_hal_only"))
    }

    @Test
    fun anUnsafeAliasLeavesTheFidOutEvenWhenTheDedupKeptASafeName() {
        val dump = """
            BYDAutoConstants.BYDAUTO_DEVICE_OTA=1032
            Ota.OTA_ECU_VERSION_TEXT=157571
            Ota.OTA_CMD_ECU_VER=157571
            BYDAutoFeatureIds.OTA_CMD_ECU_VER=157571
            Ota.OTA_PROGRESS=157572
        """.trimIndent()
        assertEquals(listOf(ScanItem(1032, 157572, "Ota.OTA_PROGRESS")), scanList(dump))
        assertEquals(listOf(ScanItem(1032, 157571, "Ota.OTA_ECU_VERSION_TEXT")), scanPlan(dump).unsafe)
    }

    @Test
    fun deviceConstantsAreNotFids() {
        val dump = """
            BYDAutoConstants.BYDAUTO_DEVICE_OTA=1032
            BYDAutoConstants.SOME_START=157572
            Ota.OTA_PROGRESS=157572
        """.trimIndent()
        assertEquals(listOf(ScanItem(1032, 157572, "Ota.OTA_PROGRESS")), scanList(dump))
    }

    @Test
    fun setAliasListedBeforeTheGetterDoesNotHideIt() {
        val dump = """
            BYDAutoConstants.BYDAUTO_DEVICE_ENGINE=1012
            Engine.ENGINE_X_SET=9
            Engine.ENGINE_X=9
        """.trimIndent()
        assertEquals(listOf(ScanItem(1012, 9, "Engine.ENGINE_X")), scanList(dump))
    }

    @Test
    fun pushDevicesAreTheKnownDevicesWithoutMqtt() {
        val devs = pushDevices().toList()
        assertFalse(EXCLUDED_DEVICE in devs)
        assertEquals(FID_PUSH_DEVICE_CLASSES.keys.filter { it != EXCLUDED_DEVICE }.sorted(), devs)
        assertTrue(devs.isNotEmpty() && devs.size <= MAX_REC_DEVICES)
    }

    @Test
    fun oneLineReplacesLineBreaksWithSpaces() {
        assertEquals("a b  c d", oneLine("a\nb\r\nc\rd"))
        assertEquals("plain", oneLine("plain"))
    }

    @Test
    fun scanListOfEmptyDumpIsEmpty() {
        assertTrue(scanList("").isEmpty())
    }

    @Test
    fun scanModeRules() {
        assertEquals(ScanMode.UNKNOWN, scanMode(null))
        assertEquals(ScanMode.CHARGE_AC, scanMode(di(chargeGunState = 2, powerState = 0)))
        assertEquals(ScanMode.CHARGE_DC, scanMode(di(chargeGunState = 4)))
        assertEquals(ScanMode.CHARGE_DC, scanMode(di(chargeGunState = 3)))
        assertEquals(ScanMode.V2L, scanMode(di(chargeGunState = 5)))
        assertEquals(ScanMode.OFF, scanMode(di(chargeGunState = 1, powerState = 0)))
        assertEquals(ScanMode.DRIVE, scanMode(di(gear = 4)))
        assertEquals(ScanMode.DRIVE, scanMode(di(gear = 2)))
        assertEquals(ScanMode.DRIVE, scanMode(di(gear = 1, speed = 3)))
        assertEquals(ScanMode.PARK, scanMode(di(gear = 1, speed = 0, powerState = 1)))
    }

    @Test
    fun readingTextPrintsValuesAndStatuses() {
        assertEquals("i=65535 d=1.5", readingText(Reading(0, 65535, 0, 1.5f.toRawBits())))
        assertEquals("i=!-999 d=!3", readingText(Reading(-999, 0, 3, 0)))
    }

    @Test
    fun valueLineFormat() {
        val item = ScanItem(1012, 1, "Engine.X")
        assertEquals("V 1012 Engine.X i=7 d=1.5", valueLine(item, Reading(0, 7, 0, 1.5f.toRawBits())))
    }

    @Test
    fun stateLineDashesForMissingData() {
        assertEquals(
            "S 15:00:00 mode=PARK gear=- speed=- soc=- gun=- rpm=- chg=- bms=- power=- odo=-",
            stateLine("15:00:00", ScanMode.PARK, null),
        )
    }

    @Test
    fun stateLineWithData() {
        val d = di(
            soc = 55, speed = 3, mileage = 1234.5, chargeGunState = 1, gear = 4, powerState = 2,
            engineRpm = 1500, chargingStatus = 0, bmsState = 2,
        )
        assertEquals(
            "S 15:00:00 mode=DRIVE gear=4 speed=3 soc=55 gun=1 rpm=1500 chg=0 bms=2 power=2 odo=1234.5",
            stateLine("15:00:00", ScanMode.DRIVE, d),
        )
    }

    @Test
    fun sweepDueRule() {
        assertTrue(sweepDue(0, null, false))
        assertFalse(sweepDue(299_999, 0, false))
        assertTrue(sweepDue(300_000, 0, false))
        assertFalse(sweepDue(59_999, 0, true))
        assertTrue(sweepDue(60_000, 0, true))
    }
}
