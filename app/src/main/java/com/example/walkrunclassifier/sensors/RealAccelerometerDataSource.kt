package com.example.walkrunclassifier.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.example.walkrunclassifier.util.convertAndClampToMilliG

private const val REAL_DS_TAG = "RealAccelDataSource"

class RealAccelerometerDataSource(
    private val context: Context,
    private val desiredFrequencyHz: Int) : AccelerometerDataSource {

    private val desiredSamplingPeriodUs: Int = (1000000 / desiredFrequencyHz)

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val accelerometerSensor: Sensor? by lazy {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.e(REAL_DS_TAG, "Accelerometer sensor not available on this device.")
        }
        sensor
    }

    override fun getAccelerometerData(): Flow<AccelerometerData> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {

                    val x_mg = convertAndClampToMilliG(event.values[0])
                    val y_mg = convertAndClampToMilliG(event.values[1])
                    val z_mg = convertAndClampToMilliG(event.values[2])

                    // passed to the flow pipeline
                    trySend(
                        AccelerometerData(
                            timestamp = event.timestamp,
                            x = x_mg,
                            y = y_mg,
                            z = z_mg
                        )
                    ).isSuccess // Or handle failure if channel is closed
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.i(REAL_DS_TAG, "Sensor: ${sensor?.name}, Accuracy changed to: $accuracy")
            }
        }

        if (accelerometerSensor == null) {
            Log.e(REAL_DS_TAG, "Cannot register listener, accelerometer sensor is null.")
            close(IllegalStateException("Accelerometer sensor not available on this device."))
            return@callbackFlow
        }

        Log.d(REAL_DS_TAG, "Registering accelerometer listener for Flow.")
        sensorManager.registerListener(
            listener,
            accelerometerSensor,
            desiredSamplingPeriodUs
        )

        awaitClose {
            Log.d(REAL_DS_TAG, "Unregistering accelerometer listener from Flow.")
            sensorManager.unregisterListener(listener)
        }
    }
}
