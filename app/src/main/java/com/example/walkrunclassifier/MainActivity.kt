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


// Constants for windowing parameters
private const val DESIRED_WINDOW_DURATION_SECONDS = 10
private const val SENSOR_SAMPLING_RATE_HZ = 50 // Approximate for SENSOR_DELAY_GAME
private const val CLASSIFICATION_INTERVAL_SECONDS = 1

private const val ACC_WINDOW_SIZE = DESIRED_WINDOW_DURATION_SECONDS * SENSOR_SAMPLING_RATE_HZ
private const val ACC_WINDOW_STEP = CLASSIFICATION_INTERVAL_SECONDS * SENSOR_SAMPLING_RATE_HZ

private const val MAIN_ACTIVITY_TAG = "MainActivity" // <--- DECLARE TAG

class MainActivity : ComponentActivity() {

    private lateinit var accelerometerFlowManager: AccelerometerFlowManager
    private var windowedDataJob: Job? = null // <--- DECLARE JOB

    // Example state for UI update
    private var processedWindowCount by mutableStateOf(0) // <--- DECLARE for UI
    private var currentActivityGuess by mutableStateOf("Initializing...") // <--- Example state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("ON Created")
        Log.d("MyAppTest", "MainActivity onCreate CALLED")

        enableEdgeToEdge()

        // Initialize accelerometerFlowManager HERE
        accelerometerFlowManager = AccelerometerFlowManager(applicationContext, lifecycleScope)

        // Start collecting the flow HERE
        windowedDataJob = accelerometerFlowManager.getWindowedAccelerometerDataFlow(
            windowSize = ACC_WINDOW_SIZE,
            windowStep = ACC_WINDOW_STEP
        )
            // .distinctUntilChanged()
            .onEach { window ->
                processedWindowCount++ // This will now update the Compose UI if used
                Log.d(
                    MAIN_ACTIVITY_TAG,
                    "Overlap Window #${processedWindowCount}: ${window.readings.size} readings, First ts: ${window.readings.firstOrNull()?.timestamp}, Last ts: ${window.readings.lastOrNull()?.timestamp}"
                )
                processAccelerometerWindow(window)
            }
            .launchIn(lifecycleScope) // lifecycleScope is available here

        setContent {
            WalkRunClassifierTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Example UI to show the data
                    Column(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
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

// Keep your Greeting and Preview if you want to use them elsewhere,
// or adapt the UI inside setContent as shown in the Column example.
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    println("ON Preview")
//    WalkRunClassifierTheme {
//        Greeting("Android")
//    }
//}
