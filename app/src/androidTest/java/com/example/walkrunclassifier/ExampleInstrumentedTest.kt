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
import kotlin.math.roundToLong


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
        val csvFileName = "stationary.csv"

        // Read data from file with ~26 Hz
        val hz = 26
        val emissionDelayMillis = (1000f / hz).roundToLong()
        val csvDataSource = CsvAccelerometerDataSource(
            testContext,
            csvFileName,
            emissionDelayMillis = emissionDelayMillis, // data rate
            skipHeaderLine = false      // no header in CSV
        )

        // Create and inject the test AccelerometerFlowManager
        val testManager = AccelerometerFlowManager(
            sharedScope = testAppScope,
            dataSource = csvDataSource
        )
        MainActivity.testAccelerometerFlowManager = testManager
        Log.d("StationaryCsvTest", "Test AccelerometerFlowManager " +
                "injected into MainActivity.")

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait 10 seconds
        val processingDelayMillis = 12000L // 10 seconds
        runBlocking { delay(processingDelayMillis) }

        var actualActivityGuess = ""
        scenario.onActivity { actualActivityGuess = it.currentActivityClassification }

        assertTrue(
            "Expected activity containing 'stationary' " +
                    "but got '$actualActivityGuess' after ${processingDelayMillis}ms",
            actualActivityGuess.contains("stationary", ignoreCase = true)
        )

        scenario.close()
    }

    @Test
    fun testWalkingActivity_fromCsv() {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val csvFileName = "walking.csv"

        // Read data from file with ~26 Hz
        val hz = 26
        val emissionDelayMillis = (1000f / hz).roundToLong()
        val csvDataSource = CsvAccelerometerDataSource(
            testContext,
            csvFileName,
            emissionDelayMillis = emissionDelayMillis, // data rate
            skipHeaderLine = false      // no header in CSV
        )

        // Create and inject the test AccelerometerFlowManager
        val testManager = AccelerometerFlowManager(
            sharedScope = testAppScope,
            dataSource = csvDataSource
        )
        MainActivity.testAccelerometerFlowManager = testManager
        Log.d("WalkingCsvTest", "Test AccelerometerFlowManager " +
                "injected into MainActivity.")

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait 10 seconds
        val processingDelayMillis = 12000L // 10 seconds
        runBlocking { delay(processingDelayMillis) }

        var actualActivityGuess = ""
        scenario.onActivity { actualActivityGuess = it.currentActivityClassification }

        assertTrue(
            "Expected activity containing 'walking' but got '$actualActivityGuess' " +
                    "after ${processingDelayMillis}ms",
            actualActivityGuess.contains("walking", ignoreCase = true)
        )

        scenario.close()
    }

    @Test
    fun testRunningActivity_fromCsv() {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val csvFileName = "running.csv"

        // Read data from file with ~26 Hz
        val hz = 26
        val emissionDelayMillis = (1000f / hz).roundToLong()
        val csvDataSource = CsvAccelerometerDataSource(
            testContext,
            csvFileName,
            emissionDelayMillis = emissionDelayMillis, // data rate
            skipHeaderLine = false      // no header in CSV
        )

        // Create and inject the test AccelerometerFlowManager
        val testManager = AccelerometerFlowManager(
            sharedScope = testAppScope,
            dataSource = csvDataSource
        )
        MainActivity.testAccelerometerFlowManager = testManager
        Log.d("RunningCsvTest", "Test AccelerometerFlowManager " +
                "injected into MainActivity.")

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait 10 seconds
        val processingDelayMillis = 12000L // 10 seconds
        runBlocking { delay(processingDelayMillis) }

        var actualActivityGuess = ""
        scenario.onActivity { actualActivityGuess = it.currentActivityClassification }

        assertTrue(
            "Expected activity containing 'running' but got '$actualActivityGuess' " +
                    "after ${processingDelayMillis}ms",
            actualActivityGuess.contains("running", ignoreCase = true)
        )

        scenario.close()
    }
}