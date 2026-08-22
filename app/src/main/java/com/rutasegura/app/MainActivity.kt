package com.rutasegura.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val BG = Color(0xFF0A0E1A)
private val SURFACE = Color(0xFF141A2E)
private val SURFACE_HI = Color(0xFF1E2743)
private val ACCENT = Color(0xFF4F7CFF)
private val ACCENT_GLOW = Color(0xFF6B93FF)
private val DANGER = Color(0xFFFF3B5C)
private val SUCCESS = Color(0xFF2EE6A8)
private val TEXT = Color(0xFFF0F3FA)
private val TEXT_DIM = Color(0xFF8A96B4)

class MainActivity : ComponentActivity() {

    private val pendingStart = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted =
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingStart.value) startTracking()
        pendingStart.value = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            val state by RouteRepository.alertState.collectAsStateWithLifecycle()

            LaunchedEffect(state) {
                when (state) {
                    RouteRepository.AlertState.COUNTDOWN -> wakeScreen()
                    RouteRepository.AlertState.ALERTED -> wakeScreen()
                    else -> clearScreenFlags()
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ACCENT, background = BG, surface = SURFACE
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 4 }) togetherWith fadeOut()
                        },
                        label = "screen"
                    ) { s ->
                        when (s) {
                            RouteRepository.AlertState.IDLE -> IdleScreen()
                            RouteRepository.AlertState.NORMAL -> TrackingScreen()
                            RouteRepository.AlertState.COUNTDOWN -> CountdownScreen()
                            RouteRepository.AlertState.ALERTED -> AlertedScreen()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun IdleScreen() {
        val savedContact by RouteRepository.contact.collectAsStateWithLifecycle()
        var contact by remember { mutableStateOf(savedContact) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BG, Color(0xFF0D1428))))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ACCENT, ACCENT_GLOW))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Shield, null, tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Ruta Segura", color = TEXT, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Tu acompañante en el camino", color = TEXT_DIM, fontSize = 15.sp)
            Spacer(Modifier.height(56.dp))

            Text(
                "Contacto de confianza", color = TEXT_DIM, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                placeholder = { Text("Ej: 573001234567", color = TEXT_DIM) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SURFACE,
                    unfocusedContainerColor = SURFACE,
                    focusedBorderColor = ACCENT,
                    unfocusedBorderColor = SURFACE_HI,
                    focusedTextColor = TEXT,
                    unfocusedTextColor = TEXT
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(36.dp))

            GradientButton(
                text = "Iniciar recorrido",
                icon = Icons.Rounded.PlayArrow,
                colors = listOf(ACCENT, ACCENT_GLOW)
            ) {
                if (contact.trim().length >= 10) {
                    RouteRepository.setContact(contact.trim())
                    requestPermissionsAndStart()
                }
            }
        }
    }

    @Composable
    private fun TrackingScreen() {
        val points by RouteRepository.points.collectAsStateWithLifecycle()
        val debug by RouteRepository.debugInfo.collectAsStateWithLifecycle()

        Column(Modifier.fillMaxSize().background(BG)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(SUCCESS))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "En recorrido", color = SUCCESS, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(debug, color = TEXT_DIM, fontSize = 12.sp)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            minZoomLevel = 12.0
                            maxZoomLevel = 19.0
                            controller.setZoom(17.0)
                        }
                    },
                    update = { map ->
                        if (points.isNotEmpty()) {
                            map.overlays.clear()
                            val line = Polyline().apply {
                                outlinePaint.color = ACCENT.hashCode()
                                outlinePaint.strokeWidth = 14f
                            }
                            points.forEach { line.addPoint(GeoPoint(it.lat, it.lng)) }
                            map.overlays.add(line)
                            val last = points.last()
                            val here = GeoPoint(last.lat, last.lng)
                            map.overlays.add(Marker(map).apply {
                                position = here
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            })
                            map.controller.setCenter(here)
                            map.invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                GradientButton(
                    text = "SOS — Pedir ayuda",
                    icon = Icons.Rounded.Warning,
                    colors = listOf(DANGER, Color(0xFFFF5C78))
                ) { RouteRepository.manualSos() }
                Spacer(Modifier.height(12.dp))
                GhostButton("Detener recorrido") { stopTracking() }
            }
        }
    }

    @Composable
    private fun CountdownScreen() {
        val left by RouteRepository.countdown.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(BG, Color(0xFF2A0A12))))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("¿Estás bien?", color = TEXT, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Llevas un rato sin moverte", color = TEXT_DIM, fontSize = 16.sp)
            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier.size(180.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(SURFACE_HI, SURFACE))),
                contentAlignment = Alignment.Center
            ) {
                Text("$left", color = DANGER, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Si no respondes, se enviará una alerta",
                color = TEXT_DIM, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(44.dp))

            GradientButton(
                text = "Estoy bien",
                icon = Icons.Rounded.Check,
                colors = listOf(SUCCESS, Color(0xFF4FF0BC))
            ) {
                RouteRepository.cancelAlert()
            }
        }
    }

    @Composable
    private fun AlertedScreen() {
        LaunchedEffect(Unit) {
            sendWhatsAppAlert()
        }
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(BG, Color(0xFF2A0A12))))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(DANGER, Color(0xFFFF5C78)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Warning, null, tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Alerta activada", color = TEXT, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Se abrió WhatsApp con tu ubicación", color = TEXT_DIM, fontSize = 15.sp)
            Spacer(Modifier.height(44.dp))

            GradientButton(
                "Llamar al contacto", Icons.Rounded.Call,
                listOf(DANGER, Color(0xFFFF5C78))
            ) { callContact() }
            Spacer(Modifier.height(14.dp))
            GradientButton(
                "Reenviar ubicación", Icons.Rounded.Send,
                listOf(ACCENT, ACCENT_GLOW)
            ) { sendWhatsAppAlert() }
            Spacer(Modifier.height(24.dp))
            GhostButton("Estoy bien, volver") { RouteRepository.cancelAlert() }
            GhostButton("Detener recorrido") { stopTracking() }
        }
    }

    @Composable
    private fun GradientButton(
        text: String,
        icon: ImageVector,
        colors: List<Color>,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            TextButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text, color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    @Composable
    private fun GhostButton(text: String, onClick: () -> Unit) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text, color = TEXT_DIM, fontSize = 15.sp)
        }
    }

    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
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
            setShowWhenLocked(false); setTurnScreenOn(false)
        }
    }

    private fun callContact() {
        val number = RouteRepository.contact.value
        if (number.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: Exception) { }
    }

    private fun sendWhatsAppAlert() {
        val number = RouteRepository.contact.value
        val last = RouteRepository.lastPoint()
        val loc = if (last != null)
            "Mi ubicación: https://maps.google.com/?q=${last.lat},${last.lng}"
        else "No se pudo obtener mi ubicación exacta."
        val msg = "🚨 EMERGENCIA - Ruta Segura 🚨\nNecesito ayuda. Última ubicación:\n$loc"
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/$number?text=${Uri.encode(msg)}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )
        } catch (e: Exception) { }
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
        if (missing.isEmpty()) startTracking()
        else {
            pendingStart.value = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startTracking() {
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
    }

    private fun stopTracking() {
        stopService(Intent(this, TrackingService::class.java))
    }
}
