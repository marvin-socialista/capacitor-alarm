var _a;
import { registerPlugin } from '@capacitor/core';
const CapgoAlarm = registerPlugin('CapgoAlarm', {
    web: () => import('./web').then((m) => new m.CapgoAlarmWeb()),
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
export * from './definitions';
export { CapgoAlarm };
//# sourceMappingURL=index.js.map