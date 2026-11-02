package com.runtrack.sensors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.runtrack.sensors.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startMonitoring()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        showAvailability()

        b.btnStart.setOnClickListener {
            val needed = buildList {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.launch(needed.toTypedArray())
        }
        b.btnStop.setOnClickListener {
            startService(Intent(this, SensorService::class.java).setAction(SensorService.ACTION_STOP))
        }

        // Recolecta el estado solo mientras la UI es visible (ahorra CPU/batería)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SensorRepository.state.collect { s ->
                    b.tvAccel.text = "Acelerómetro (m/s²)\nX %.2f  Y %.2f  Z %.2f".format(s.accel[0], s.accel[1], s.accel[2])
                    b.tvGyro.text = "Giroscopio (rad/s)\nX %.2f  Y %.2f  Z %.2f".format(s.gyro[0], s.gyro[1], s.gyro[2])
                    val aPct = if (s.accelRaw > 0) 100 - 100 * s.accelAccepted / s.accelRaw else 0
                    val gPct = if (s.gyroRaw > 0) 100 - 100 * s.gyroAccepted / s.gyroRaw else 0
                    b.tvStats.text = "Estado: ${if (s.running) "ACTIVO" else "detenido"}\n" +
                        "Cambios de dirección: ${s.directionChanges}\n" +
                        "Acel: ${s.accelRaw} recibidas / ${s.accelAccepted} usadas (−$aPct %)\n" +
                        "Giro: ${s.gyroRaw} recibidas / ${s.gyroAccepted} usadas (−$gPct %)"
                }
            }
        }
    }

    private fun startMonitoring() {
        ContextCompat.startForegroundService(this, Intent(this, SensorService::class.java))
    }

    /** Paso 1: verificación de disponibilidad de hardware. */
    private fun showAvailability() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fun line(name: String, type: Int): String {
            val s = sm.getDefaultSensor(type)
            return if (s == null) "✗ $name: NO disponible"
            else "✓ $name: ${s.name} (rango ${s.maximumRange}, resolución ${s.resolution}, " +
                "${if (s.minDelay > 0) 1_000_000 / s.minDelay else 0} Hz máx.)"
        }
        b.tvAvailability.text = line("Acelerómetro", Sensor.TYPE_ACCELEROMETER) + "\n" +
            line("Giroscopio", Sensor.TYPE_GYROSCOPE)
    }
}
