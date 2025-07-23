package com.example.walkrunclassifier.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val REAL_DS_TAG = "RealAccelDataSource"

class RealAccelerometerDataSource(private val context: Context) : AccelerometerDataSource {

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
                    trySend(
                        AccelerometerData(
                            timestamp = event.timestamp,
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2]
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
            SensorManager.SENSOR_DELAY_GAME
        )

        awaitClose {
            Log.d(REAL_DS_TAG, "Unregistering accelerometer listener from Flow.")
            sensorManager.unregisterListener(listener)
        }
    }
}
