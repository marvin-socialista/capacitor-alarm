import { WebPlugin } from '@capacitor/core';
export class CapgoAlarmWeb extends WebPlugin {
    async createAlarm(_options) {
        return { success: false, message: 'Native alarm not supported on web' };
    }
    async cancelAlarm(_options) {
        return { success: true, message: 'Native alarm not supported on web' };
    }
    async cancelAllAlarms() {
        return { success: true, message: 'Native alarm not supported on web' };
    }
    async openAlarms() {
        return { success: false, message: 'Native alarm UI not available on web' };
    }
    async getOSInfo() {
        return {
            platform: 'web',
            version: navigator.userAgent,
            supportsNativeAlarms: false,
            supportsScheduledNotifications: false,
        };
    }
    async requestPermissions(_options) {
        return { granted: true };
    }
    async checkPermissions() {
        return {
            granted: false,
            message: 'Native alarm permissions are not available on web',
        };
    }
    async getPluginVersion() {
        return { version: 'web' };
    }
    async getAlarms() {
        return { alarms: [] };
    }
}
//# sourceMappingURL=web.js.map