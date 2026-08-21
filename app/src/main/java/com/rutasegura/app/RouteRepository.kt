package com.rutasegura.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RouteRepository {

    data class Point(
        val lat: Double,
        val lng: Double,
        val time: Long
    )

    private val _points = MutableStateFlow<List<Point>>(emptyList())
    val points: StateFlow<List<Point>> = _points

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking

    @Synchronized
    fun addPoint(lat: Double, lng: Double) {
        _points.value = _points.value + Point(lat, lng, System.currentTimeMillis())
    }

    @Synchronized
    fun start() {
        _points.value = emptyList()
        _tracking.value = true
    }

    @Synchronized
    fun stop() {
        _tracking.value = false
    }
}
