import { WebPlugin } from '@capacitor/core';
import type { CapgoAlarmPlugin, NativeAlarmCreateOptions, NativeActionResult, OSInfo, PermissionResult, AlarmInfo } from './definitions';
export declare class CapgoAlarmWeb extends WebPlugin implements CapgoAlarmPlugin {
    createAlarm(_options: NativeAlarmCreateOptions): Promise<NativeActionResult>;
    cancelAlarm(_options: {
        id: string;
    }): Promise<NativeActionResult>;
    cancelAllAlarms(): Promise<NativeActionResult>;
    openAlarms(): Promise<NativeActionResult>;
    getOSInfo(): Promise<OSInfo>;
    requestPermissions(_options?: {
        exactAlarm?: boolean;
    }): Promise<PermissionResult>;
    checkPermissions(): Promise<PermissionResult>;
    getPluginVersion(): Promise<{
        version: string;
    }>;
    getAlarms(): Promise<{
        alarms: AlarmInfo[];
    }>;
}
