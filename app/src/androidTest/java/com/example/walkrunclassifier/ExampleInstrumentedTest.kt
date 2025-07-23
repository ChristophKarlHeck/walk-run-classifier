package com.example.walkrunclassifier

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.walkrunclassifier.sensors.AccelerometerFlowManager
import com.example.walkrunclassifier.testutils.CsvAccelerometerDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    // Declare testAppScope at the class level
    private lateinit var testAppScope: CoroutineScope

    @Before
    fun setUp() {
        // Initialize testAppScope before each test
        // Using Dispatchers.Default for simplicity. For more control, consider TestDispatchers.
        testAppScope = CoroutineScope(Dispatchers.Default + Job())
        Log.d("InstrumentedTest", "testAppScope initialized.")
    }

    @After
    fun tearDown() {
        // Cancel the scope after each test to clean up coroutines
        testAppScope.cancel()
        Log.d("InstrumentedTest", "testAppScope cancelled.")
        // Clean up the static test instance in MainActivity
        MainActivity.testAccelerometerFlowManager = null
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.walkrunclassifier", appContext.packageName)
    }

    @Test
    fun testStationaryActivity_fromCsv() {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val csvFileName = "stationary_01_26Hz_4g_mg.csv" // Make sure this file exists in androidTest/assets

        Log.d("StationaryCsvTest", "Starting test with CSV: $csvFileName")

        val csvDataSource = CsvAccelerometerDataSource(
            testContext,
            csvFileName,
            emissionDelayMillis = 10L, // Example delay
            skipHeaderLine = false // Adjust if your CSV has a header
        )

        // --- Create and inject the test AccelerometerFlowManager ---
        val testManager = AccelerometerFlowManager(
            sharedScope = testAppScope, // Now using the initialized testAppScope
            dataSource = csvDataSource
        )
        MainActivity.testAccelerometerFlowManager = testManager
        Log.d("StationaryCsvTest", "Test AccelerometerFlowManager injected into MainActivity.")

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // --- Give some time for processing ---
        val processingDelayMillis = 5000L
        Log.d("StationaryCsvTest", "Delaying for $processingDelayMillis ms to allow data processing...")
        runBlocking { delay(processingDelayMillis) }

        // --- Assertions ---
        var actualActivityGuess = ""
        var actualProcessedWindowCount = 0
        scenario.onActivity { activity ->
            actualActivityGuess = activity.currentActivityGuess
            actualProcessedWindowCount = activity.processedWindowCount // Assuming you have this
            Log.d("StationaryCsvTest", "Activity state: Guess='$actualActivityGuess', ProcessedWindows=$actualProcessedWindowCount")
        }


        // Make sure the activity guess is not the initial "Initializing..." or an error state
        assertFalse("Activity guess should not be 'Initializing...'", actualActivityGuess.contains("Initializing...", ignoreCase = true))
        assertFalse("Activity guess should not indicate an error", actualActivityGuess.contains("Error", ignoreCase = true))
        assertTrue("Expected some windows to be processed, count was $actualProcessedWindowCount", actualProcessedWindowCount > 0)

        assertTrue(
            "Expected activity containing 'Stationary' but got '$actualActivityGuess'",
            actualActivityGuess.contains("Stationary", ignoreCase = true)
        )

        Log.d("StationaryCsvTest", "Test assertions passed. Closing scenario.")
        scenario.close()
    }
}
