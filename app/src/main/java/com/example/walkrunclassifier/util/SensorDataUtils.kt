package com.example.walkrunclassifier.util


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
 * @return The value in milli-g, clamped to +-maxMilliGRange, because this range complies
 * with the range from training data and we cannot set the range in the settings of the phone.
 */
fun convertAndClampToMilliG(
    valueInMetersPerSecondSquared: Float,
    maxMilliGRange: Float = DEFAULT_MAX_MG_RANGE // Allow customization of range if needed
): Float {
    val valueInGs = valueInMetersPerSecondSquared / STANDARD_GRAVITY_MS2

    val valueInMilliGs = valueInGs * MG_PER_G

    return valueInMilliGs.coerceIn(-maxMilliGRange, maxMilliGRange)
}

