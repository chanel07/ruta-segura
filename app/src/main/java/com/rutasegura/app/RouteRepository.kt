package com.rutasegura.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RouteRepository {

    data class Point(
        val lat: Double,
        val lng: Double,
        val time: Long
    )

    enum class AlertState {
        IDLE, NORMAL, COUNTDOWN, ALERTED
    }

    private const val MOVE_THRESHOLD_METERS = 40.0
    private const val STOP_TIME_MS = 1 * 60 * 1000L   // 1 min para probar (cambiar a 5 luego)
    private const val MAX_SPEED_MPS = 42.0
    private const val MIN_MOVE_METERS = 5.0

    private val _points = MutableStateFlow<List<Point>>(emptyList())
    val points: StateFlow<List<Point>> = _points

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking

    private val _alertState = MutableStateFlow(AlertState.IDLE)
    val alertState: StateFlow<AlertState> = _alertState

    private val _contact = MutableStateFlow("")
    val contact: StateFlow<String> = _contact

    private val _debugInfo = MutableStateFlow("esperando GPS...")
    val debugInfo: StateFlow<String> = _debugInfo

    private val _countdown = MutableStateFlow(30)
    val countdown: StateFlow<Int> = _countdown
    fun setCountdown(seconds: Int) { _countdown.value = seconds }

    private var pointCount = 0

    private var refLat = 0.0
    private var refLng = 0.0
    private var refTime = 0L
    private var hasRef = false

    private var lastRawLat = 0.0
    private var lastRawLng = 0.0
    private var lastRawTime = 0L
    private var hasRaw = false

    fun setContact(number: String) { _contact.value = number }

    fun lastPoint(): Point? = _points.value.lastOrNull()

    @Synchronized
    fun addPoint(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        pointCount++

        if (hasRaw) {
            val d = distanceMeters(lastRawLat, lastRawLng, lat, lng)
            val dt = (now - lastRawTime) / 1000.0
            if (dt > 0 && d / dt > MAX_SPEED_MPS) {
                _debugInfo.value = "pts:$pointCount (salto descartado)"
                return
            }
        }
        lastRawLat = lat; lastRawLng = lng; lastRawTime = now; hasRaw = true

        val lastDrawn = _points.value.lastOrNull()
        if (lastDrawn == null ||
            distanceMeters(lastDrawn.lat, lastDrawn.lng, lat, lng) >= MIN_MOVE_METERS) {
            _points.value = _points.value + Point(lat, lng, now)
        }

        evaluateStop(lat, lng, now)
    }

    private fun evaluateStop(lat: Double, lng: Double, now: Long) {
        if (_alertState.value == AlertState.COUNTDOWN ||
            _alertState.value == AlertState.ALERTED) {
            return
        }

        if (!hasRef) {
            refLat = lat; refLng = lng; refTime = now; hasRef = true
            _debugInfo.value = "pts:$pointCount ref fijada"
            return
        }

        val distFromRef = distanceMeters(refLat, refLng, lat, lng)
        val quietoSeg = (now - refTime) / 1000

        _debugInfo.value = "pts:$pointCount | dist:${distFromRef.toInt()}m | quieto:${quietoSeg}s"

        if (distFromRef > MOVE_THRESHOLD_METERS) {
            refLat = lat; refLng = lng; refTime = now
            if (_alertState.value != AlertState.NORMAL) {
                _alertState.value = AlertState.NORMAL
            }
        } else {
            if (now - refTime >= STOP_TIME_MS) {
                _countdown.value = 30        // inicializa ANTES de cambiar estado
                _alertState.value = AlertState.COUNTDOWN
            }
        }
    }

    @Synchronized
    fun cancelAlert() {
        _alertState.value = AlertState.NORMAL
        refTime = System.currentTimeMillis()
        if (hasRaw) { refLat = lastRawLat; refLng = lastRawLng }
    }

    @Synchronized
    fun triggerAlert() { _alertState.value = AlertState.ALERTED }

    @Synchronized
    fun manualSos() {
        _countdown.value = 30
        _alertState.value = AlertState.COUNTDOWN   // SOS ahora también pasa por cuenta regresiva
    }

    @Synchronized
    fun start() {
        _points.value = emptyList()
        _tracking.value = true
        _alertState.value = AlertState.NORMAL
        hasRef = false
        hasRaw = false
        pointCount = 0
        _debugInfo.value = "esperando GPS..."
    }

    @Synchronized
    fun stop() {
        _tracking.value = false
        _alertState.value = AlertState.IDLE
        hasRef = false
        hasRaw = false
    }

    private fun distanceMeters(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
