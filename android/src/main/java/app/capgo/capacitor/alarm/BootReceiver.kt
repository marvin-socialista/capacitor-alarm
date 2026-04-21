package app.capgo.capacitor.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED and re-arms every persisted alarm via [AlarmScheduler].
 *
 * AlarmManager drops all pending alarms when the device reboots; without this
 * receiver, alarms scheduled before the reboot silently vanish.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED" &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        Log.d(TAG, "Boot completed — rescheduling persisted alarms")
        AlarmScheduler.rescheduleAllFromStore(context)
    }

    private companion object {
        const val TAG = "AlarmBootReceiver"
    }
}
