import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(CapgoAlarmPlugin)
public class CapgoAlarmPlugin: CAPPlugin, CAPBridgedPlugin {
    private let pluginVersion: String = "8.3.1-waif"
    public let identifier = "CapgoAlarmPlugin"
    public let jsName = "CapgoAlarm"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "createAlarm", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancelAlarm", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancelAllAlarms", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openAlarms", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getOSInfo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAlarms", returnType: CAPPluginReturnPromise)
    ]

    private lazy var iso8601Formatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    @objc func createAlarm(_ call: CAPPluginCall) {
        let id = call.getString("id")
        let dateString = call.getString("date")
        let hour = call.getInt("hour")
        let minute = call.getInt("minute")
        let label = call.getString("label")
        let sound = call.getString("sound")

        var parsedDate: Date?
        if let dateString = dateString {
            parsedDate = iso8601Formatter.date(from: dateString)
                ?? ISO8601DateFormatter().date(from: dateString)
            if parsedDate == nil {
                call.reject("Invalid `date`. Expected ISO 8601 string.")
                return
            }
        }

        if parsedDate == nil && (hour == nil || minute == nil) {
            call.reject("Either `date` or both `hour` and `minute` are required")
            return
        }

        AlarmKitBridge.createAlarm(
            id: id,
            date: parsedDate,
            hour: hour,
            minute: minute,
            label: label,
            sound: sound
        ) { success, message, assignedId in
            var result: [String: Any] = ["success": success]
            if let message = message { result["message"] = message }
            if let assignedId = assignedId { result["id"] = assignedId }
            call.resolve(result)
        }
    }

    @objc func cancelAlarm(_ call: CAPPluginCall) {
        guard let id = call.getString("id") else {
            call.reject("`id` is required")
            return
        }
        AlarmKitBridge.cancelAlarm(id: id) { success, message in
            var result: [String: Any] = ["success": success]
            if let message = message { result["message"] = message }
            call.resolve(result)
        }
    }

    @objc func cancelAllAlarms(_ call: CAPPluginCall) {
        AlarmKitBridge.cancelAllAlarms { success, message in
            var result: [String: Any] = ["success": success]
            if let message = message { result["message"] = message }
            call.resolve(result)
        }
    }

    @objc func openAlarms(_ call: CAPPluginCall) {
        AlarmKitBridge.openAlarms { success, message in
            var result: [String: Any] = ["success": success]
            if let message = message { result["message"] = message }
            call.resolve(result)
        }
    }

    @objc func getOSInfo(_ call: CAPPluginCall) {
        let version = UIDevice.current.systemVersion
        let supportsNative = AlarmKitBridge.isAvailable()
        call.resolve([
            "platform": "ios",
            "version": version,
            "supportsNativeAlarms": supportsNative,
            "supportsScheduledNotifications": true
        ])
    }

    override public func checkPermissions(_ call: CAPPluginCall) {
        guard AlarmKitBridge.isAvailable() else {
            call.resolve([
                "granted": false,
                "details": ["alarmKit": false],
                "message": "AlarmKit not available on this device/SDK"
            ])
            return
        }

        AlarmKitBridge.currentAuthorizationStatus { granted, statusDescription in
            var result: [String: Any] = [
                "granted": granted,
                "details": ["alarmKit": granted]
            ]
            if !granted, let statusDescription = statusDescription {
                result["message"] = "AlarmKit authorization status: \(statusDescription)"
            }
            call.resolve(result)
        }
    }

    override public func requestPermissions(_ call: CAPPluginCall) {
        if !AlarmKitBridge.isAvailable() {
            call.resolve(["granted": false])
            return
        }
        AlarmKitBridge.requestAuthorization { granted, message in
            var result: [String: Any] = ["granted": granted]
            if let message = message { result["message"] = message }
            call.resolve(result)
        }
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }

    @objc func getAlarms(_ call: CAPPluginCall) {
        AlarmKitBridge.getAlarms { alarms, error in
            if let error = error {
                call.resolve(["alarms": [], "message": error])
            } else {
                call.resolve(["alarms": alarms])
            }
        }
    }
}
