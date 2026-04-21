package app.capgo.capacitor.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wrapper around Android's AlarmManager that schedules exact alarms owned by
 * this app and persists their metadata so they can be listed, cancelled, or
 * restored after reboot.
 *
 * Uses `setAlarmClock` which:
 *  - Fires even in Doze mode (exits Doze for the alarm window)
 *  - Shows an alarm clock icon in the status bar
 *  - Provides the strongest delivery guarantee for user-visible alarms
 */
internal object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val PREFS_NAME = "capgo_alarms"
    private const val PREF_ALARMS_KEY = "scheduled_alarms"

    data class AlarmRecord(
        val id: String,
        val label: String?,
        val triggerAt: Long,
        val hour: Int,
        val minute: Int,
    )

    fun scheduleAlarm(context: Context, id: String, label: String, triggerAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            throw IllegalStateException("SCHEDULE_EXACT_ALARM permission not granted — alarm '$id' cannot be scheduled")
        }

        val pendingIntent = buildPendingIntent(context, id, label)
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerAt, null)
        alarmManager.setAlarmClock(alarmInfo, pendingIntent)

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = triggerAt }
        val record = AlarmRecord(
            id = id,
            label = label,
            triggerAt = triggerAt,
            hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
            minute = cal.get(java.util.Calendar.MINUTE),
        )
        persistRecord(context, record)

        Log.d(TAG, "Scheduled alarm '$id' at $triggerAt (${java.util.Date(triggerAt)})")
    }

    fun cancelAlarm(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(
            context, id, label = null,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        removeRecord(context, id)
        Log.d(TAG, "Cancelled alarm '$id'")
    }

    fun cancelAllAlarms(context: Context) {
        val records = loadRecords(context)
        for (record in records) {
            cancelAlarm(context, record.id)
        }
        clearRecords(context)
        Log.d(TAG, "Cancelled all ${records.size} alarms")
    }

    fun listAlarms(context: Context): List<AlarmRecord> = loadRecords(context)

    /**
     * Re-arm every persisted alarm. Called by [BootReceiver] — AlarmManager drops
     * all pending alarms when the device reboots, so we need to reschedule from
     * our SharedPreferences store.
     */
    fun rescheduleAllFromStore(context: Context) {
        val records = loadRecords(context)
        val now = System.currentTimeMillis()
        var rescheduled = 0
        var dropped = 0
        for (record in records) {
            if (record.triggerAt <= now) {
                dropped++
                removeRecord(context, record.id)
                continue
            }
            try {
                scheduleAlarm(context, record.id, record.label ?: "Alarm", record.triggerAt)
                rescheduled++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reschedule alarm '${record.id}': ${e.message}")
            }
        }
        Log.d(TAG, "Reschedule after boot: rescheduled=$rescheduled dropped=$dropped")
    }

    // ── PendingIntent helper ──────────────────────────────────────────────────

    private fun buildPendingIntent(
        context: Context,
        id: String,
        label: String?,
        flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
            if (label != null) putExtra(AlarmReceiver.EXTRA_LABEL, label)
        }
        val requestCode = id.hashCode()
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    // ── SharedPreferences ─────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadRecords(context: Context): List<AlarmRecord> {
        val raw = prefs(context).getString(PREF_ALARMS_KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                AlarmRecord(
                    id = obj.optString("id"),
                    label = obj.optString("label").takeIf { it.isNotEmpty() },
                    triggerAt = obj.optLong("triggerAt"),
                    hour = obj.optInt("hour"),
                    minute = obj.optInt("minute"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistRecord(context: Context, record: AlarmRecord) {
        val existing = loadRecords(context).filter { it.id != record.id }.toMutableList()
        existing.add(record)
        saveRecords(context, existing)
    }

    private fun removeRecord(context: Context, id: String) {
        val remaining = loadRecords(context).filter { it.id != id }
        saveRecords(context, remaining)
    }

    private fun saveRecords(context: Context, records: List<AlarmRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("label", r.label ?: "")
                put("triggerAt", r.triggerAt)
                put("hour", r.hour)
                put("minute", r.minute)
            })
        }
        prefs(context).edit().putString(PREF_ALARMS_KEY, arr.toString()).apply()
    }

    private fun clearRecords(context: Context) {
        prefs(context).edit().remove(PREF_ALARMS_KEY).apply()
    }
}
