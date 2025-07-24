# WalkRunClassifier

An Android application that classifies stationary, walking, and running activity in real-time
using accelerometer sensor data and an on-device TensorFlow Lite model.

<!-- 
Optional: Add a screenshot or a short GIF of the app in action here 
![App Screenshot](docs/images/app_screenshot.png) 
-->

<!-- 
**Video Demonstrations:**
- How to run tests: [Link to your video when ready]
- How to deploy and run on a real phone: [Link to your video when ready] 
-->

## Table of Contents

- [About The Project](#about-the-project)
- [Features](#features)
- [Built With](#built-with)
- [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Installation](#installation)
- [Usage](#usage)
- [Model Details](#model-details)
- [Data Handling](#data-handling)
    - [Real-time Data](#real-time-data)
    - [Test Data](#test-data)
- [Code Structure](#code-structure)
- [License](#license)
- [Acknowledgements](#acknowledgements)

## About The Project

WalkRunClassifier provides a simple interface to demonstrate on-device activity recognition.
It leverages the built-in accelerometer to gather motion data, preprocesses this data into windows,
and then feeds it to a pre-trained TensorFlow Lite model to predict whether the user is 
stationary, walking, or running. The number of processed windows, predicted activity and its confidence score are 
displayed on the screen.

This project is a completed example showcasing:
- Real-time sensor data collection and processing on Android.
- Integration of a TensorFlow Lite model for on-device inference.
- Use of Kotlin Flows and Coroutines for managing asynchronous data streams.
- UI implementation with Jetpack Compose.

## Features

- Real-time accelerometer data collection.
- Configurable sensor sampling rate (currently set around 26Hz).
- Preprocessing:
    - Conversion of raw sensor data (m/s²) to milli-g (mg).
    - Clamping of milli-g values to a ±4000mg range.
    - Windowing of sensor data (e.g., 10-second windows with 90% overlap).
    - Normalization of windowed data (division by 4000.0f) before model input.
- On-device classification using a pre-trained TensorFlow Lite model.
- Display of predicted activity label (stationary, walking, running) and confidence score plus the number of processed windows.
- CSV-based test data replay capability for development and debugging (used in Instrumented Tests).

## Built With

- [Kotlin](https://kotlinlang.org/)
- Android SDK:
    - `minSdk = 24`
    - `compileSdk = 36`
    - `targetSdk = 36`
- [TensorFlow Lite](https://www.tensorflow.org/lite) (`org.tensorflow:tensorflow-lite`)
- Kotlin Coroutines & Flow
- Jetpack Compose:
    - `androidx.compose.ui:ui`
    - `androidx.compose.material3:material3`
    - `androidx.compose.foundation:foundation` (implicitly via BOM)
- Android Jetpack:
    - `androidx.core:core-ktx`
    - `androidx.lifecycle:lifecycle-runtime-ktx`
    - `androidx.activity:activity-compose`
    - `androidx.lifecycle:lifecycle-viewmodel-compose` (Used in `MainActivity`)
- Testing:
    - JUnit 4
    - AndroidX Test (JUnit)
    - Compose UI Tests
- [Android Studio](https://developer.android.com/studio) (Developed with Android Studio Narwhal | 2025.1.1)

## Getting Started

To get a local copy up and running, follow these simple steps.

### Prerequisites

- Android Studio (e.g., Iguana | 2023.2.1 or later recommended).
- Android SDK Platform for API level 34 (or 36 if available) installed.
- An Android device (API 24+) or emulator with an accelerometer sensor.
    - For physical devices: Enable Developer Options and USB Debugging.

### Installation

1.  **Clone the repository:**


## Usage

1.  **Launch the App:**
    Find and tap the "WalkRunClassifier" icon in your app drawer or on your home screen.

2.  **Initial State:**
    Upon launching, the screen will initially display "Activity: initializing". This means the app is active and warming up by collecting the initial accelerometer data needed for the first classification window.

3.  **Perform an Activity:**
    To see the app in action, simply begin one of the following activities:
    *   Stay **Stationary** (e.g., sitting or standing still).
    *   Start **Walking**.
    *   Start **Running** or jogging.

4.  **View Classification:**
    After a brief period (new window classification every second), the display will update to show:
    *   The **Predicted Activity**: "Stationary", "Walking", or "Running".
    *   The **Confidence Score**: A value in parentheses (e.g., `(0.92)`) indicating how confident the model is in its prediction.

5.  **Continuous Updates:**
    The app continuously processes accelerometer data. As you continue your activity or switch to a different one, the displayed prediction and confidence score will update accordingly to reflect the current movement pattern. There are no buttons to press; the classification is automatic and ongoing.

## Model Details

The machine learning model used in this application was trained to classify stationary, walking, and running activities based on accelerometer data.
For comprehensive information regarding the model's architecture, training process, dataset used, and experimentation details, please refer to the dedicated model training repository:

**➡️ [Human Activity Recognition Model Training Repository](https://github.com/ChristophKarlHeck/human-activity-recognition)**

Below is a summary of the model's specifics as integrated into this Android application:

-   **Model File:** The TensorFlow Lite model is located at `app/src/main/assets/model.tflite`.
-   **Input:**
    -   Type: Floating point numbers.
    -   Shape: `[1, 260, 3]`
        -   `1`: Batch size.
        -   `260`: Number of time steps/samples in the window (10 seconds * 26 Hz).
        -   `3`: Features per time step (x, y, z accelerometer values).
    -   Preprocessing (within this Android app):
        1.  Raw accelerometer (x, y, z) readings are taken in m/s².
        2.  Converted to milli-g (mg) using standard gravity (9.81 m/s²).
        3.  Clamped to a ±4000mg range.
        4.  Collected into windows of 260 samples, with a 90% overlap between consecutive windows.
        5.  Each value (x, y, z) in the window is then normalized by dividing by 4000.0f (resulting in values approximately between -1.0 and 1.0).
-   **Output:**
    -   Type: Floating point numbers representing probabilities.
    -   Shape: `[1, 3]` (Batch size, Number of classes).
    -   Interpretation: The output array contains the confidence scores for each activity class in the following order:
        1.  Stationary
        2.  Walking
        3.  Running
    -   The class with the highest score is chosen as the prediction.



