import SwiftUI

/// The app has two modes sharing one install: Euroklíč (accessible toilets) and Odjezdy
/// (public transport, like app.odjezdy.online). The choice is remembered.
enum AppMode: String, CaseIterable, Identifiable {
    case euroklic
    case odjezdy

    static let storageKey = "app_mode"
    var id: String { rawValue }

    var label: String {
        switch self {
        case .euroklic: return "Euroklíč"
        case .odjezdy: return "Odjezdy"
        }
    }
}

/// Row used in both Více screens to switch mode.
struct ModePickerSection: View {
    @AppStorage(AppMode.storageKey) private var mode: AppMode = .euroklic

    var body: some View {
        Section {
            Picker("Režim aplikace", selection: $mode) {
                ForEach(AppMode.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
        } header: {
            Text("Režim aplikace")
        } footer: {
            Text("Euroklíč: toalety s Euroklíčem. Odjezdy: veřejná doprava, odjezdy a vozidla na mapě.")
        }
    }
}
