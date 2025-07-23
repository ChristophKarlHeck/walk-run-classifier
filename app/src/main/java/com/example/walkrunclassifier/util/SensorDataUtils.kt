package com.example.walkrunclassifier.util

import android.util.Log // If you decide to add logging within these utils

// --- Sensor Data Conversion Utilities ---

// Constants for accelerometer conversion
const val STANDARD_GRAVITY_MS2 = 9.81f // Standard gravity in m/s^2
const val MG_PER_G = 1000f             // Milli-g per g
const val DEFAULT_MAX_MG_RANGE = 4000f // Default clamping range for milli-g (+-4000mg)

/**
 * Converts an accelerometer value from meters per second squared (m/s^2)
 * to milli-g (mg) and clamps it to a specified milli-g range.
 *
 * @param valueInMetersPerSecondSquared The accelerometer reading in m/s^2.
 * @param maxMilliGRange The maximum absolute value for the milli-g range (e.g., 4000f for +-4000mg).
 *                       Defaults to [DEFAULT_MAX_MG_RANGE].
 * @return The value in milli-g, clamped to +-maxMilliGRange.
 */
fun convertAndClampToMilliG(
    valueInMetersPerSecondSquared: Float,
    maxMilliGRange: Float = DEFAULT_MAX_MG_RANGE // Allow customization of range if needed
): Float {
    // 1. Convert from m/s^2 to g's
    val valueInGs = valueInMetersPerSecondSquared / STANDARD_GRAVITY_MS2

    // 2. Convert from g's to milli-g's (mg)
    val valueInMilliGs = valueInGs * MG_PER_G

    // 3. Clamp to the specified range
    return valueInMilliGs.coerceIn(-maxMilliGRange, maxMilliGRange)
}

// You could add other sensor utility functions here in the future, e.g.:
// fun normalizeSensorValue(value: Float, min: Float, max: Float): Float { ... }
