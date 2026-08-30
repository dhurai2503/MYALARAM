package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AlarmRingState
import java.io.IOException
import kotlinx.coroutines.*

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volumeEnforcerJob: Job? = null
    private var volumeScaleJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val NOTIFICATION_ID = 4821

        fun stopAlarm(context: Context) {
            val stopIntent = Intent(context, AlarmService::class.java)
            context.stopService(stopIntent)
            AlarmRingState.stopRinging()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        val label = intent?.getStringExtra("ALARM_LABEL") ?: "Wake Up!"
        val toneUriStr = intent?.getStringExtra("ALARM_TONE")

        Log.d("AlarmService", "AlarmService active for ID: $alarmId, label: $label, tone: $toneUriStr")

        // Update active ringing state so UI reacts instantly if open
        AlarmRingState.startRinging(alarmId, label)

        // Show foreground notification
        startForeground(NOTIFICATION_ID, buildNotification(label))

        // Play alarm sound
        playAlarmSound(toneUriStr)

        // Vibrate
        startVibrating()

        // Enforce maximum volume to prevent the user from lowering it
        startVolumeEnforcement()

        // Observe volume scaling changes (e.g. 50% volume when user enters first digit of passcode)
        observeVolumeScale()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AlarmService", "AlarmService destroyed. Stopping sound and vibration.")
        stopVolumeScaleObservation()
        stopVolumeEnforcement()
        stopAlarmSound()
        stopVibrating()
        AlarmRingState.stopRinging()
    }

    private fun calculatePlayerVolume(scale: Float): Float {
        if (scale <= 0.01f) return 0.0f
        if (scale >= 0.99f) return 1.0f
        // Smooth linear volume scaling: 0.40f represents 40% playback volume (down 60%)
        return scale.coerceIn(0.15f, 1.0f)
    }

    private fun playAlarmSound(toneUriStr: String?) {
        stopAlarmSound() // Stop if already playing

        val currentScale = AlarmRingState.volumeScale.value
        val playerVolume = calculatePlayerVolume(currentScale)

        val alarmUri: Uri = if (!toneUriStr.isNullOrEmpty()) {
            try {
                Uri.parse(toneUriStr)
            } catch (e: Exception) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                setVolume(playerVolume, playerVolume)
                start()
            }
            Log.d("AlarmService", "Playing alarm sound successfully at volume scale: $currentScale (actual: $playerVolume)")
        } catch (e: Exception) {
            Log.e("AlarmService", "Error building default alarm media player, trying ringtone fallback", e)
            try {
                // Secondary fallback
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer.create(applicationContext, ringtoneUri)?.apply {
                    isLooping = true
                    setVolume(playerVolume, playerVolume)
                    start()
                }
            } catch (e2: Exception) {
                Log.e("AlarmService", "All ringtone / media players failed", e2)
            }
        }
    }

    private fun observeVolumeScale() {
        volumeScaleJob?.cancel()
        volumeScaleJob = CoroutineScope(Dispatchers.Main).launch {
            AlarmRingState.volumeScale.collect { scale ->
                try {
                    val actualVol = calculatePlayerVolume(scale)
                    mediaPlayer?.setVolume(actualVol, actualVol)

                    // Ensure the hardware system volume stream is kept high so the alarm is loud & clear
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    if (audioManager != null) {
                        val maxAlarmVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxAlarmVol, 0)

                        val maxMusicVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                    }

                    Log.d("AlarmService", "Updated alarm playback volume to scale=$scale (playerVol=$actualVol)")
                } catch (e: Exception) {
                    Log.e("AlarmService", "Error setting media player volume scale", e)
                }
            }
        }
    }

    private fun stopVolumeScaleObservation() {
        volumeScaleJob?.cancel()
        volumeScaleJob = null
    }

    private fun startVolumeEnforcement() {
        volumeEnforcerJob?.cancel()
        volumeEnforcerJob = CoroutineScope(Dispatchers.Default).launch {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                while (isActive) {
                    try {
                        val maxAlarmVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxAlarmVol, 0)
                    } catch (e: Exception) {
                        Log.e("AlarmService", "Failed to enforce alarm stream volume", e)
                    }
                    try {
                        val maxMusicVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                    } catch (e: Exception) {
                        Log.e("AlarmService", "Failed to enforce music stream volume", e)
                    }
                    delay(800) // Reinforce stream volume level periodically
                }
            }
        }
    }

    private fun stopVolumeEnforcement() {
        volumeEnforcerJob?.cancel()
        volumeEnforcerJob = null
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {
                Log.e("AlarmService", "Error stopping player", e)
            } finally {
                it.release()
            }
        }
        mediaPlayer = null
    }

    @Suppress("DEPRECATION")
    private fun startVibrating() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (it.hasVibrator()) {
                val pattern = longArrayOf(0, 800, 800, 800) // delay, vibe, silence, vibe
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(pattern, 1))
                } else {
                    it.vibrate(pattern, 1)
                }
            }
        }
    }

    private fun stopVibrating() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Activation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notifications when the passcode alarm triggers"
                setSound(null, null) // Silent notification channel since we manage playback explicitly
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(label: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm Ringing!")
            .setContentText(label)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pendingIntent, true) // Ensure it displays prominently on screen wakes
            .setOngoing(true)
            .build()
    }
}
