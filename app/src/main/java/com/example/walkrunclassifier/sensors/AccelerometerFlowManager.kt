package com.example.walkrunclassifier.sensors

import android.content.Context // Keep for potential future use if manager needs context directly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import com.example.walkrunclassifier.util.customWindowed // Assuming this is correctly defined

// No TAG needed here if logging is in the data source and this class becomes simpler

class AccelerometerFlowManager(
    // private val context: Context, // No longer needed if all sensor logic is in dataSource
    private val sharedScope: CoroutineScope, // Scope for shareIn
    private val dataSource: AccelerometerDataSource
) {

    // The sensorManager and accelerometerSensor properties are no longer needed here,
    // as that logic is now encapsulated within RealAccelerometerDataSource.

    /**
     * A hot Flow that emits [AccelerometerData] sourced from the injected [dataSource].
     * It starts emitting when the first collector subscribes and stops when the last collector unsubscribes
     * (due to SharingStarted.WhileSubscribed).
     * It also includes a buffer to handle potential backpressure.
     */
    val accelerometerDataFlow: Flow<AccelerometerData> = dataSource.getAccelerometerData()
        .buffer(capacity = 128) // Buffer to handle temporary mismatches
        .shareIn( // Make it a hot flow
            scope = sharedScope, // Use the passed-in scope
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 500),
            replay = 0 // No need to replay old values for new subscribers
        )

    /**
     * Transforms the raw [accelerometerDataFlow] into a Flow of [AccelerometerWindow].
     * @param windowSize The number of [AccelerometerData] points in each window.
     * @param windowStep The number of [AccelerometerData] points to advance before starting a new window.
     */
    fun getWindowedAccelerometerDataFlow(
        windowSize: Int,
        windowStep: Int,
        partialWindows: Boolean = false
    ): Flow<AccelerometerWindow> {
        if (windowSize <= 0) throw IllegalArgumentException("windowSize must be positive")
        if (windowStep <= 0) throw IllegalArgumentException("windowStep must be positive")
        if (windowStep > windowSize) throw IllegalArgumentException("windowStep cannot be greater than windowSize")

        return accelerometerDataFlow // This now comes from the dataSource
            .customWindowed(size = windowSize, step = windowStep, partialWindows = partialWindows)
            .map { listDataPoints ->
                AccelerometerWindow(readings = listDataPoints)
            }
    }
}