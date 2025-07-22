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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import com.example.walkrunclassifier.util.customWindowed
import kotlinx.coroutines.CoroutineScope

private const val TAG = "AccelerometerFlowMgr" // For logging

class AccelerometerFlowManager(private val context: Context, private val scope: CoroutineScope) {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val accelerometerSensor: Sensor? by lazy {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.e(TAG, "Accelerometer sensor not available on this device.")
        }
        sensor
    }

    /**
     * A hot Flow that emits [AccelerometerData] from the accelerometer sensor.
     * It starts emitting when the first collector subscribes and stops when the last collector unsubscribes
     * (due to SharingStarted.WhileSubscribed).
     * It also includes a buffer to handle potential backpressure.
     */
    val accelerometerDataFlow: Flow<AccelerometerData> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    trySend(
                        AccelerometerData(
                            timestamp = event.timestamp, // Time in nanoseconds
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2]
                        )
                    ).isSuccess // Check isSuccess or handle potential failures if the channel is closed
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.i(TAG, "Sensor: ${sensor?.name}, Accuracy changed to: $accuracy")
                // You could potentially emit this information on a separate flow or state if needed
            }
        }

        if (accelerometerSensor == null) {
            Log.e(TAG, "Cannot register listener, accelerometer sensor is null.")
            close(IllegalStateException("Accelerometer sensor not available on this device."))
            return@callbackFlow
        }

        Log.d(TAG, "Registering accelerometer listener for Flow.")
        // SENSOR_DELAY_GAME is a good starting point for activity recognition (approx 50Hz)
        // Adjust as needed for your model's requirements and battery considerations.
        sensorManager.registerListener(
            listener,
            accelerometerSensor,
            SensorManager.SENSOR_DELAY_GAME
        )

        // This block is executed when the Flow collector cancels or the channel is closed
        awaitClose {
            Log.d(TAG, "Unregistering accelerometer listener from Flow.")
            sensorManager.unregisterListener(listener)
        }
    }
        .buffer(capacity = 128) // Buffer to handle temporary mismatches in production/consumption rates
        .shareIn( // Make it a hot flow: starts when first subscriber appears, stops when last leaves
            scope = kotlinx.coroutines.GlobalScope, // Or a more specific CoroutineScope if available and appropriate
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 500),
            replay = 0 // No need to replay old values for new subscribers in this case
        )


    /**
     * Transforms the raw [accelerometerDataFlow] into a Flow of [AccelerometerWindow].
     * @param windowSize The number of [AccelerometerData] points in each window.
     * @param windowStep The number of [AccelerometerData] points to advance before starting a new window.
     *                   Typically same as windowSize for non-overlapping windows, or windowSize/2 for 50% overlap.
     */
    fun getWindowedAccelerometerDataFlow(
        windowSize: Int, // e.g., 500
        windowStep: Int, // e.g., 50
        partialWindows: Boolean = false
    ): Flow<AccelerometerWindow> {
        if (windowSize <= 0) throw IllegalArgumentException("windowSize must be positive")
        if (windowStep <= 0) throw IllegalArgumentException("windowStep must be positive")
        if (windowStep > windowSize) throw IllegalArgumentException("windowStep cannot be greater than windowSize")

        return accelerometerDataFlow
            .customWindowed(size = windowSize, step = windowStep, partialWindows = partialWindows) // Use the custom operator
            .map { listDataPoints ->
                AccelerometerWindow(readings = listDataPoints)
            }
    }
}