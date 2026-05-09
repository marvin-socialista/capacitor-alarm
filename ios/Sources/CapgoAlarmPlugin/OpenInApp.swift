import Foundation
import AppIntents

/// LiveActivityIntent attached to the alarm alert's secondary button.
/// Tapping "Open" on the alert (or in the Live Activity / Dynamic Island)
/// brings WAIF to the foreground via `openAppWhenRun = true`.
///
/// `alarmID` is carried through so the host app can route to a relevant
/// screen (item detail, morning digest, etc.) — currently surfaced via
/// the standard URL scheme path the consumer wires up in their AppDelegate.
@available(iOS 26.0, *)
public struct OpenInApp: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Open WAIF"
    public static var description = IntentDescription("Opens the WAIF app from an alarm alert.")
    public static var openAppWhenRun: Bool = true

    @Parameter(title: "alarmID")
    public var alarmID: String

    public init() {
        self.alarmID = ""
    }

    public init(alarmID: String) {
        self.alarmID = alarmID
    }

    public func perform() async throws -> some IntentResult {
        // The system foregrounds the app because of openAppWhenRun. The
        // alarmID is preserved on the intent so a future enhancement can
        // route it through to a detail screen via UIApplicationDelegate
        // — for now we just bring the app to the front.
        return .result()
    }
}
