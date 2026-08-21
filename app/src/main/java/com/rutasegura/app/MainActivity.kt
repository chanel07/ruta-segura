package com.rutasegura.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var pointsText: TextView
    private lateinit var toggleButton: Button

    private var pendingStart = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted =
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted && pendingStart) {
            startTracking()
        } else {
            statusText.text = "Permiso de ubicación denegado."
        }
        pendingStart = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        observeState()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 80, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Ruta Segura"
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        statusText = TextView(this).apply {
            text = "Listo para iniciar"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        pointsText = TextView(this).apply {
            text = "Puntos GPS registrados: 0"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        toggleButton = Button(this).apply {
            text = "INICIAR RECORRIDO"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setOnClickListener { onToggle() }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200
        )
        toggleButton.layoutParams = params

        root.addView(title)
        root.addView(statusText)
        root.addView(pointsText)
        root.addView(toggleButton)
        setContentView(root)
    }

    private fun observeState() {
        lifecycleScope.launch {
            RouteRepository.points.collect { points ->
                pointsText.text = "Puntos GPS registrados: ${points.size}"
                if (points.isNotEmpty()) {
                    val last = points.last()
                    statusText.text = "Siguiendo tu recorrido...\n" +
                            "Último: ${"%.5f".format(last.lat)}, ${"%.5f".format(last.lng)}"
                }
            }
        }
        lifecycleScope.launch {
            RouteRepository.tracking.collect { active ->
                if (active) {
                    toggleButton.text = "DETENER RECORRIDO"
                    toggleButton.setBackgroundColor(Color.parseColor("#B00020"))
                } else {
                    toggleButton.text = "INICIAR RECORRIDO"
                    toggleButton.setBackgroundColor(Color.parseColor("#1B5E20"))
                    statusText.text = "Recorrido detenido"
                }
            }
        }
    }

    private fun onToggle() {
        if (RouteRepository.tracking.value) {
            stopTracking()
        } else {
            requestPermissionsAndStart()
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startTracking()
        } else {
            pendingStart = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startTracking() {
        val intent = Intent(this, TrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)
        statusText.text = "Iniciando GPS..."
    }

    private fun stopTracking() {
        stopService(Intent(this, TrackingService::class.java))
    }
}
