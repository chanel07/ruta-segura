package com.rutasegura.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    private var countdownTimer: CountDownTimer? = null
    private val countdownLeft = mutableStateOf(30)
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
                    RouteRepository.AlertState.COUNTDOWN -> { wakeScreen(); startCountdownDisplay() }
                    RouteRepository.AlertState.ALERTED -> wakeScreen()
                    else -> { clearScreenFlags(); countdownTimer?.cancel() }
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = ACCENT, background = BG, surface = SURFACE
            )) {
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
                Icon(Icons.Rounded.Shield, null, tint = Color.White,
                    modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Ruta Segura", color = TEXT, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Tu acompañante en el camino", color = TEXT_DIM, fontSize = 15.sp)
            Spacer(Modifier.height(56.dp))

            Text("Contacto de confianza", color = TEXT_DIM, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                placeholder = { Text("Ej: 573001234567", color = TEXT_DIM) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor
