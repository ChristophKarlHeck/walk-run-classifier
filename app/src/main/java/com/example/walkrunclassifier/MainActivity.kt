package com.example.walkrunclassifier

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.walkrunclassifier.sensors.AccelerometerFlowManager
import com.example.walkrunclassifier.sensors.AccelerometerWindow
import com.example.walkrunclassifier.sensors.RealAccelerometerDataSource // Import Real
import com.example.walkrunclassifier.ui.theme.WalkRunClassifierTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.example.walkrunclassifier.ml.ActivityClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.walkrunclassifier.sensors.AccelerometerData as ModelInputReading

// Constants
private const val DESIRED_WINDOW_DURATION_SECONDS = 10 // 10s
private const val SENSOR_SAMPLING_RATE_HZ = 26 // Approximate for SENSOR_DELAY_GAME
private const val CLASSIFICATION_INTERVAL_SECONDS = 1 // 1s

private const val ACC_WINDOW_SIZE = DESIRED_WINDOW_DURATION_SECONDS * SENSOR_SAMPLING_RATE_HZ
private const val ACC_WINDOW_STEP = CLASSIFICATION_INTERVAL_SECONDS * SENSOR_SAMPLING_RATE_HZ

private const val MAIN_ACTIVITY_TAG = "MainActivity"
private const val TFLITE_MODEL_FILENAME = "model.tflite" // Or your actual path

class MainActivity : ComponentActivity() {

    // Companion object for testing purposes (simple service locator for tests)
    companion object {
        var testAccelerometerFlowManager: AccelerometerFlowManager? = null
    }

    private lateinit var accelerometerFlowManager: AccelerometerFlowManager
    private var windowedDataJob: Job? = null

    // UI State
    var processedWindowCount by mutableStateOf(0) // Made public for potential test access, but not ideal
        private set // Restrict external modification
    var currentActivityGuess by mutableStateOf("initializing") // Made public for test access
        private set  // Restrict external modification


    private var activityClassifier: ActivityClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(MAIN_ACTIVITY_TAG, "onCreate CALLED")
        enableEdgeToEdge()

        // --- Initialize TFLite Activity Classifier ---
        activityClassifier = try {
            ActivityClassifier(applicationContext, TFLITE_MODEL_FILENAME)
        } catch (e: Exception) {
            Log.e(MAIN_ACTIVITY_TAG, "Error initializing ActivityClassifier: ${e.message}", e)
            currentActivityGuess = "Error: Classifier Init Failed"
            null
        }

        // --- Initialize AccelerometerFlowManager ---
        // Use the test instance if provided, otherwise create the real one.
        accelerometerFlowManager = testAccelerometerFlowManager ?: AccelerometerFlowManager(
            sharedScope = lifecycleScope, // Use activity's lifecycle scope for the manager's shared flow
            dataSource = RealAccelerometerDataSource(applicationContext, SENSOR_SAMPLING_RATE_HZ)
        )

        // Start collecting data only if the classifier initialized successfully
        if (activityClassifier != null) {
            startCollectingAccelerometerData()
        } else {
            Log.e(MAIN_ACTIVITY_TAG, "Accelerometer data collection NOT started due to classifier init failure.")
            if (currentActivityGuess == "Initializing...") { // Only update if not already set by classifier error
                currentActivityGuess = "Classifier Error"
            }
        }

        setContent {
            WalkRunClassifierTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
                }
            }
        }
    }

    private fun startCollectingAccelerometerData() {
        Log.d(MAIN_ACTIVITY_TAG, "Starting accelerometer data collection.")
        // Cancel any existing job to prevent multiple collectors
        windowedDataJob?.cancel()

        windowedDataJob = accelerometerFlowManager.getWindowedAccelerometerDataFlow(
            windowSize = ACC_WINDOW_SIZE,
            windowStep = ACC_WINDOW_STEP
        )
            .onEach { window ->
                processedWindowCount++ // Use `this.processedWindowCount` if there's ambiguity
                Log.d(
                    MAIN_ACTIVITY_TAG,
                    "Sensor Window #${this.processedWindowCount}: ${window.readings.size} readings."
                )
                processAccelerometerWindowWithTFLite(window)
            }
            .launchIn(lifecycleScope) // Collect in the activity's lifecycle scope
    }

    private fun processAccelerometerWindowWithTFLite(window: AccelerometerWindow) {
        val currentClassifier = activityClassifier
        if (currentClassifier == null) {
            Log.w(MAIN_ACTIVITY_TAG, "Classifier not initialized, skipping TFLite processing.")
            currentActivityGuess = "Error: Classifier Not Ready"
            return
        }

        if (window.readings.size != currentClassifier.modelExpectedInputSamples) {
            Log.w(
                MAIN_ACTIVITY_TAG,
                "Window size mismatch: Got ${window.readings.size}, expected ${currentClassifier.modelExpectedInputSamples}. Skipping."
            )
            // Consider if this should be a more persistent error state in UI
            // currentActivityGuess = "Error: Window Size Mismatch (${window.readings.size})"
            return // Skip this window
        }

        lifecycleScope.launch(Dispatchers.Default) { // Use Default for CPU-intensive work
            val modelInputData = window.readings.map { accData -> // No need to specify AccelerometerData type here explicitly
                ModelInputReading( // Alias for ml.AccelerometerReading
                    timestamp = accData.timestamp,
                    x = accData.x,
                    y = accData.y,
                    z = accData.z,
                )
            }

            val classificationResult: Pair<String, Float>? = currentClassifier.classify(modelInputData)

            withContext(Dispatchers.Main) { // Switch back to Main thread to update UI
                if (classificationResult != null) {
                    val (label, confidence) = classificationResult
                    this@MainActivity.currentActivityGuess = "$label (${String.format("%.2f", confidence)})" // Use `this@MainActivity`
                    Log.i(MAIN_ACTIVITY_TAG, "TFLite Prediction: $label, Confidence: $confidence")
                } else {
                    // Avoid rapidly changing to "Classification Failed" for every null result if it's frequent
                    // Log it, but maybe don't overwrite a previous valid guess immediately unless it's a persistent failure
                    Log.w(MAIN_ACTIVITY_TAG, "TFLite classification returned null.")
                    // this@MainActivity.currentActivityGuess = "Classification Failed"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the job if it's active.
        // `launchIn(lifecycleScope)` should handle this when the scope is cancelled,
        // but explicit cancellation is also fine.
        windowedDataJob?.cancel()
        Log.d(MAIN_ACTIVITY_TAG, "onDestroy: windowedDataJob cancelled.")

        // Clean up test instance if it was set
        if (testAccelerometerFlowManager === accelerometerFlowManager) { // Check if it's the one we are using
            testAccelerometerFlowManager = null
        }
    }
}