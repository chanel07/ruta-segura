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

    private const val STOP_RADIUS_METERS = 30.0
    private const val STOP_TIME_MS = 5 * 60 * 1000L

    // --- Filtro de calidad GPS ---
    // Descarta saltos imposibles: si un punto implica velocidad mayor a esto,
    // es un error del GPS, no un movimiento real (150 km/h caminando/moto = imposible).
    private const val MAX_SPEED_MPS = 42.0   // ~150 km/h
    // Ignora micro-movimientos menores a esto (ruido del GPS estando quieto).
    private const val MIN_MOVE_METERS = 5.0

    private val _points = MutableStateFlow<List<Point>>(emptyList())
    val points: StateFlow<List<Point>> = _points

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking

    private val _alertState = MutableStateFlow(AlertState.IDLE)
    val alertState: StateFlow<AlertState> = _alertState

    private val _contact = MutableStateFlow("")
    val contact: StateFlow<String> = _contact

    private var anchorLat = 0.0
    private var anchorLng = 0.0
    private var anchorTime = 0L
    private var hasAnchor = false

    fun setContact(number: String) { _contact.value = number }

    fun lastPoint(): Point? = _points.value.lastOrNull()

    @Synchronized
    fun addPoint(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()

        // --- Filtro de calidad ---
        val last = _points.value.lastOrNull()
        if (last != null) {
            val dist = distanceMeters(last.lat, last.lng, lat, lng)
            val dtSec = (now - last.time) / 1000.0

            // 1) Salto imposible: velocidad irreal = error GPS, descartar.
            if (dtSec > 0 && dist / dtSec > MAX_SPEED_MPS) {
                return
            }
            // 2) Micro-movimiento: si casi no se movió, no ensuciar la línea,
            //    pero SÍ dejamos pasar para la lógica de "quieto" (no return aquí
            //    si es el primer quieto). Para la línea, lo omitimos.
            if (dist < MIN_MOVE_METERS) {
                // Actualiza solo el tiempo del último punto para el reloj de quietud,
                // sin agregar un punto nuevo casi idéntico.
                evaluateStop(lat, lng, now)
                return
            }
        }

        _points.value = _points.value + Point(lat, lng, now)
        evaluateStop(lat, lng, now)
    }

    private fun evaluateStop(lat: Double, lng: Double, now: Long) {
        if (_alertState.value == AlertState.COUNTDOWN ||
            _alertState.value == AlertState.ALERTED) {
            return
        }

        if (!hasAnchor) {
            setAnchor(lat, lng, now)
            return
        }

        val dist = distanceMeters(anchorLat, anchorLng, lat, lng)
        if (dist > STOP_RADIUS_METERS) {
            setAnchor(lat, lng, now)
            _alertState.value = AlertState.NORMAL
        } else {
            if (now - anchorTime >= STOP_TIME_MS) {
                _alertState.value = AlertState.COUNTDOWN
            }
        }
    }

    private fun setAnchor(lat: Double, lng: Double, time: Long) {
        anchorLat = lat
        anchorLng = lng
        anchorTime = time
        hasAnchor = true
    }

    @Synchronized
    fun cancelAlert() {
        _alertState.value = AlertState.NORMAL
        anchorTime = System.currentTimeMillis()
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
        hasAnchor = false
    }

    @Synchronized
    fun stop() {
        _tracking.value = false
        _alertState.value = AlertState.IDLE
        hasAnchor = false
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
