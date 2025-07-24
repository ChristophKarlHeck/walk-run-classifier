# WalkRunClassifier

An Android application that classifies stationary, walking, and running activity in real-time
using accelerometer sensor data and an on-device TensorFlow Lite model.

**Repository:** [https://github.com/ChristophKarlHeck/walk-run-classifier.git](https://github.com/ChristophKarlHeck/walk-run-classifier.git)

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
stationary, walking, or running. The predicted activity and its confidence score are 
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
- Display of predicted activity label (Stationary, Walking, Running) and confidence score plus the number of processed windows.
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
    - AndroidX Test (JUnit, Espresso Core)
    - Compose UI Tests
- [Android Studio](https://developer.android.com/studio) (Developed with Android Studio Iguana | 2023.2.1 or similar)

## Getting Started

To get a local copy up and running, follow these simple steps.

### Prerequisites

- Android Studio (e.g., Iguana | 2023.2.1 or later recommended).
- Android SDK Platform for API level 34 (or 36 if available) installed.
- An Android device (API 24+) or emulator with an accelerometer sensor.
    - For physical devices: Enable Developer Options and USB Debugging.

### Installation

1.  **Clone the repository:**
    