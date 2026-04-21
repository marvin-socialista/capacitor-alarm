/**
 * Options for creating a native OS alarm via the platform clock app.
 *
 * @since 1.0.0
 */
export interface NativeAlarmCreateOptions {
    /**
     * Stable unique identifier for this alarm. When provided, the alarm can be
     * cancelled later by calling `cancelAlarm({ id })` with the same value.
     * Must be a UUID string on iOS (AlarmKit requirement). If omitted, the plugin
     * generates one and returns it in the `id` field of the result.
     *
     * @since 8.1.0
     */
    id?: string;
    /**
     * Absolute fire time in ISO 8601 format (e.g. '2026-04-23T07:30:00.000Z').
     * Takes precedence over `hour`/`minute` when provided.
     * Required for alarms that should fire on a specific future date rather than
     * the next occurrence of a time-of-day.
     *
     * @since 8.1.0
     */
    date?: string;
    /** Hour of day in 24h format (0-23). Used when `date` is not provided. */
    hour?: number;
    /** Minute of hour (0-59). Used when `date` is not provided. */
    minute?: number;
    /** Optional label for the alarm */
    label?: string;
    /** Android only: attempt to skip UI if possible (legacy AlarmClock intent path, deprecated) */
    skipUi?: boolean;
    /** Android only: set alarm to vibrate */
    vibrate?: boolean;
}
/**
 * Result of a native action.
 *
 * @since 1.0.0
 */
export interface NativeActionResult {
    /** Whether the action was successful */
    success: boolean;
    /** Optional message with additional information */
    message?: string;
    /**
     * When returned from `createAlarm`, the UUID assigned to the scheduled alarm.
     * Pass this to `cancelAlarm` to cancel the alarm later.
     *
     * @since 8.1.0
     */
    id?: string;
}
/**
 * Information about a scheduled alarm.
 *
 * @since 1.1.0
 */
export interface AlarmInfo {
    /** Unique identifier for the alarm */
    id: string;
    /** Hour of day in 24h format (0-23) */
    hour: number;
    /** Minute of hour (0-59) */
    minute: number;
    /** Optional label for the alarm */
    label?: string;
    /** Whether the alarm is enabled */
    enabled?: boolean;
}
/**
 * Returned info about current OS and capabilities.
 *
 * @since 1.0.0
 */
export interface OSInfo {
    /** Platform identifier: 'ios' | 'android' | 'web' */
    platform: string;
    /** OS version string */
    version: string;
    /** Whether the platform exposes a native alarm app integration */
    supportsNativeAlarms: boolean;
    /** Whether scheduling local notifications is supported */
    supportsScheduledNotifications: boolean;
    /** Android only: whether exact alarms are allowed */
    canScheduleExactAlarms?: boolean;
}
/**
 * Result of a permissions request.
 *
 * @since 1.0.0
 */
export interface PermissionResult {
    /** Overall grant for requested scope */
    granted: boolean;
    /** Optional details by permission key */
    details?: Record<string, boolean>;
    /** Optional human readable diagnostic */
    message?: string;
}
/**
 * Capacitor Alarm Plugin interface for managing native OS alarms.
 *
 * @since 1.0.0
 */
export interface CapgoAlarmPlugin {
    /**
     * Create a native OS alarm.
     * On Android the alarm is scheduled via `AlarmManager.setExactAndAllowWhileIdle`
     * and owned by this app (cancellable, survives reboot via a boot receiver).
     * On iOS 26+ the alarm is scheduled via AlarmKit.
     *
     * @param options - Options for creating the alarm
     * @returns Promise that resolves with `{ success, id? }`. `id` is the UUID
     *   that can be passed to `cancelAlarm`.
     * @since 1.0.0
     */
    createAlarm(options: NativeAlarmCreateOptions): Promise<NativeActionResult>;
    /**
     * Cancel a previously scheduled alarm by its id.
     *
     * @param options - `{ id }` of the alarm to cancel
     * @returns `{ success: true }` if the alarm was cancelled (or did not exist);
     *   `{ success: false, message }` on error.
     * @since 8.1.0
     */
    cancelAlarm(options: {
        id: string;
    }): Promise<NativeActionResult>;
    /**
     * Cancel every alarm this app has scheduled.
     *
     * @returns `{ success: true }` when all alarms have been cancelled.
     * @since 8.1.0
     */
    cancelAllAlarms(): Promise<NativeActionResult>;
    /**
     * Open the platform's native alarm list UI, if available.
     *
     * @returns Promise that resolves with the action result
     * @since 1.0.0
     */
    openAlarms(): Promise<NativeActionResult>;
    /**
     * Get information about the OS and capabilities.
     *
     * @since 1.0.0
     */
    getOSInfo(): Promise<OSInfo>;
    /**
     * Request relevant permissions for alarm usage on the platform.
     * iOS: AlarmKit authorization.
     * Android: routes to system settings for exact alarms when `exactAlarm: true`.
     *
     * @since 1.0.0
     */
    requestPermissions(options?: {
        exactAlarm?: boolean;
    }): Promise<PermissionResult>;
    /**
     * Check the current permission state for native alarm access without triggering UI.
     *
     * @since 8.0.4
     */
    checkPermissions(): Promise<PermissionResult>;
    /**
     * Get the native Capacitor plugin version.
     *
     * @since 1.0.0
     */
    getPluginVersion(): Promise<{
        version: string;
    }>;
    /**
     * Get a list of alarms scheduled by this app.
     * iOS 26+: returns alarms from AlarmKit.
     * Android: returns alarms from the local SharedPreferences store maintained
     * by this plugin.
     *
     * @since 1.1.0
     */
    getAlarms(): Promise<{
        alarms: AlarmInfo[];
        message?: string;
    }>;
}
