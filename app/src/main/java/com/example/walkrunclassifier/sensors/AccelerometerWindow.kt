package com.example.walkrunclassifier.sensors

/**
 * Represents a window (a list) of accelerometer readings.
 * This is often the input format before feature extraction for ML models.
 *
 * @param readings The list of AccelerometerData points in this window.
 * @param startTime The timestamp of the first reading in the window (optional, for reference).
 * @param endTime The timestamp of the last reading in the window (optional, for reference).
 */
data class AccelerometerWindow(
    val readings: List<AccelerometerData>,
    val startTime: Long? = readings.firstOrNull()?.timestamp, // Example: Can derive if needed
    val endTime: Long? = readings.lastOrNull()?.timestamp   // Example: Can derive if needed
)
