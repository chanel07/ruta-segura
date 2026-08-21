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

    // Si te alejas más de esto del punto de referencia, cuenta como "te moviste".
    private const val MOVE_THRESHOLD_METERS = 40.0
    // Tiempo quieto (dentro del radio) para disparar la alerta.
    private const val STOP_TIME_MS = 5 * 60 * 1000L
    // Salto imposible = error GPS.
    private const val MAX_SPEED_MPS = 42.0
    // Micro-movimiento: no ensucia la línea del mapa.
    private const val MIN_MOVE_METERS = 5.0

    private val _points = MutableStateFlow<List<Point>>(emptyList())
    val points: StateFlow<List<Point>> = _points

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking

    private val _alertState = MutableStateFlow(AlertState.IDLE)
    val alertState: StateFlow<AlertState> = _alertState

    private val _contact = MutableStateFlow("")
    val contact: StateFlow<String> = _contact

    // Punto de referencia para medir quietud. Se mueve solo cuando de verdad avanzas.
    private var refLat = 0.0
    private var refLng = 0.0
    private var refTime = 0L
    private var hasRef = false

    // Último punto crudo recibido (para filtrar saltos y micro-movimientos de la línea).
    private var lastRawLat = 0.0
    private var lastRawLng = 0.0
    private var lastRawTime = 0L
    private var hasRaw = false

    fun setContact(number: String) { _contact.value = number }

    fun lastPoint(): Point? = _points.value.lastOrNull()

    @Synchronized
    fun addPoint(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()

        // 1) Filtro de salto imposible (error GPS).
        if (hasRaw) {
            val d = distanceMeters(lastRawLat, lastRawLng, lat, lng)
            val dt = (now - lastRawTime) / 1000.0
            if (dt > 0 && d / dt > MAX_SPEED_MPS) {
                return  // descarta punto basura
            }
        }
        lastRawLat = lat; lastRawLng = lng; lastRawTime = now; hasRaw = true

        // 2) Línea del mapa: solo agrega si hubo movimiento apreciable.
        val lastDrawn = _points.value.lastOrNull()
        if (lastDrawn == null ||
            distanceMeters(lastDrawn.lat, lastDrawn.lng, lat, lng) >= MIN_MOVE_METERS) {
            _points.value = _points.value + Point(lat, lng, now)
        }

        // 3) Lógica de quietud (independiente de la línea).
        evaluateStop(lat, lng, now)
    }

    private fun evaluateStop(lat: Double, lng: Double, now: Long) {
        if (_alertState.value == AlertState.COUNTDOWN ||
            _alertState.value == AlertState.ALERTED) {
            return
        }

        if (!hasRef) {
            refLat = lat; refLng = lng; refTime = now; hasRef = true
            return
        }

        val distFromRef = distanceMeters(refLat, refLng, lat, lng)

        if (distFromRef > MOVE_THRESHOLD_METERS) {
            // Se movió de verdad: nuevo punto de referencia, reinicia el reloj.
            refLat = lat; refLng = lng; refTime = now
            if (_alertState.value != AlertState.NORMAL) {
                _alertState.value = AlertState.NORMAL
            }
        } else {
            // Sigue dentro del radio: ¿cuánto lleva quieto?
            if (now - refTime >= STOP_TIME_MS) {
                _alertState.value = AlertState.COUNTDOWN
            }
        }
    }

    @Synchronized
    fun cancelAlert() {
        _alertState.value = AlertState.NORMAL
        // Reinicia el reloj de quietud desde la posición actual.
        refTime = System.currentTimeMillis()
        if (hasRaw) { refLat = lastRawLat; refLng = lastRawLng }
    }

    @Synchronized
    fun triggerAlert() { _alertState.value = AlertState.ALERTED }

    @Synchronized
    fun manualSos() { _alertState.value = AlertState.ALERTED }

    @Synchronized
    fun start() {
        _points.value = emptyList()
        _tracking.value = true
        _alertState.value = AlertState.NORMAL
        hasRef = false
        hasRaw = false
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
