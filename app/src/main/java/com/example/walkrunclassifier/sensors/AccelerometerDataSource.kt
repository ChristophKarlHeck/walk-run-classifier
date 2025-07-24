package com.example.walkrunclassifier.sensors


import kotlinx.coroutines.flow.Flow

interface AccelerometerDataSource {
    fun getAccelerometerData(): Flow<AccelerometerData>
}