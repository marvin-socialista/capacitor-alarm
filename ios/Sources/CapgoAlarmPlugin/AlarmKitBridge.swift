import Foundation
#if canImport(UIKit)
import UIKit
#endif

#if canImport(AlarmKit)
import AlarmKit
#if canImport(AppIntents)
import AppIntents
#endif
#if canImport(ActivityKit)
import ActivityKit
#endif
#if canImport(SwiftUI)
import SwiftUI
#endif

@available(iOS 26.0, *)
private struct AlarmBridgeMetadata: AlarmMetadata {
    let label: String
}
#endif

public class AlarmKitBridge {
    /// Optional consumer-supplied factory for the AlarmKit alert's secondary
    /// button intent. Called once per `createAlarm` with the alarm UUID;
    /// must return any LiveActivityIntent (typically one configured with
    /// `openAppWhenRun = true` plus app-specific routing logic).
    ///
    /// Register from your AppDelegate at boot so the plugin can build the
    /// AlarmConfiguration without needing to import the host-app types:
    ///
    ///   AlarmKitBridge.secondaryIntentFactory = { alarmId in
    ///       OpenAppointmentIntent(alarmId: alarmId, itemId: …)
    ///   }
    ///
    /// When unset (default), the plugin falls back to its built-in
    /// `OpenInApp` stub which only foregrounds the app via openAppWhenRun.
    #if canImport(AlarmKit)
    @available(iOS 26.0, *)
    public static var secondaryIntentFactory: ((_ alarmId: UUID) -> any LiveActivityIntent)?
    #endif

    static func isAvailable() -> Bool {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) { return true } else { return false }
        #else
        return false
        #endif
    }

    static func requestAuthorization(completion: @escaping (Bool, String?) -> Void) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            Task {
                do {
                    let alarmManager = AlarmManager.shared
                    let state = try await alarmManager.requestAuthorization()
                    completion(state == .authorized, nil)
                } catch {
                    completion(false, "Authorization error: \(error.localizedDescription)")
                }
            }
            return
        }
        #endif
        completion(false, "AlarmKit not available on this device/SDK")
    }

    static func currentAuthorizationStatus(completion: @escaping (Bool, String?) -> Void) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            let status = AlarmManager.shared.authorizationState
            completion(status == .authorized, String(describing: status))
            return
        }
        #endif
        completion(false, "AlarmKit not available on this device/SDK")
    }

    /// Create an alarm. `explicitId` may be passed to use a stable UUID
    /// (so the caller can cancel it later). If nil, a new UUID is generated.
    /// `explicitDate` takes precedence over hour/minute when provided.
    /// `sound` is the filename stem (without extension) of a `.caf` bundled
    /// in the app's main bundle under Sounds/. Falls back to the system
    /// default alarm sound if the file isn't found.
    ///
    /// The alert presentation includes a secondary "Open" button wired to
    /// the OpenInApp LiveActivityIntent — tapping it brings the host app
    /// to the foreground.
    static func createAlarm(
        id explicitId: String?,
        date explicitDate: Date?,
        hour: Int?,
        minute: Int?,
        label: String?,
        sound: String?,
        completion: @escaping (Bool, String?, String?) -> Void
    ) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            let triggerDate: Date
            if let explicitDate = explicitDate {
                triggerDate = explicitDate
            } else if let hour = hour, let minute = minute,
                      (0..<24).contains(hour), (0..<60).contains(minute),
                      let computed = nextTriggerDate(hour: hour, minute: minute) {
                triggerDate = computed
            } else {
                completion(false, "Either `date` or valid `hour`+`minute` must be provided.", nil)
                return
            }

            let alarmId: UUID
            if let explicitId = explicitId, let parsed = UUID(uuidString: explicitId) {
                alarmId = parsed
            } else if explicitId != nil {
                completion(false, "Provided alarm id is not a valid UUID.", nil)
                return
            } else {
                alarmId = UUID()
            }

            Task {
                do {
                    let displayLabel = sanitizedLabel(label)
                    let configuration = try alarmConfiguration(
                        triggerDate: triggerDate,
                        label: displayLabel,
                        soundName: sound,
                        alarmId: alarmId
                    )

                    _ = try await AlarmManager.shared.schedule(id: alarmId, configuration: configuration)

                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    formatter.timeStyle = .short
                    formatter.locale = Locale.current
                    formatter.timeZone = Calendar.current.timeZone

                    let message = "Alarm scheduled for \(formatter.string(from: triggerDate))."
                    completion(true, message, alarmId.uuidString)
                } catch {
                    completion(false, "Failed to schedule alarm: \(error.localizedDescription)", nil)
                }
            }
            return
        }
        #endif
        completion(false, "AlarmKit not available on this device/SDK", nil)
    }

    static func cancelAlarm(id: String, completion: @escaping (Bool, String?) -> Void) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            guard let uuid = UUID(uuidString: id) else {
                completion(false, "Invalid alarm id: \(id)")
                return
            }
            do {
                try AlarmManager.shared.cancel(id: uuid)
                completion(true, nil)
            } catch {
                completion(false, "Failed to cancel alarm: \(error.localizedDescription)")
            }
            return
        }
        #endif
        completion(false, "AlarmKit not available on this device/SDK")
    }

    static func cancelAllAlarms(completion: @escaping (Bool, String?) -> Void) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            do {
                let alarms = try AlarmManager.shared.alarms
                for alarm in alarms {
                    try AlarmManager.shared.cancel(id: alarm.id)
                }
                completion(true, nil)
            } catch {
                completion(false, "Failed to cancel all alarms: \(error.localizedDescription)")
            }
            return
        }
        #endif
        completion(false, "AlarmKit not available on this device/SDK")
    }

    static func openAlarms(completion: @escaping (Bool, String?) -> Void) {
        #if canImport(UIKit)
        DispatchQueue.main.async {
            let candidates = AlarmKitBridge.clockURLCandidates()
            attemptOpenClock(urls: candidates, index: 0, completion: completion)
        }
        #else
        completion(false, "Clock UI cannot be opened on this platform.")
        #endif
    }

    static func getAlarms(completion: @escaping ([[String: Any]], String?) -> Void) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            Task {
                do {
                    let alarmManager = AlarmManager.shared
                    let alarms = try alarmManager.alarms

                    var alarmsList: [[String: Any]] = []
                    for alarm in alarms {
                        if let alarmDict = convertAlarmToDict(alarm: alarm) {
                            alarmsList.append(alarmDict)
                        }
                    }

                    completion(alarmsList, nil)
                } catch {
                    completion([], "Failed to retrieve alarms: \(error.localizedDescription)")
                }
            }
            return
        }
        #endif
        // Return empty list for consistency with Android/Web when not supported
        completion([], nil)
    }
}

#if canImport(AlarmKit)
@available(iOS 26.0, *)
private extension AlarmKitBridge {
    static func convertAlarmToDict(alarm: Alarm) -> [String: Any]? {
        // Get the trigger date from the schedule
        var triggerDate: Date?
        let schedule = alarm.configuration.schedule

        // Extract date based on schedule type
        if case .fixed(let date) = schedule {
            triggerDate = date
        } else if case .repeating(let dateComponents) = schedule {
            // For repeating alarms, create a date from components
            triggerDate = Calendar.current.date(from: dateComponents)
        }

        guard let date = triggerDate else {
            return nil
        }

        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        guard let hour = components.hour, let minute = components.minute else {
            return nil
        }

        // Try to get label from metadata if available
        var label: String?
        if let metadata = alarm.configuration.attributes.metadata as? AlarmBridgeMetadata {
            label = metadata.label
        }

        return [
            "id": alarm.id.uuidString,
            "hour": hour,
            "minute": minute,
            "label": label ?? NSNull(),
            "enabled": alarm.state == .active
        ]
    }

    static func sanitizedLabel(_ label: String?) -> String {
        let trimmed = label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? "Alarm" : trimmed
    }

    /// Copy a `.caf` shipped inside the Capacitor public/ bundle into the
    /// app's `Library/Sounds/` directory if it isn't there yet. AlarmKit's
    /// `AlertSound.named()` lookup searches the main bundle root and
    /// Library/Sounds; the public/ folder reference puts our assets at
    /// `<bundle>/public/alarms/caf/<name>.caf`, which the bare-name lookup
    /// won't match. Mirroring once per (id × install) gives us a flat
    /// canonical path without any pbxproj surgery.
    ///
    /// Returns true when the file is available in Library/Sounds after the
    /// call. Returns false if the source can't be located (stale id) or
    /// the copy fails (rare — quota, sandbox issue), so the caller can
    /// fall back to the system default.
    static func ensureCustomSoundAvailable(named soundName: String) -> Bool {
        // Reject any path-traversal/edge-case input — AlarmKit names should
        // be plain ascii filenames matching the catalog ids.
        if soundName.contains("/") || soundName.contains("\\") || soundName.contains("..") {
            return false
        }
        let fm = FileManager.default
        guard let libraryDir = fm.urls(for: .libraryDirectory, in: .userDomainMask).first else {
            return false
        }
        let soundsDir = libraryDir.appendingPathComponent("Sounds", isDirectory: true)
        let dest = soundsDir.appendingPathComponent("\(soundName).caf")
        if fm.fileExists(atPath: dest.path) {
            return true
        }
        // Try the public/ folder-reference subdirectory first (production
        // path), then fall back to a bundle-root lookup so that hand-added
        // files still work.
        let candidates: [URL?] = [
            Bundle.main.url(forResource: soundName, withExtension: "caf", subdirectory: "public/alarms/caf"),
            Bundle.main.url(forResource: soundName, withExtension: "caf"),
        ]
        guard let source = candidates.compactMap({ $0 }).first else {
            return false
        }
        do {
            try fm.createDirectory(at: soundsDir, withIntermediateDirectories: true)
            try fm.copyItem(at: source, to: dest)
            return true
        } catch {
            return false
        }
    }

    static func nextTriggerDate(hour: Int, minute: Int) -> Date? {
        let now = Date()
        var components = Calendar.current.dateComponents([.year, .month, .day], from: now)
        components.hour = hour
        components.minute = minute
        components.second = 0

        guard let candidate = Calendar.current.date(from: components) else { return nil }
        if candidate.timeIntervalSince(now) > 1 {
            return candidate
        }
        return Calendar.current.date(byAdding: .day, value: 1, to: candidate)
    }

    static func alarmConfiguration(triggerDate: Date, label: String, soundName: String?, alarmId: UUID) throws -> AlarmManager.AlarmConfiguration<AlarmBridgeMetadata> {
        #if canImport(SwiftUI)
        let stopTitle: LocalizedStringResource = LocalizedStringResource("Stop")
        let stopButton = AlarmButton(text: stopTitle, textColor: Color.white, systemImageName: "stop.fill")

        // Secondary "Open" button — tapping it invokes the consumer-supplied
        // intent (or the plugin's built-in OpenInApp stub if no factory is
        // registered). `secondaryButtonBehavior: .custom` is what makes
        // AlarmKit invoke our intent instead of applying its own
        // snooze/dismiss default.
        let openTitle: LocalizedStringResource = LocalizedStringResource("Open")
        let openButton = AlarmButton(text: openTitle, textColor: Color.white, systemImageName: "arrow.right.circle.fill")

        let titleResource = LocalizedStringResource(String.LocalizationValue(label))
        let alert = AlarmPresentation.Alert(
            title: titleResource,
            stopButton: stopButton,
            secondaryButton: openButton,
            secondaryButtonBehavior: .custom
        )
        let presentation = AlarmPresentation(alert: alert)

        let tintColor = Color.orange
        let metadata = AlarmBridgeMetadata(label: label)

        let attributes = AlarmAttributes<AlarmBridgeMetadata>(presentation: presentation, metadata: metadata, tintColor: tintColor)
        // Resolve the sound. AlarmKit's `.named()` looks up the file in
        // the main bundle OR `Library/Sounds/` of the data container, and
        // *requires the file extension* in the name (".caf"). Capacitor
        // ships our .caf assets inside the bundled `public/` folder
        // reference (so Codemagic auto-includes them without pbxproj
        // surgery); we lazily mirror them to Library/Sounds on first use
        // so AlarmKit can find them by name. Falls back to `.default`
        // whenever the source file is missing — never let an alarm fire
        // silently.
        // AlertSound is declared in ActivityKit as
        // `AlertConfiguration.AlertSound` and re-used by AlarmKit. Without
        // `import ActivityKit` at the top, `AlertConfiguration` doesn't
        // resolve and xcodebuild fails with "cannot find type in scope".
        // Apple's own examples pass the bare stem to `.named()` (no .caf
        // extension); the framework appends the extension internally.
        let alertSound: AlertConfiguration.AlertSound
        if let soundName = soundName,
           !soundName.isEmpty,
           ensureCustomSoundAvailable(named: soundName) {
            alertSound = .named(soundName)
        } else {
            alertSound = .default
        }
        // Pick the secondary-button intent. Consumers can register a factory
        // (typically in AppDelegate) that returns a host-app intent like
        // OpenAppointmentIntent — that intent has access to host-app types
        // the plugin can't import. If unset, fall back to the plugin's own
        // OpenInApp stub which only foregrounds the app.
        let secondaryIntent: any LiveActivityIntent = AlarmKitBridge.secondaryIntentFactory?(alarmId)
            ?? OpenInApp(alarmID: alarmId.uuidString)
        return AlarmManager.AlarmConfiguration<AlarmBridgeMetadata>.alarm(
            schedule: .fixed(triggerDate),
            attributes: attributes,
            secondaryIntent: secondaryIntent,
            sound: alertSound
        )
        #else
        struct MissingSwiftUI: Error {}
        throw MissingSwiftUI()
        #endif
    }

}
#endif

#if canImport(UIKit)
private extension AlarmKitBridge {
    static func clockURLCandidates() -> [URL] {
        ["clock-alarm://", "clock://", "clock-app://"].compactMap { URL(string: $0) }
    }

    static func attemptOpenClock(urls: [URL], index: Int, completion: @escaping (Bool, String?) -> Void) {
        guard index < urls.count else {
            completion(false, "Clock app not reachable on this device.")
            return
        }

        UIApplication.shared.open(urls[index], options: [:]) { success in
            if success {
                completion(true, nil)
            } else {
                attemptOpenClock(urls: urls, index: index + 1, completion: completion)
            }
        }
    }
}
#endif
