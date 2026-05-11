package app.capgo.capacitor.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback

/**
 * Capacitor bridge for the CapgoAlarm plugin on Android.
 *
 * Schedules alarms via AlarmManager (owned by this app, cancellable) instead of
 * delegating to the device's default clock app — that gives the consumer full
 * lifecycle control (cancel by id, cancel all).
 *
 * Audio plays on AudioAttributes.USAGE_ALARM which bypasses DND and silent mode.
 */
@CapacitorPlugin(
    name = "CapgoAlarm",
    permissions = [
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = "notifications"),
        Permission(strings = [Manifest.permission.WAKE_LOCK], alias = "wakeLock"),
        Permission(strings = [Manifest.permission.VIBRATE], alias = "vibrate"),
    ]
)
class CapgoAlarmPlugin : Plugin() {

    private val pluginVersion: String = "8.3.1-waif"

    @PluginMethod
    fun createAlarm(call: PluginCall) {
        val id = call.getString("id") ?: java.util.UUID.randomUUID().toString()
        val label = call.getString("label") ?: "Alarm"
        val sound = call.getString("sound")
        val dateStr = call.getString("date")
        val hour = call.getInt("hour")
        val minute = call.getInt("minute")

        val triggerAt: Long = when {
            dateStr != null -> {
                try {
                    parseIso8601(dateStr)
                } catch (e: Exception) {
                    call.reject("Invalid `date`. Expected ISO 8601 string.")
                    return
                }
            }
            hour != null && minute != null -> nextTriggerAt(hour, minute)
            else -> {
                call.reject("Either `date` or both `hour` and `minute` are required")
                return
            }
        }

        try {
            AlarmScheduler.scheduleAlarm(context, id = id, label = label, triggerAt = triggerAt, sound = sound)
            val ret = JSObject().apply {
                put("success", true)
                put("id", id)
                put("message", "Alarm scheduled for ${java.util.Date(triggerAt)}")
            }
            call.resolve(ret)
        } catch (e: IllegalStateException) {
            val ret = JSObject().apply {
                put("success", false)
                put("message", e.message ?: "Scheduling failed")
            }
            call.resolve(ret)
        }
    }

    @PluginMethod
    fun cancelAlarm(call: PluginCall) {
        val id = call.getString("id") ?: run { call.reject("`id` is required"); return }
        AlarmScheduler.cancelAlarm(context, id)
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun cancelAllAlarms(call: PluginCall) {
        AlarmScheduler.cancelAllAlarms(context)
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun openAlarms(call: PluginCall) {
        val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            call.resolve(JSObject().apply { put("success", true) })
        } catch (e: Exception) {
            val ret = JSObject().apply {
                put("success", false)
                put("message", e.message)
            }
            call.resolve(ret)
        }
    }

    @PluginMethod
    fun getOSInfo(call: PluginCall) {
        val ret = JSObject().apply {
            put("platform", "android")
            put("version", Build.VERSION.RELEASE)
            put("supportsNativeAlarms", true)
            put("supportsScheduledNotifications", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                put("canScheduleExactAlarms", am?.canScheduleExactAlarms() == true)
            }
        }
        call.resolve(ret)
    }

    override fun requestPermissions(call: PluginCall) {
        val requestExact = call.getBoolean("exactAlarm") ?: false
        if (requestExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canExact = am?.canScheduleExactAlarms() == true
            if (!canExact) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try { context.startActivity(settingsIntent) } catch (_: Exception) {}
                val details = JSObject().apply { put("exactAlarm", false) }
                call.resolve(JSObject().apply {
                    put("granted", false)
                    put("details", details)
                })
                return
            }
        }

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (notifGranted) {
            call.resolve(JSObject().apply { put("granted", true) })
            return
        }

        requestPermissionForAlias("notifications", call, "permissionsCallback")
    }

    @PermissionCallback
    private fun permissionsCallback(call: PluginCall) {
        val granted = getPermissionState("notifications").toString() == "granted"
        call.resolve(JSObject().apply { put("granted", granted) })
    }

    override fun checkPermissions(call: PluginCall) {
        val ret = JSObject()
        val details = JSObject()
        var granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        var hasDetails = false
        var message: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val canExact = am?.canScheduleExactAlarms() == true
                details.put("exactAlarm", canExact)
                hasDetails = true
                granted = canExact
                if (!canExact) message = "Exact alarm permission not granted"
            } catch (e: Exception) {
                details.put("exactAlarm", false)
                hasDetails = true
                message = "Failed to query exact alarm capability"
                Log.w(TAG, "Unable to determine exact alarm capability status", e)
                granted = false
            }
        } else {
            message = "Exact alarm capability check requires Android S+"
        }

        ret.put("granted", granted)
        if (hasDetails) ret.put("details", details)
        if (message != null) ret.put("message", message)
        call.resolve(ret)
    }

    @PluginMethod
    fun getPluginVersion(call: PluginCall) {
        call.resolve(JSObject().apply { put("version", pluginVersion) })
    }

    @PluginMethod
    fun getAlarms(call: PluginCall) {
        val alarms = AlarmScheduler.listAlarms(context)
        val arr = org.json.JSONArray()
        for (info in alarms) {
            val obj = JSObject().apply {
                put("id", info.id)
                put("hour", info.hour)
                put("minute", info.minute)
                put("label", info.label ?: JSObject.NULL)
                put("enabled", true)
            }
            arr.put(obj)
        }
        call.resolve(JSObject().apply { put("alarms", arr) })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseIso8601(raw: String): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        }
        // API 24/25 fallback — try several common ISO 8601 variants.
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (pattern in patterns) {
            try {
                val fmt = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                // 'Z'-literal patterns treat Z as literal text, so force UTC for those
                if (pattern.endsWith("'Z'")) fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return fmt.parse(raw)?.time ?: continue
            } catch (_: Exception) {
                continue
            }
        }
        throw IllegalArgumentException("Unparseable date: $raw")
    }

    private fun nextTriggerAt(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000L) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private companion object {
        const val TAG = "CapgoAlarmPlugin"
    }
}
