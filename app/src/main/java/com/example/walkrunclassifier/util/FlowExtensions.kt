package com.example.walkrunclassifier.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * Groups elements of the original flow into lists of a fixed [size] (sliding window).
 * The [step] parameter controls how many elements to advance before starting a new window.
 */
fun <T> Flow<T>.customWindowed(
    size: Int,
    step: Int,
): Flow<List<T>> {
    require(size > 0 && step > 0) { "Size and step must be positive ($size, $step)." }
    require(step <= size) { "Step ($step) cannot be greater than size ($size)." }

    return channelFlow {
        val buffer = ArrayDeque<T>(size)
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
    }
}