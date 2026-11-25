package com.bydmate.app.fork.cells.motors

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.ui.tech.TechPanelUiState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

private const val TX_GET_INT = 5
private const val DEV_GB = 1039
private const val MIN_C = -39
private const val MAX_C = 150
private const val RPM_NOISE_FLOOR = 10

/**
 * Motor and inverter °C of the Leopard 5, frame 0x4DA, confirmed by the owner's drive log (2026-11-23): 18–45 °C,
 * rising under load. The author's fids (frames 0x44D/0x44E, taken from the Leopard 3) read −40 or 182–209 on this car.
 * getInt only, never a write. Order: front inverter, front motor, rear inverter, rear motor.
 */
internal val MOTOR_TEMP_ITEMS = listOf(
    BatchReadItem(TX_GET_INT, DEV_GB, 1302331648), // Gb.GB_FRONT_MOTOR_IPM_TEMP_DM40 0x4DA00100
    BatchReadItem(TX_GET_INT, DEV_GB, 1302331672), // Gb.GB_FRONT_MOTOR_TEMP_DM40 0x4DA00118
    BatchReadItem(TX_GET_INT, DEV_GB, 1302331748), // Gb.GB_REAR_MOTOR_IPM_TEMP_DM40 0x4DA00164
    BatchReadItem(TX_GET_INT, DEV_GB, 1302331756), // Gb.GB_TREAR_MOTOR_TEMP_DM40 0x4DA0016C
)

data class MotorTemps(
    val inverterFront: Int?,
    val motorFront: Int?,
    val inverterRear: Int?,
    val motorRear: Int?,
)

/** −39..150 is a temperature; −40 (no data), 65535 and everything else is none. */
internal fun motorTempOrNull(raw: Int): Int? = if (raw in MIN_C..MAX_C) raw else null

/**
 * At rest the electric-motor rpm signal dithers (±1 in the drive log), which [abs] would show as a
 * 0↔1 flicker on the card; below [RPM_NOISE_FLOOR] there is no real rotation, so show 0. Magnitude otherwise.
 */
internal fun denoiseMotorRpm(rpm: Int?): Int? = rpm?.let { if (abs(it) < RPM_NOISE_FLOOR) 0 else abs(it) }

interface MotorTempsReader {
    /** Null = the read failed as a whole; a single bad value is a null field. */
    suspend fun read(): MotorTemps?
}

class HelperMotorTempsReader(private val helper: HelperClient) : MotorTempsReader {
    override suspend fun read(): MotorTemps? {
        val reply = helper.readBatch(MOTOR_TEMP_ITEMS) ?: return null
        if (reply.size != MOTOR_TEMP_ITEMS.size) return null
        val v = reply.map { (status, value) -> if (status == 0) motorTempOrNull(value) else null }
        return MotorTemps(inverterFront = v[0], motorFront = v[1], inverterRear = v[2], motorRear = v[3])
    }
}

/** Demo: the demo car's own temperatures; never touches the helper. */
class DemoMotorTempsReader(private val live: StateFlow<DiParsData?>) : MotorTempsReader {
    override suspend fun read(): MotorTemps? = live.value?.let {
        MotorTemps(it.inverterTempFront, it.motorTempFront, it.inverterTempRear, it.motorTempRear)
    }
}

/**
 * The «Моторы» card for the Leopard 5: temperatures from [temps] (a dash while unread), every rpm as a magnitude,
 * with the electric motors denoised so the at-rest dither (±1) reads 0 ([denoiseMotorRpm]). On this car the front
 * motor turns negative forward and the rear negative in reverse (drive log 2026-11-23); the sign says nothing to
 * the driver (owner, 2026-11-23).
 */
internal fun applyLeopard5Motors(state: TechPanelUiState, temps: MotorTemps?): TechPanelUiState = state.copy(
    inverterTempFront = temps?.inverterFront,
    motorTempFront = temps?.motorFront,
    inverterTempRear = temps?.inverterRear,
    motorTempRear = temps?.motorRear,
    motorRpmFront = denoiseMotorRpm(state.motorRpmFront),
    motorRpmRear = denoiseMotorRpm(state.motorRpmRear),
    engineRpm = state.engineRpm?.let(::abs),
)
