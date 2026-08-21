package com.rutasegura.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var rootLayout: LinearLayout
    private var pendingStart = false
    private var countdownTimer: CountDownTimer? = null

    private val COUNTDOWN_SECONDS = 30L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted =
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingStart) {
            startTracking()
        }
        pendingStart = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 60, 48, 48)
        }
        setContentView(rootLayout)
        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            RouteRepository.alertState.collect { state ->
                render(state)
            }
        }
    }

    private fun render(state: RouteRepository.AlertState) {
        when (state) {
            RouteRepository.AlertState.IDLE -> showIdle()
            RouteRepository.AlertState.NORMAL -> showTracking()
            RouteRepository.AlertState.COUNTDOWN -> showCountdown()
            RouteRepository.AlertState.ALERTED -> showAlerted()
        }
    }

    // ---------- Pantalla inicial ----------

    private fun showIdle() {
        countdownTimer?.cancel()
        rootLayout.removeAllViews()

        addTitle("Ruta Segura")
        addText("Presiona iniciar cuando salgas.\nLa app te acompaña en el camino.", 16f)
        addSpacer(40)

        val startBtn = bigButton("INICIAR RECORRIDO", "#1B5E20") {
            requestPermissionsAndStart()
        }
        rootLayout.addView(startBtn)
    }

    // ---------- Pantalla recorrido normal ----------

    private fun showTracking() {
        countdownTimer?.cancel()
        rootLayout.removeAllViews()

        addTitle("En recorrido")
        addText("La app está vigilando tu trayecto.", 16f)

        val pointsView = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        rootLayout.addView(pointsView)
        lifecycleScope.launch {
            RouteRepository.points.collect { pts ->
                pointsView.text = "Puntos registrados: ${pts.size}"
            }
        }

        addSpacer(48)

        val sosBtn = bigButton("SOS — PEDIR AYUDA", "#B00020") {
            RouteRepository.manualSos()
        }
        rootLayout.addView(sosBtn)

        addSpacer(20)

        val stopBtn = smallButton("Detener recorrido") {
            stopTracking()
        }
        rootLayout.addView(stopBtn)
    }

    // ---------- Cuenta regresiva "¿estás bien?" ----------

    private fun showCountdown() {
        rootLayout.removeAllViews()

        addTitle("¿Estás bien?")
        addText("Detectamos que llevas rato sin moverte.", 16f)
        addSpacer(24)

        val counter = TextView(this).apply {
            textSize = 60f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#B00020"))
        }
        rootLayout.addView(counter)

        addText("Si no respondes, se enviará una alerta.", 15f)
        addSpacer(32)

        val okBtn = bigButton("ESTOY BIEN", "#1B5E20") {
            RouteRepository.cancelAlert()
        }
        rootLayout.addView(okBtn)

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) {
            override fun onTick(msLeft: Long) {
                counter.text = (msLeft / 1000).toString()
            }
            override fun onFinish() {
                RouteRepository.triggerAlert()
            }
        }.start()
    }

    // ---------- Alerta disparada (Fase 2: solo simulada) ----------

    private fun showAlerted() {
        countdownTimer?.cancel()
        rootLayout.removeAllViews()

        addTitle("⚠ ALERTA")
        val banner = TextView(this).apply {
            text = "Aquí se habría enviado la alerta\na tu contacto de confianza.\n\n" +
                    "(En la siguiente fase se envía de verdad.)"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#B00020"))
            setPadding(0, 24, 0, 24)
        }
        rootLayout.addView(banner)

        addSpacer(32)

        val backBtn = bigButton("VOLVER AL RECORRIDO", "#1B5E20") {
            RouteRepository.cancelAlert()
        }
        rootLayout.addView(backBtn)

        addSpacer(16)

        val stopBtn = smallButton("Detener recorrido") {
            stopTracking()
        }
        rootLayout.addView(stopBtn)
    }

    // ---------- Permisos y servicio ----------

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
    }

    private fun stopTracking() {
        stopService(Intent(this, TrackingService::class.java))
    }

    // ---------- Utilidades UI ----------

    private fun addTitle(text: String) {
        val t = TextView(this).apply {
            this.text = text
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(t)
    }

    private fun addText(text: String, size: Float) {
        val t = TextView(this).apply {
            this.text = text
            textSize = size
            gravity = Gravity.CENTER
        }
        rootLayout.addView(t)
    }

    private fun addSpacer(height: Int) {
        val s = View(this)
        s.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, height
        )
        rootLayout.addView(s)
    }

    private fun bigButton(label: String, colorHex: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(colorHex))
            setOnClickListener { onClick() }
        }
        b.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200
        )
        return b
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            textSize = 15f
            setOnClickListener { onClick() }
        }
        return b
    }
}
