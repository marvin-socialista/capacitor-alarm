package app.capgo.capacitor.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that fires the actual alarm:
 *
 *  1. Acquires FULL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP — turns the screen on
 *     even if the device is sleeping.
 *  2. Plays the system default alarm sound on [AudioAttributes.USAGE_ALARM] —
 *     this stream bypasses DND and silent mode on Android.
 *  3. Shows a [NotificationCompat] with a full-screen intent launching
 *     [AlarmActivity] — same pattern as an incoming phone call.
 *  4. Vibrates with a repeating pattern.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val ACTION_START = "app.capgo.capacitor.alarm.START"
        const val ACTION_DISMISS = "app.capgo.capacitor.alarm.DISMISS"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "label"

        const val CHANNEL_ID = "capgo_alarm"
        const val NOTIFICATION_ID = 9001

        private val VIBRATION_PATTERN = longArrayOf(0L, 500L, 200L, 500L)
        private const val WAKE_LOCK_TAG_PREFIX = "capgo:alarm:"
        private const val TAG = "AlarmForegroundService"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibratorRef: Vibrator? = null
    private var vibratorManagerRef: VibratorManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                dismissAlarm()
                return START_NOT_STICKY
            }
            else -> {
                val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Alarm"
                val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID) ?: "unknown"
                startAlarm(alarmId, label)
                // Remove the persisted record for this alarm — it has fired.
                AlarmScheduler.cancelAlarm(applicationContext, alarmId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dismissAlarm()
        super.onDestroy()
    }

    private fun startAlarm(alarmId: String, label: String) {
        Log.d(TAG, "Starting alarm: id=$alarmId label='$label'")

        acquireWakeLock(alarmId)

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmActivity.EXTRA_LABEL, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Tap to dismiss")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_delete, "Dismiss", dismissPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        playAlarmSound()
        vibrate()
    }

    private fun acquireWakeLock(alarmId: String) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock?.run { if (isHeld) release() }
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "$WAKE_LOCK_TAG_PREFIX$alarmId",
        ).apply { acquire(60_000L) }
    }

    private fun playAlarmSound() {
        try {
            val fallback = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, fallback)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound: ${e.message}")
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManagerRef = vm
                val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 1)
                vm.defaultVibrator.vibrate(android.os.CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibratorRef = vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(VIBRATION_PATTERN, 1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate failed: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibratorManagerRef?.cancel()
            @Suppress("DEPRECATION")
            vibratorRef?.cancel()
        } catch (_: Exception) {
            // best effort
        } finally {
            vibratorManagerRef = null
            vibratorRef = null
        }
    }

    fun dismissAlarm() {
        Log.d(TAG, "Dismissing alarm")
        mediaPlayer?.run {
            try { if (isPlaying) stop() } catch (_: IllegalStateException) {}
            release()
        }
        mediaPlayer = null

        stopVibration()

        wakeLock?.run { if (isHeld) release() }
        wakeLock = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alarm notifications that override silent mode"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
