package com.runtrack.sensors

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SensorUiState(
    val accel: FloatArray = FloatArray(3),
    val gyro: FloatArray = FloatArray(3),
    val accelRaw: Long = 0, val accelAccepted: Long = 0,
    val gyroRaw: Long = 0, val gyroAccepted: Long = 0,
    val directionChanges: Int = 0,
    val running: Boolean = false
)

/** Fuente única de verdad compartida entre el Service y la Activity (mismo proceso). */
object SensorRepository {
    private val _state = MutableStateFlow(SensorUiState())
    val state: StateFlow<SensorUiState> = _state
    fun update(block: (SensorUiState) -> SensorUiState) { _state.value = block(_state.value) }
    fun reset() { _state.value = SensorUiState() }
}
