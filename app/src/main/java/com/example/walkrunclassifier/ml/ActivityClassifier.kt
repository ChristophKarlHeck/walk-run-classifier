package com.example.walkrunclassifier.ml // Or your preferred package

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import com.example.walkrunclassifier.sensors.AccelerometerData

class ActivityClassifier(
    context: Context,
    modelNameInAssets: String
) {
    private val TAG = "ActivityClassifier"
    private var interpreter: Interpreter? = null

    // MUST match model's specifications
    val modelExpectedInputSamples: Int = 260 // Number of time steps (260 = 10s)
    private val modelFeaturesPerSample: Int = 3 // Number of features per time step (e.g., x, y, z)
    private val numOutputClasses: Int = 3 // "stationary", "walking", "running"

    // Define the labels for your output classes in the correct order
    private val classLabels = listOf("stationary", "walking", "running")

    private lateinit var inputByteBuffer: ByteBuffer
    // For models outputting probabilities per class
    private lateinit var outputBuffer: Array<FloatArray>

    init {
        val options = Interpreter.Options()

        try {
            val modelByteBuffer = loadModelFile(context, modelNameInAssets)
            interpreter = Interpreter(modelByteBuffer, options)
            initializeBuffers()
            Log.i(TAG, "TFLite interpreter initialized successfully: $modelNameInAssets")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR initializing TFLite interpreter: ${e.message}", e)
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        assetFileDescriptor.use { afd ->
            FileInputStream(afd.fileDescriptor).use { fis ->
                fis.channel.use { channel ->
                    return channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        }
    }

    private fun initializeBuffers() {
        // Input Buffer:
        // Size = batch_size * modelExpectedInputSamples * modelFeaturesPerSample * bytes_per_float
        // batch_size = 1 and input is FLOAT32 (4 bytes per float)
        val inputByteSize = 1 * modelExpectedInputSamples * modelFeaturesPerSample * 4
        inputByteBuffer = ByteBuffer.allocateDirect(inputByteSize)
        inputByteBuffer.order(ByteOrder.nativeOrder()) // Crucial for correct byte interpretation

        // Output Buffer:
        //  batch_size = 1 and output is an array of probabilities for each class
        outputBuffer = Array(1) { FloatArray(numOutputClasses) }

        Log.d(TAG, "Input buffer size: $inputByteSize bytes. " +
                "Output buffer shape: [1, $numOutputClasses]")
    }

    /**
     * Prepares the raw accelerometer data window to match the model's expected input format.
     * THIS IS THE MOST CRITICAL AND MODEL-SPECIFIC PART.
     *
     * @param readingsList A list of AccelerometerReading objects forming a window.
     * @return ByteBuffer ready for the interpreter, or null
     * if input is invalid or preprocessing fails.
     */
    private fun preprocessInput(readingsList: List<AccelerometerData>): ByteBuffer? {
        if (readingsList.size != modelExpectedInputSamples) {
            Log.w(TAG, "Input data size (${readingsList.size}) doesn't match model's " +
                    "expected input size ($modelExpectedInputSamples).")
            return null
        }

        inputByteBuffer.rewind() // Reset buffer position to the beginning for writing

        for (reading in readingsList) {
            // Mapping mg values to range [-1, 1] as applied in training
            val xProcessed = reading.x / 4000
            val yProcessed = reading.y / 4000
            val zProcessed = reading.z / 4000

            inputByteBuffer.putFloat(xProcessed)
            inputByteBuffer.putFloat(yProcessed)
            inputByteBuffer.putFloat(zProcessed)
        }
        return inputByteBuffer
    }

    /**
     * Runs inference on the preprocessed input data.
     *
     * @param inputWindowData A list of AccelerometerReading objects forming a window.
     * @return A Pair containing the predicted class label (String) and its confidence (Float),
     *         or null if classification fails or the interpreter isn't initialized.
     */
    fun classify(inputWindowData: List<AccelerometerData>): Pair<String, Float>? {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter not initialized. Cannot classify.")
            return null
        }

        val processedInputBuffer = preprocessInput(inputWindowData)
            ?: run {
                Log.e(TAG, "Preprocessing failed or input data was invalid.")
                return null
            }

        try {
            interpreter?.run(processedInputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "TFLite inference error: ${e.message}", e)
            return null
        }

        // Post-process the outputBuffer to get the class with the highest probability
        val probabilities = outputBuffer[0] // Assuming batch size of 1

        if (probabilities.size != numOutputClasses) {
            Log.e(TAG, "Output probabilities size (${probabilities.size}) doesn't match " +
                    "expected numOutputClasses ($numOutputClasses). " +
                    "Check model output or numOutputClasses.")
            return null
        }

        var maxProbability = -1f
        var predictedClassIndex = -1

        for (i in probabilities.indices) {
            if (probabilities[i] > maxProbability) {
                maxProbability = probabilities[i]
                predictedClassIndex = i
            }
        }

        return if (predictedClassIndex != -1 && predictedClassIndex < classLabels.size) {
            classLabels[predictedClassIndex] to maxProbability
        } else {
            Log.e(TAG, "Could not determine predicted class. " +
                    "Index: $predictedClassIndex, Prob: $maxProbability")
            null
        }
    }

    /**
     * Releases TFLite resources. Call this when the classifier is no longer needed
     * (e.g., in Activity's onDestroy or ViewModel's onCleared).
     */
    fun close() {
        interpreter?.close()
        interpreter = null // Help GC
        Log.i(TAG, "TFLite interpreter closed.")
    }
}
