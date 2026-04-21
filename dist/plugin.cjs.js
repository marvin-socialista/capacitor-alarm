'use strict';

var core = require('@capacitor/core');

var _a;
const CapgoAlarm = core.registerPlugin('CapgoAlarm', {
    web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.CapgoAlarmWeb()),
});
const missingCheckPermissionsResult = {
    granted: false,
    message: 'CapgoAlarm.checkPermissions is not implemented on this platform or native plugin version. Update the native plugin to use this feature.',
};
const nativeCheckPermissions = (_a = CapgoAlarm.checkPermissions) === null || _a === void 0 ? void 0 : _a.bind(CapgoAlarm);
CapgoAlarm.checkPermissions = (async () => {
    if (!nativeCheckPermissions) {
        return missingCheckPermissionsResult;
    }
    try {
        return await nativeCheckPermissions();
    }
    catch (error) {
        if (isUnimplementedError(error)) {
            return missingCheckPermissionsResult;
        }
        throw error;
    }
});
function isUnimplementedError(error) {
    if (!error || typeof error !== 'object') {
        return false;
    }
    const code = error.code;
    if (code === 'UNIMPLEMENTED') {
        return true;
    }
    const message = error.message;
    return typeof message === 'string' && message.toLowerCase().includes('not implemented');
}

class CapgoAlarmWeb extends core.WebPlugin {
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

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    CapgoAlarmWeb: CapgoAlarmWeb
});

exports.CapgoAlarm = CapgoAlarm;
//# sourceMappingURL=plugin.cjs.js.map
