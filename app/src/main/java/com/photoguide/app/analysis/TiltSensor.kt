package com.photoguide.app.analysis

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Reads the gravity/accelerometer sensor and exposes the device roll and pitch
 * in degrees, smoothed with a low-pass filter so the on-screen level isn't
 * jittery.
 *
 *  - roll  : rotation about the viewing axis. 0 == held upright in portrait,
 *            positive == right edge dipped down.
 *  - pitch : forward/back tilt. 0 == vertical, positive == top tipped away.
 */
class TiltSensor(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val available: Boolean get() = sensor != null

    @Volatile var rollDegrees: Float = 0f; private set
    @Volatile var pitchDegrees: Float = 0f; private set

    private var gx = 0f; private var gy = 0f; private var gz = 0f
    private var seeded = false
    private val alpha = 0.2f // smoothing factor

    fun start() {
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        // Low-pass filter to isolate gravity from short movements.
        if (!seeded) {
            gx = event.values[0]; gy = event.values[1]; gz = event.values[2]
            seeded = true
        } else {
            gx += alpha * (event.values[0] - gx)
            gy += alpha * (event.values[1] - gy)
            gz += alpha * (event.values[2] - gz)
        }

        rollDegrees = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
        pitchDegrees = Math.toDegrees(
            atan2(gz.toDouble(), sqrt((gx * gx + gy * gy).toDouble()))
        ).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
}
