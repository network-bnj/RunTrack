package com.runtrack.sensors

import kotlin.math.abs

/** Filtro paso-bajo exponencial: suaviza el ruido del sensor. alpha pequeño = más suavizado. */
class LowPassFilter(private val alpha: Float = 0.15f) {
    private var last: FloatArray? = null
    fun apply(input: FloatArray): FloatArray {
        val prev = last
        val out = if (prev == null) input.copyOf()
        else FloatArray(input.size) { i -> prev[i] + alpha * (input[i] - prev[i]) }
        last = out
        return out
    }
}

/**
 * Control de eventos: deja pasar una lectura solo si
 *  (a) pasó al menos [minIntervalMs] desde la última aceptada  (throttling) y
 *  (b) alguno de los ejes cambió más de [minDelta]             (anti-duplicados).
 */
class EventGate(private val minIntervalMs: Long, private val minDelta: Float) {
    private var lastTsMs = 0L
    private var lastValues: FloatArray? = null

    fun accept(timestampNs: Long, values: FloatArray): Boolean {
        val tsMs = timestampNs / 1_000_000
        if (tsMs - lastTsMs < minIntervalMs) return false
        val prev = lastValues
        if (prev != null && values.indices.all { abs(values[it] - prev[it]) < minDelta }) return false
        lastTsMs = tsMs
        lastValues = values.copyOf()
        return true
    }
}
