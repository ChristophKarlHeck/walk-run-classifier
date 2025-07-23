package com.example.walkrunclassifier.sensors


import kotlinx.coroutines.flow.Flow

interface AccelerometerDataSource {
    fun getAccelerometerData(): Flow<AccelerometerData>
    // You could also have start/stop methods if your data source needs explicit management,
    // but a Flow often handles this naturally.
}