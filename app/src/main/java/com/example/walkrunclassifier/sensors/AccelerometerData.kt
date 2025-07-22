package com.example.walkrunclassifier.sensors

/**
 * Represents a single reading from the accelerometer.
 *
 * @param timestamp The timestamp of the event in nanoseconds.
 * @param x The acceleration force along the x axis (including gravity).
 * @param y The acceleration force along the y axis (including gravity).
 * @param z The acceleration force along the z axis (including gravity).
 */

data class AccelerometerData(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)
