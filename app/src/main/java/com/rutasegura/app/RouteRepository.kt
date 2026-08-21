package com.rutasegura.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RouteRepository {

    data class Point(
        val lat: Double,
        val lng: Double,
        val time: Long
    )

    // Estados posibles del monitoreo.
    enum class AlertState {
        IDLE,        // no hay recorrido activo
        NORMAL,      // moviéndose normal
        COUNTDOWN,   // detectó algo raro, cuenta regresiva "¿estás bien?"
        ALERTED      // no se canceló: se dispararía la alerta
    }

    // --- Parámetros de detección (Fase 2) ---
    private const val STOP_RADIUS_METERS = 30.0      // dentro de esto = "no se movió"
    private const val STOP_TIME_MS = 5 * 60 * 1000L  // 5 minutos detenido

    private val _points = MutableStateFlow<List<Point>>(emptyList())
    val points: StateFlow<List<Point>> = _points

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking

    private val _alertState = MutableStateFlow(AlertState.IDLE)
    val alertState: StateFlow<AlertState> = _alertState

    // Ancla: el punto donde la persona "se quedó quieta".
    private var anchorLat = 0.0
    private var anchorLng = 0.0
    private var anchorTime = 0L
    private var hasAnchor = false

    @Synchronized
    fun addPoint(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        _points.value = _points.value + Point(lat, lng, now)

        // Si estamos en cuenta regresiva o ya alertado, no re-evaluar movimiento.
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
            // Se movió: reinicia el ancla, todo normal.
            setAnchor(lat, lng, now)
            _alertState.value = AlertState.NORMAL
        } else {
            // Sigue cerca del ancla: ¿cuánto tiempo lleva quieto?
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

    /** La persona confirmó que está bien: vuelve a normal. */
    @Synchronized
    fun cancelAlert() {
        _alertState.value = AlertState.NORMAL
        anchorTime = System.currentTimeMillis()  // reinicia el reloj de quietud
    }

    /** No se canceló la cuenta regresiva: se dispara la alerta. */
    @Synchronized
    fun triggerAlert() {
        _alertState.value = AlertState.ALERTED
    }

    /** SOS manual: salta directo a alerta. */
    @Synchronized
    fun manualSos() {
        _alertState.value = AlertState.ALERTED
    }

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

    // Distancia entre dos coordenadas en metros (fórmula de Haversine).
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
