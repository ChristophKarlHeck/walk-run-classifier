package com.example.walkrunclassifier.sensors

/**
 * Represents a single reading from the accelerometer, after conversion to milli-g.
 *
 * @param timestamp The timestamp of the event in nanoseconds.
 * @param x The acceleration force along the x axis in milli-g (mg).
 * @param y The acceleration force along the y axis in milli-g (mg).
 * @param z The acceleration force along the z axis in milli-g (mg).
 */

data class AccelerometerData(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)
