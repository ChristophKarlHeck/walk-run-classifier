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
    private var interpreter: Interpreter? = null // Make nullable for safer initialization

    // --- Critical: These MUST match your model's specifications ---
    // You MUST find these values from your model (e.g., using Netron or model documentation)
    val modelExpectedInputSamples: Int = 260 // EXAMPLE: Number of time steps (e.g., 100 accelerometer readings)
    private val modelFeaturesPerSample: Int = 3      // EXAMPLE: Number of features per time step (e.g., x, y, z)
    private val numOutputClasses: Int = 3            // EXAMPLE: "Stationary", "Walking", "Running"
    // --- End Critical Model Specifications ---

    // Define the labels for your output classes in the correct order
    private val classLabels = listOf("stationary", "walking", "running") // Adjust if your model has more/different classes

    private lateinit var inputByteBuffer: ByteBuffer
    private lateinit var outputBuffer: Array<FloatArray> // For models outputting probabilities per class

    init {
        val options = Interpreter.Options()
        // TODO: Consider adding delegates (NNAPI, GPU) for performance optimization later
        // options.addDelegate(NnApiDelegate())

        try {
            val modelByteBuffer = loadModelFile(context, modelNameInAssets)
            interpreter = Interpreter(modelByteBuffer, options)
            initializeBuffers()
            Log.i(TAG, "TFLite interpreter initialized successfully: $modelNameInAssets")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR initializing TFLite interpreter: ${e.message}", e)
            // Handle initialization failure: The interpreter will remain null.
            // The classify method should check for this.
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
        // Assuming batch_size = 1 and input is FLOAT32 (4 bytes per float)
        val inputByteSize = 1 * modelExpectedInputSamples * modelFeaturesPerSample * 4
        inputByteBuffer = ByteBuffer.allocateDirect(inputByteSize)
        inputByteBuffer.order(ByteOrder.nativeOrder()) // Crucial for correct byte interpretation

        // Output Buffer:
        // Assuming batch_size = 1 and output is an array of probabilities for each class
        outputBuffer = Array(1) { FloatArray(numOutputClasses) }

        Log.d(TAG, "Input buffer size: $inputByteSize bytes. Output buffer shape: [1, $numOutputClasses]")
    }

    /**
     * Prepares the raw accelerometer data window to match the model's expected input format.
     * THIS IS THE MOST CRITICAL AND MODEL-SPECIFIC PART.
     *
     * @param readingsList A list of AccelerometerReading objects forming a window.
     * @return ByteBuffer ready for the interpreter, or null if input is invalid or preprocessing fails.
     */
    private fun preprocessInput(readingsList: List<AccelerometerData>): ByteBuffer? {
        if (readingsList.size != modelExpectedInputSamples) {
            Log.w(TAG, "Input data size (${readingsList.size}) doesn't match model's expected input size ($modelExpectedInputSamples).")
            // In a real app, you might pad with zeros or truncate if your model can handle it.
            // For now, we'll return null to indicate an issue.
            return null
        }

        inputByteBuffer.rewind() // Reset buffer position to the beginning for writing

        for (reading in readingsList) {
            // --- Normalization/Scaling ---
            // **VERY IMPORTANT**: If your model was trained with normalized/scaled data
            // (e.g., values scaled to [-1, 1] or Z-score normalized),
            // you MUST apply the *exact same* transformation here.
            // Example (no normalization, using raw values - adapt as needed):
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
            Log.e(TAG, "Output probabilities size (${probabilities.size}) doesn't match expected numOutputClasses ($numOutputClasses). Check model output or numOutputClasses.")
            return null
        }

        var maxProbability = -1f
        var predictedClassIndex = -1

        for (i in probabilities.indices) {
            if (probabilities[i] > maxProbability) {
                maxProbability = probabilities[i]
                predictedClassIndex = i
            }
            // Log.v(TAG, "Class ${classLabels.getOrNull(i) ?: "Unknown"}: ${probabilities[i]}") // Verbose
        }

        return if (predictedClassIndex != -1 && predictedClassIndex < classLabels.size) {
            classLabels[predictedClassIndex] to maxProbability
        } else {
            Log.e(TAG, "Could not determine predicted class. Index: $predictedClassIndex, Prob: $maxProbability")
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
