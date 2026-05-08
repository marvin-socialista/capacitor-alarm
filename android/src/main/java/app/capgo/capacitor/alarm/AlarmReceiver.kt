package app.capgo.capacitor.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver that wakes up when a scheduled AlarmManager alarm fires.
 * Immediately starts [AlarmForegroundService] which plays audio on the ALARM
 * stream (bypasses DND/silent) and shows a full-screen notification.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_ALARM = "app.capgo.capacitor.alarm.ACTION_FIRE_ALARM"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SOUND = "sound"

        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_ALARM) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }

        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: "unknown"
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val sound = intent.getStringExtra(EXTRA_SOUND)

        Log.d(TAG, "Alarm received: id=$alarmId label='$label' sound=${sound ?: "default"}")

        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmForegroundService.EXTRA_LABEL, label)
            if (sound != null) putExtra(AlarmForegroundService.EXTRA_SOUND, sound)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
