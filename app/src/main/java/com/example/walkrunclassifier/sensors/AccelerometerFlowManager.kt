package com.example.walkrunclassifier.sensors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import com.example.walkrunclassifier.util.customWindowed


class AccelerometerFlowManager(
    private val sharedScope: CoroutineScope,
    private val dataSource: AccelerometerDataSource
) {

    /**
     * A hot Flow that emits [AccelerometerData] sourced from the injected [dataSource].
     * It starts emitting when the first collector subscribes and stops when the last collector
     * unsubscribes (due to SharingStarted.WhileSubscribed).
     * It also includes a buffer to handle potential backpressure (data is produced faster
     * than consumed).
     */
    private val accelerometerDataFlow: Flow<AccelerometerData> = dataSource.getAccelerometerData()
        .buffer(capacity = 128) // Buffer to handle temporary mismatches between producer & consumer
        .shareIn( // Converts the cold flow into a hot, shared flow
            scope = sharedScope, // Coroutine scope in which sharing and upstream collection run
            // Emit only while subscribed
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 500),
            replay = 0 // Do not replay old values to new collectors
        )

    /**
     * Transforms the raw [accelerometerDataFlow] into a Flow of [AccelerometerWindow].
     * @param windowSize The number of [AccelerometerData] points in each window.
     * @param windowStep The number of [AccelerometerData] points to advance
     * before starting a new window.
     */
    fun getWindowedAccelerometerDataFlow(
        windowSize: Int,
        windowStep: Int,
    ): Flow<AccelerometerWindow> {
        if (windowSize <= 0) throw IllegalArgumentException("windowSize must be positive")
        if (windowStep <= 0) throw IllegalArgumentException("windowStep must be positive")
        if (windowStep > windowSize) throw IllegalArgumentException("windowStep cannot" +
                "be greater than windowSize")

        return accelerometerDataFlow // This now comes from the dataSource
            .customWindowed(size = windowSize, step = windowStep)
            .map { listDataPoints ->
                AccelerometerWindow(readings = listDataPoints)
            }
    }
}