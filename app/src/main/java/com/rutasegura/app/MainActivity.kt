package com.rutasegura.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Paleta
    private val COLOR_BG = Color.parseColor("#0D1B2A")        // azul muy oscuro (fondo)
    private val COLOR_CARD = Color.parseColor("#1B263B")      // azul oscuro (tarjetas)
    private val COLOR_PRIMARY = Color.parseColor("#2E5EAA")   // azul principal
    private val COLOR_PRIMARY_D = Color.parseColor("#1B3A6B") // azul oscuro botón
    private val COLOR_DANGER = Color.parseColor("#C1121F")    // rojo SOS/alerta
    private val COLOR_SUCCESS = Color.parseColor("#2A9D8F")   // verde "estoy bien"
    private val COLOR_TEXT = Color.parseColor("#E0E6ED")      // texto claro
    private val COLOR_TEXT_DIM = Color.parseColor("#9DB2CE")  // texto tenue

    private lateinit var rootLayout: LinearLayout
    private var pendingStart = false
    private var countdownTimer: CountDownTimer? = null
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null

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
        vibrator = getSystemService(Vibrator::class.java)
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(56, 72, 56, 56)
            setBackgroundColor(COLOR_BG)
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
            RouteRepository.AlertState.IDLE -> {
                clearScreenFlags(); showIdle()
            }
            RouteRepository.AlertState.NORMAL -> {
                clearScreenFlags(); showTracking()
            }
            RouteRepository.AlertState.COUNTDOWN -> {
                wakeScreen(); showCountdown()
            }
            RouteRepository.AlertState.ALERTED -> {
                wakeScreen(); showAlerted()
            }
        }
    }

    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun clearScreenFlags() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    // ---------- Pantalla inicial ----------

    private fun showIdle() {
        countdownTimer?.cancel()
        stopAlarmSignal()
        rootLayout.removeAllViews()

        addLogo()
        addTitle("Ruta Segura")
        addText("Tu acompañante en el camino", 15f, COLOR_TEXT_DIM)
        addSpacer(48)

        addText("Contacto de confianza", 14f, COLOR_TEXT_DIM)
        addSpacer(8)

        val contactField = EditText(this).apply {
            hint = "Ej: 573001234567"
            setText(RouteRepository.contact.value)
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_TEXT_DIM)
            background = roundedBg(COLOR_CARD, 24)
            setPadding(32, 32, 32, 32)
        }
        rootLayout.addView(contactField, fullWidth())
        addSpacer(40)

        val startBtn = styledButton("INICIAR RECORRIDO", COLOR_PRIMARY) {
            val number = contactField.text.toString().trim()
            if (number.length < 10) {
                Toast.makeText(this, "Escribe un número válido con código de país", Toast.LENGTH_LONG).show()
            } else {
                RouteRepository.setContact(number)
                requestPermissionsAndStart()
            }
        }
        rootLayout.addView(startBtn)
    }

    // ---------- Recorrido normal ----------

    private fun showTracking() {
        countdownTimer?.cancel()
        stopAlarmSignal()
        rootLayout.removeAllViews()

        addStatusDot(COLOR_SUCCESS)
        addTitle("En recorrido")
        addText("La app está cuidando tu trayecto", 15f, COLOR_TEXT_DIM)
        addSpacer(16)

        val pointsView = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT_DIM)
        }
        rootLayout.addView(pointsView)
        lifecycleScope.launch {
            RouteRepository.points.collect { pts ->
                pointsView.text = "Ubicaciones registradas: ${pts.size}"
            }
        }

        addSpacer(56)

        val sosBtn = styledButton("SOS — PEDIR AYUDA", COLOR_DANGER) {
            RouteRepository.manualSos()
        }
        rootLayout.addView(sosBtn)

        addSpacer(20)

        val stopBtn = ghostButton("Detener recorrido") {
            stopTracking()
        }
        rootLayout.addView(stopBtn)
    }

    // ---------- Cuenta regresiva ----------

    private fun showCountdown() {
        rootLayout.removeAllViews()

        addTitle("¿Estás bien?")
        addText("Llevas un rato sin moverte", 16f, COLOR_TEXT_DIM)
        addSpacer(32)

        val counter = TextView(this).apply {
            textSize = 72f
            gravity = Gravity.CENTER
            setTextColor(COLOR_DANGER)
        }
        rootLayout.addView(counter)
        addSpacer(8)

        addText("Si no respondes, se enviará una alerta", 14f, COLOR_TEXT_DIM)
        addSpacer(40)

        val okBtn = styledButton("ESTOY BIEN", COLOR_SUCCESS) {
            stopAlarmSignal()
            RouteRepository.cancelAlert()
        }
        rootLayout.addView(okBtn)

        startAlarmSignal()

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) {
            override fun onTick(msLeft: Long) {
                counter.text = (msLeft / 1000).toString()
            }
            override fun onFinish() {
                stopAlarmSignal()
                RouteRepository.triggerAlert()
            }
        }.start()
    }

    // ---------- Alerta ----------

    private fun showAlerted() {
        countdownTimer?.cancel()
        stopAlarmSignal()
        rootLayout.removeAllViews()

        addText("⚠", 56f, COLOR_DANGER)
        addTitle("Alerta activada")
        addText("Avisa a tu contacto de confianza", 15f, COLOR_TEXT_DIM)
        addSpacer(40)

        val callBtn = styledButton("LLAMAR AL CONTACTO", COLOR_DANGER) {
            callContact()
        }
        rootLayout.addView(callBtn)
        addSpacer(16)

        val sendBtn = styledButton("ENVIAR UBICACIÓN", COLOR_PRIMARY) {
            sendWhatsAppAlert()
        }
        rootLayout.addView(sendBtn)
        addSpacer(24)

        val backBtn = ghostButton("Estoy bien, volver") {
            RouteRepository.cancelAlert()
        }
        rootLayout.addView(backBtn)
        addSpacer(8)

        val stopBtn = ghostButton("Detener recorrido") {
            stopTracking()
        }
        rootLayout.addView(stopBtn)
    }

    private fun callContact() {
        val number = RouteRepository.contact.value
        if (number.isBlank()) {
            Toast.makeText(this, "No hay contacto guardado", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el marcador", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendWhatsAppAlert() {
        val number = RouteRepository.contact.value
        val last = RouteRepository.lastPoint()

        val locationText = if (last != null) {
            "Mi ubicación: https://maps.google.com/?q=${last.lat},${last.lng}"
        } else {
            "No se pudo obtener mi ubicación exacta."
        }

        val message = "🚨 EMERGENCIA - Ruta Segura 🚨\n" +
                "Necesito ayuda. Esta es mi última ubicación conocida:\n$locationText"

        val url = "https://wa.me/$number?text=${Uri.encode(message)}"

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Sonido + vibración ----------

    private fun startAlarmSignal() {
        try {
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) { }

        try {
            ringtone?.stop()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone?.play()
        } catch (e: Exception) { }
    }

    private fun stopAlarmSignal() {
        try { vibrator?.cancel() } catch (e: Exception) { }
        try { ringtone?.stop() } catch (e: Exception) { }
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
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
    }

    private fun stopTracking() {
        stopService(Intent(this, TrackingService::class.java))
    }

    // ---------- Componentes UI ----------

    private fun addLogo() {
        val logo = TextView(this).apply {
            text = "🛡"
            textSize = 56f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(logo)
    }

    private fun addTitle(text: String) {
        val t = TextView(this).apply {
            this.text = text
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT)
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(t)
    }

    private fun addText(text: String, size: Float, color: Int) {
        val t = TextView(this).apply {
            this.text = text
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(color)
        }
        rootLayout.addView(t)
    }

    private fun addStatusDot(color: Int) {
        val dot = TextView(this).apply {
            text = "●"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(color)
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(dot)
    }

    private fun addSpacer(height: Int) {
        val s = View(this)
        s.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, height
        )
        rootLayout.addView(s)
    }

    private fun fullWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun roundedBg(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun styledButton(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBg(color, 32)
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 170
        )
        params.setMargins(0, 0, 0, 0)
        b.layoutParams = params
        return b
    }

    private fun ghostButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            setTextColor(COLOR_TEXT_DIM)
            background = null
            setOnClickListener { onClick() }
        }
        return b
    }
}
