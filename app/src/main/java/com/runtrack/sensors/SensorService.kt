package com.runtrack.sensors

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Foreground Service: mantiene vivos los listeners aunque la app se cierre.
 * Un Service normal sería detenido por el sistema; el foreground service + notificación evita eso.
 */
class SensorService : Service(), SensorEventListener {

    private lateinit var sm: SensorManager
    private lateinit var notifier: AlertNotifier

    // Filtros independientes por sensor
    private val accelLp = LowPassFilter(alpha = 0.2f)
    private val gyroLp = LowPassFilter(alpha = 0.3f)
    private val accelGate = EventGate(minIntervalMs = 100, minDelta = 0.05f)  // ≤10 act./s
    private val gyroGate = EventGate(minIntervalMs = 100, minDelta = 0.02f)

    // Detección de giro con histéresis + enfriamiento
    private val turnThreshold = 1.5f          // rad/s en eje Z
    private val turnCooldownMs = 5_000L
    private var lastTurnAt = 0L
    private var inTurn = false

    override fun onCreate() {
        super.onCreate()
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        notifier = AlertNotifier(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        ServiceCompat.startForeground(
            this, AlertNotifier.ID_SERVICE, notifier.serviceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        )
        SensorRepository.reset()
        SensorRepository.update { it.copy(running = true) }
        registerSensors()
        scheduleCloudSync()
        return START_STICKY
    }

    private fun registerSensors() {
        // maxReportLatency = 200 ms → el hardware agrupa (batching) eventos y despierta menos la CPU
        val period = SensorManager.SENSOR_DELAY_GAME
        val latencyUs = 200_000
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, period, latencyUs) }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(this, it, period, latencyUs) }
    }

    /** Tarea periódica con restricciones: solo con red y batería no baja (ver CloudSyncWorker). */
    private fun scheduleCloudSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("runtrack_sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val f = accelLp.apply(e.values)
                val ok = accelGate.accept(e.timestamp, f)
                SensorRepository.update {
                    it.copy(accelRaw = it.accelRaw + 1,
                        accelAccepted = it.accelAccepted + if (ok) 1 else 0,
                        accel = if (ok) f.copyOf() else it.accel)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val f = gyroLp.apply(e.values)
                detectTurn(f[2])                 // la detección usa todas las lecturas filtradas
                val ok = gyroGate.accept(e.timestamp, f)
                SensorRepository.update {
                    it.copy(gyroRaw = it.gyroRaw + 1,
                        gyroAccepted = it.gyroAccepted + if (ok) 1 else 0,
                        gyro = if (ok) f.copyOf() else it.gyro)
                }
            }
        }
    }

    private fun detectTurn(z: Float) {
        val now = SystemClock.elapsedRealtime()
        if (!inTurn && abs(z) > turnThreshold && now - lastTurnAt > turnCooldownMs) {
            inTurn = true; lastTurnAt = now
            SensorRepository.update { it.copy(directionChanges = it.directionChanges + 1) }
            notifier.directionChange(SensorRepository.state.value.directionChanges)
        } else if (inTurn && abs(z) < turnThreshold / 2) {
            inTurn = false                      // histéresis: se rearma al bajar a la mitad
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sm.unregisterListener(this)             // IMPRESCINDIBLE: evita fugas y consumo de batería
        SensorRepository.update { it.copy(running = false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val ACTION_STOP = "com.runtrack.sensors.STOP" }
}
