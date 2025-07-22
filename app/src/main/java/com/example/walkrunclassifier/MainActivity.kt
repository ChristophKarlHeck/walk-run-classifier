package com.example.walkrunclassifier

import android.os.Bundle
import android.util.Log // <--- IMPORT Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column // For demo
import androidx.compose.foundation.layout.Arrangement // For demo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.* // For mutableStateOf, etc.
import androidx.compose.ui.Alignment // For demo
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope // <--- IMPORT lifecycleScope
import com.example.walkrunclassifier.sensors.AccelerometerFlowManager
import com.example.walkrunclassifier.sensors.AccelerometerWindow // <--- IMPORT AccelerometerWindow
// If you have AccelerometerData and use it in UI:
// import com.example.walkrunclassifier.sensors.AccelerometerData
import com.example.walkrunclassifier.ui.theme.WalkRunClassifierTheme
import kotlinx.coroutines.Job // <--- IMPORT Job
import kotlinx.coroutines.flow.launchIn // <--- IMPORT launchIn
import kotlinx.coroutines.flow.onEach // <--- IMPORT onEach
// If you use distinctUntilChanged:
// import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch // If processAccelerometerWindow uses it
import com.example.walkrunclassifier.ml.ActivityClassifier // <--- IMPORT ActivityClassifier
import com.example.walkrunclassifier.sensors.AccelerometerData // <--- IMPORT ModelInputReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.walkrunclassifier.ml.AccelerometerReading as ModelInputReading


// Constants for windowing parameters
private const val DESIRED_WINDOW_DURATION_SECONDS = 10
private const val SENSOR_SAMPLING_RATE_HZ = 20 // Approximate for SENSOR_DELAY_GAME
private const val CLASSIFICATION_INTERVAL_SECONDS = 1

private const val ACC_WINDOW_SIZE = DESIRED_WINDOW_DURATION_SECONDS * SENSOR_SAMPLING_RATE_HZ
private const val ACC_WINDOW_STEP = CLASSIFICATION_INTERVAL_SECONDS * SENSOR_SAMPLING_RATE_HZ

private const val MAIN_ACTIVITY_TAG = "MainActivity" // <--- DECLARE TAG

private const val TFLITE_MODEL_FILENAME = "model.tflite"

class MainActivity : ComponentActivity() {

    private lateinit var accelerometerFlowManager: AccelerometerFlowManager
    private var windowedDataJob: Job? = null // <--- DECLARE JOB

    // Example state for UI update
    private var processedWindowCount by mutableStateOf(0) // <--- DECLARE for UI
    private var currentActivityGuess by mutableStateOf("Initializing...") // <--- Example state

    private var activityClassifier: ActivityClassifier? = null // Declare classifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("ON Created")
        Log.d("MyAppTest", "MainActivity onCreate CALLED")

        enableEdgeToEdge()

        // --- Initialize TFLite Activity Classifier ---
        activityClassifier = try {
            ActivityClassifier(applicationContext, TFLITE_MODEL_FILENAME)
        } catch (e: Exception) {
            Log.e(MAIN_ACTIVITY_TAG, "Error initializing ActivityClassifier: ${e.message}", e)
            currentActivityGuess = "Error: Classifier Init Failed"
            null // Set to null if initialization fails
        }

        if (activityClassifier != null) { // Only start flow if classifier is ready
            accelerometerFlowManager = AccelerometerFlowManager(applicationContext, lifecycleScope)

            windowedDataJob = accelerometerFlowManager.getWindowedAccelerometerDataFlow(
                windowSize = ACC_WINDOW_SIZE, // This should provide the exact number of samples the model needs
                windowStep = ACC_WINDOW_STEP
            )
                .onEach { window -> // window is of type AccelerometerWindow
                    processedWindowCount++
                    Log.d(
                        MAIN_ACTIVITY_TAG,
                        "Sensor Window #${processedWindowCount}: ${window.readings.size} readings."
                    )
                    // Call the new version of the function
                    processAccelerometerWindowWithTFLite(window)
                    //processAccelerometerWindow(window)
                }
                .launchIn(lifecycleScope)
        } else {
            Log.e(MAIN_ACTIVITY_TAG, "Accelerometer data collection NOT started due to classifier init failure.")
        }

        setContent {
            WalkRunClassifierTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Example UI to show the data
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Processed Windows: $processedWindowCount")
                        Text(text = "Activity: $currentActivityGuess")
                    }
                    // Greeting(
                    // name = "Android",
                    // modifier = Modifier.padding(innerPadding)
                    // )
                }
            }
        }
    }

    /**
     * Replaces the old simulation with TFLite model inference.
     */
    private fun processAccelerometerWindowWithTFLite(window: AccelerometerWindow) {
        if (activityClassifier == null) {
            Log.w(MAIN_ACTIVITY_TAG, "Classifier not initialized, skipping TFLite processing.")
            currentActivityGuess = "Error: Classifier Not Ready"
            return
        }

        // The number of readings in 'window.readings' MUST match
        // what ActivityClassifier.modelExpectedInputSamples is set to.
        // If AccelerometerFlowManager windowing (ACC_WINDOW_SIZE) doesn't guarantee this,
        // you might need an additional check or adjustment here.
        if (window.readings.size != activityClassifier?.modelExpectedInputSamples) {
            Log.w(MAIN_ACTIVITY_TAG, "Window size mismatch: Got ${window.readings.size}, expected ${activityClassifier?.modelExpectedInputSamples}. Skipping inference.")
            currentActivityGuess = "Error: Window Size Mismatch"
            return
        }


        // Launch on a background thread for TFLite inference
        lifecycleScope.launch(Dispatchers.Default) {
            // 1. Adapt data from AccelerometerWindow.readings to List<ModelInputReading>
            //    (ModelInputReading is an alias for AccelerometerReading in ActivityClassifier.kt)
            //    This mapping is crucial. Ensure your `AccelerometerData` (from the flow)
            //    has x, y, z, and timestamp fields compatible with `ModelInputReading`.
            val modelInputData = window.readings.map { accData: AccelerometerData ->
                ModelInputReading(
                    x = accData.x,
                    y = accData.y,
                    z = accData.z,
                    timestamp = accData.timestamp // Timestamp might not be used by model but good to pass along
                )
            }

            // 2. Perform classification
            val classificationResult: Pair<String, Float>? = activityClassifier?.classify(modelInputData)

            // 3. Update UI on the Main thread
            withContext(Dispatchers.Main) {
                if (classificationResult != null) {
                    val (label, confidence) = classificationResult
                    currentActivityGuess = "$label (${String.format("%.2f", confidence)})"
                    Log.i(MAIN_ACTIVITY_TAG, "TFLite Prediction: $label, Confidence: $confidence")
                } else {
                    currentActivityGuess = "Classification Failed"
                    // Log.w(MAIN_ACTIVITY_TAG, "TFLite classification returned null.")
                }
            }
        }
    }

    // Define processAccelerometerWindow method
    private fun processAccelerometerWindow(window: AccelerometerWindow) {
        // This is where your feature extraction and ML inference will happen.
        // For simulation purposes:
        lifecycleScope.launch { // Simulate some processing
            val avgMagnitude = window.readings.map {
                kotlin.math.sqrt(it.x * it.x + it.y * it.y + it.z * it.z)
            }.average()

            currentActivityGuess = if (avgMagnitude > 13.0) {
                "Running (Simulated)"
            } else if (avgMagnitude > 9.9) {
                "Walking (Simulated)"
            } else {
                "Still (Simulated)"
            }
            Log.d(MAIN_ACTIVITY_TAG, "Current Guess: $currentActivityGuess based on avg magnitude: $avgMagnitude")
        }
    }
    // Optional: Cancel the job in onDestroy, though launchIn(lifecycleScope) handles this.
    // override fun onDestroy() {
    //     super.onDestroy()
    //     windowedDataJob?.cancel()
    // }
}
