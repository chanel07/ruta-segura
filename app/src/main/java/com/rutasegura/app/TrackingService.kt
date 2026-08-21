package com.rutasegura.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrackingService : Service() {

    companion object {
        private const val CHANNEL_ID = "ruta_tracking"
        private const val CHANNEL_ALERT = "ruta_alert"
        private const val NOTIF_ID = 201
        private const val INTERVAL_MS = 4000L
        private const val MAX_ACCURACY_METERS = 50f
        const val ACTION_IM_OK = "com.rutasegura.app.IM_OK"
    }

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null
    private var countdownTimer: CountDownTimer? = null
    private var countingDown = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                if (loc.hasAccuracy() && loc.accuracy <= MAX_ACCURACY_METERS) {
                    RouteRepository.addPoint(loc.latitude, loc.longitude)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        startForegroundNotification("Ruta Segura activa", "Siguiendo tu recorrido...")

        scope.launch {
            RouteRepository.alertState.collect { state ->
                when (state) {
                    RouteRepository.AlertState.COUNTDOWN -> startCountdown()
                    RouteRepository.AlertState.ALERTED -> {
                        countingDown = false
                        stopAlarm(); showAlertNotification()
                    }
                    RouteRepository.AlertState.NORMAL -> {
                        countingDown = false
                        stopAlarm(); countdownTimer?.cancel()
                    }
                    else -> {
                        countingDown = false
                        stopAlarm(); countdownTimer?.cancel()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_IM_OK) {
            RouteRepository.cancelAlert()
            return START_STICKY
        }
        RouteRepository.start()
        startLocationUpdates()
        return START_STICKY
    }

    private fun startCountdown() {
        if (countingDown) return
        countingDown = true
        startAlarm()
        RouteRepository.setCountdown(30)
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(30_000, 1000) {
            override fun onTick(msLeft: Long) {
                val secs = (msLeft / 1000).toInt()
                RouteRepository.setCountdown(secs)
                startForegroundNotification(
                    "¿Estás bien?",
                    "Alerta en ${secs}s. Toca para responder."
                )
            }
            override fun onFinish() {
                countingDown = false
                stopAlarm()
                RouteRepository.triggerAlert()
            }
        }.start()
    }

    private fun startAlarm() {
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
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (e: Exception) { }
    }

    private fun stopAlarm() {
        try { vibrator?.cancel() } catch (e: Exception) { }
        try { ringtone?.stop() } catch (e: Exception) { }
    }

    private fun startForegroundNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Seguimiento Ruta Segura",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPending)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showAlertNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ALERT, "Alerta Ruta Segura",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fullScreenPending = PendingIntent.getActivity(
            this, 1, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = Notification.Builder(this, CHANNEL_ALERT)
            .setContentTitle("⚠ Alerta activada")
            .setContentText("Toca para avisar a tu contacto")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPending, true)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(202, notif)
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(INTERVAL_MS)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countingDown = false
        stopAlarm()
        countdownTimer?.cancel()
        fusedClient.removeLocationUpdates(locationCallback)
        RouteRepository.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
