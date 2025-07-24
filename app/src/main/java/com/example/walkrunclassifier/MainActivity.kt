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
import com.example.walkrunclassifier.sensors.RealAccelerometerDataSource
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
private const val DESIRED_WINDOW_DURATION_SECONDS = 10
private const val SENSOR_SAMPLING_RATE_HZ = 26
private const val CLASSIFICATION_INTERVAL_SECONDS = 1
private const val ACC_WINDOW_SIZE = DESIRED_WINDOW_DURATION_SECONDS * SENSOR_SAMPLING_RATE_HZ
private const val ACC_WINDOW_STEP = CLASSIFICATION_INTERVAL_SECONDS * SENSOR_SAMPLING_RATE_HZ
private const val MAIN_ACTIVITY_TAG = "MainActivity"
private const val TFLITE_MODEL_FILENAME = "model.tflite"

class MainActivity : ComponentActivity() {

    // Singleton that belongs to the class itself, not the instance
    companion object {
        var testAccelerometerFlowManager: AccelerometerFlowManager? = null
    }

    // UI State (public read, private write)
    private var processedWindowCount by mutableIntStateOf(0)
    var currentActivityClassification by mutableStateOf("initializing")
        private set

    private var windowedDataJob: Job? = null
    private var activityClassifier: ActivityClassifier? = null

    private lateinit var accelerometerFlowManager: AccelerometerFlowManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(MAIN_ACTIVITY_TAG, "onCreate CALLED")
        enableEdgeToEdge()

        initActivityClassifier()
        initAccelerometerManager()

        // Start data collection
        if (activityClassifier != null) {
            startCollectingAccelerometerData()
        } else {
            Log.e(MAIN_ACTIVITY_TAG, "Accelerometer data collection " +
                    "NOT started due to classifier init failure.")
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
                        Text(text = "Activity: $currentActivityClassification")
                    }
                }
            }
        }
    }

    private fun initActivityClassifier() {
        Log.d(MAIN_ACTIVITY_TAG, "Initializes the ActivityClassifier using the TFLite model.")

        activityClassifier = try {
            ActivityClassifier(applicationContext, TFLITE_MODEL_FILENAME)
        } catch (e: Exception) {
            Log.e(MAIN_ACTIVITY_TAG, "Error initializing ActivityClassifier: ${e.message}", e)
            currentActivityClassification = "Error: Classifier Init Failed"
            null
        }
    }

    private fun initAccelerometerManager() {
        Log.d(MAIN_ACTIVITY_TAG, "Initialize AccelerometerManager.")

        // Use the test instance if provided, otherwise create the real one.
        // The AccelerometerFlowManager uses a coroutine scope (here: lifecycleScope)
        // to collect and emit accelerometer data asynchronously via a Flow
        // We use applicationContext to collect data independently of any specific activity,
        // allowing the process to continue as long as the app remains alive
        accelerometerFlowManager = testAccelerometerFlowManager ?: AccelerometerFlowManager(
            // Use activity's lifecycle scope for the manager's shared flow
            sharedScope = lifecycleScope,
            dataSource = RealAccelerometerDataSource(applicationContext, SENSOR_SAMPLING_RATE_HZ)
        )
    }

    private fun startCollectingAccelerometerData() {
        Log.d(MAIN_ACTIVITY_TAG, "Starting accelerometer data collection.")

        // Cancel any existing job to prevent multiple collectors
        windowedDataJob?.cancel()

        windowedDataJob = accelerometerFlowManager.getWindowedAccelerometerDataFlow(
            windowSize = ACC_WINDOW_SIZE,
            windowStep = ACC_WINDOW_STEP
        )
            // For every window of sensor data emitted by the flow, perform classification.
            // AccelerometerFlowManager emits data in one coroutine.
            // .onEach { ... }.launchIn(...) collects the data in a separate coroutine.
            // Both coroutines are managed by the same scope (lifecycleScope),
            // but they run independently and communicate via the Flow pipeline
            .onEach { window ->
                processedWindowCount++ // Use `this.processedWindowCount` if there's ambiguity
                Log.d(
                    MAIN_ACTIVITY_TAG,
                    "Sensor Window #${this.processedWindowCount}: " +
                            "${window.readings.size} readings."
                )
                processAccelerometerWindowWithTFLite(window)
            }
            .launchIn(lifecycleScope) // Collect in the activity's lifecycle scope
    }

    private fun processAccelerometerWindowWithTFLite(window: AccelerometerWindow) {
        val currentClassifier = activityClassifier
        if (currentClassifier == null) {
            Log.w(MAIN_ACTIVITY_TAG, "Classifier not initialized, skipping TFLite processing.")
            currentActivityClassification = "Error: Classifier Not Ready"
            return
        }

        if (window.readings.size != currentClassifier.modelExpectedInputSamples) {
            Log.w(
                MAIN_ACTIVITY_TAG,
                "Window size mismatch: Got ${window.readings.size}," +
                        "expected ${currentClassifier.modelExpectedInputSamples}. Skipping."
            )
            return // Skip this window to avoid misclassification
        }

        // Runs on background for safe CPU-heavy background work
        lifecycleScope.launch(Dispatchers.Default) {
            val modelInputData = window.readings.map { accData ->
                ModelInputReading(
                    timestamp = accData.timestamp,
                    x = accData.x,
                    y = accData.y,
                    z = accData.z,
                )
            }

            val classificationResult: Pair<String, Float>? =
                currentClassifier.classify(modelInputData)

            // Switch back to Main thread to update UI
            withContext(Dispatchers.Main) {
                if (classificationResult != null) {
                    val (label, confidence) = classificationResult
                    this@MainActivity.currentActivityClassification =
                        "$label (${"%.2f".format(confidence)})" // Use `this@MainActivity`
                    Log.i(MAIN_ACTIVITY_TAG, "TFLite Prediction: $label, " +
                            "Confidence: $confidence")
                } else {
                    Log.w(MAIN_ACTIVITY_TAG, "TFLite classification returned null.")
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
        // Check if it's the one we are using
        if (testAccelerometerFlowManager === accelerometerFlowManager) {
            testAccelerometerFlowManager = null
        }
    }
}