import Foundation
import SwiftUI

/// Odjezdy jedné zastávky; obnovuje se nejvýš jednou za ~20 s, dokud je karta vidět
/// (smyčka běží v `.task`, takže se sama zruší při zavření).
@MainActor
final class ODStopBoardViewModel: ObservableObject {
    @Published private(set) var departures: [ODDeparture] = []
    @Published private(set) var isLoading = true
    @Published private(set) var errorText: String?
    @Published private(set) var lastLoad: Date?
    @Published var systemFilter: String?

    let stop: ODStop
    private let api = ODAPIClient.shared

    nonisolated init(stop: ODStop) {
        self.stop = stop
    }

    func run() async {
        while !Task.isCancelled {
            await load()
            try? await Task.sleep(nanoseconds: 20_000_000_000)
        }
    }

    func load() async {
        do {
            let list = try await api.departures(stopId: stop.id)
            let cutoff = Date().addingTimeInterval(-60)
            departures = list
                .filter { $0.realTime >= cutoff }
                .sorted { $0.realTime < $1.realTime }
            errorText = nil
            lastLoad = Date()
        } catch is CancellationError {
            return
        } catch let error as ODAPIError {
            errorText = error.errorDescription
        } catch {
            errorText = L("Odjezdy se nepodařilo načíst.")
        }
        isLoading = false
    }

    /// Dopravci (systémy) přítomní v odjezdech, pro chips.
    var systems: [String] {
        var seen = Set<String>()
        var out: [String] = []
        for d in departures where seen.insert(d.systemLabel).inserted {
            out.append(d.systemLabel)
        }
        return out.sorted()
    }

    var visibleDepartures: [ODDeparture] {
        guard let f = systemFilter, systems.contains(f) else { return departures }
        return departures.filter { $0.systemLabel == f }
    }
}
