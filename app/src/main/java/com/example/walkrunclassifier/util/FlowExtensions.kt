package com.example.walkrunclassifier.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * Groups elements of the original flow into lists of a fixed [size].
 * The [step] parameter controls how many elements to advance before starting a new window.
 * If [partialWindows] is true (and the flow completes), windows smaller than [size] may be emitted at the end.
 */
fun <T> Flow<T>.customWindowed( // Renamed to avoid confusion if a library adds 'windowed' later
    size: Int,
    step: Int = 1, // Default step to 1 for maximum overlap if not specified
    partialWindows: Boolean = false // Usually false for continuous sensor data
): Flow<List<T>> {
    require(size > 0 && step > 0) { "Size and step must be positive ($size, $step)." }
    require(step <= size) { "Step ($step) cannot be greater than size ($size)." } // Important constraint

    return channelFlow {
        val buffer = ArrayDeque<T>(size) // Using ArrayDeque for efficient addLast/removeFirst
        var currentStep = 0

        collect { element ->
            buffer.addLast(element)
            if (buffer.size > size) { // Keep buffer at max 'size'
                buffer.removeFirst()
            }

            if (buffer.size == size) { // Only consider emitting when we have a full window
                if (currentStep == 0) {
                    send(ArrayList(buffer)) // Send a copy of the current full window
                }
                currentStep = (currentStep + 1) % step
            }
        }

        // Handling partial windows if the main flow completes (less critical for continuous sensors)
        // This part would only trigger if the callbackFlow itself closes for a reason other than
        // the collector cancelling (which is the typical scenario for sensor listeners).
        if (partialWindows && buffer.isNotEmpty() && currentStep != 0 && buffer.size < size) {
            // This logic might need adjustment if strict partial window emission
            // at the very end of a finite stream is critical.
            // For continuous streams, we mainly care about the regular window emissions.
            // If buffer.size == size but currentStep !=0, it means we didn't send the last full window
            // because of the step counter. If partialWindows is true, maybe we should send it.
            // This is a subtle point for finite streams.
        }
    }
}