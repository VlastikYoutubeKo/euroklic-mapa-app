import MapKit
import SwiftUI

/// Place detail — native inset-grouped list: header with the one primary action
/// (Navigovat), locator map, nearby stops, info rows, vote, comments, links.
struct DetailView: View {
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var location: LocationService
    @EnvironmentObject private var auth: AuthSession

    let place: PlaceItem

    // Local vote overrides (server counts arrive via the vote response).
    @State private var displayLikes: Int
    @State private var displayDislikes: Int
    @State private var myVote: Bool?
    @State private var voting = false
    @State private var voteMessage: String?

    @State private var comments: [WcComment]?
    @State private var loadingComments = false
    @State private var stops: [NearbyStop] = []

    init(place: PlaceItem) {
        self.place = place
        _displayLikes = State(initialValue: place.likes)
        _displayDislikes = State(initialValue: place.dislikes)
    }

    var body: some View {
        List {
            Section { header }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 4, leading: 4, bottom: 8, trailing: 4))

            Section {
                MiniMap(lat: place.latitude, lon: place.longitude)
                    .frame(height: 150)
                    .listRowInsets(EdgeInsets())
                    .accessibilityHidden(true)
            } footer: {
                if let userLocation = location.lastLocation {
                    Text(distanceText(userLocation))
                }
            }

            if !stops.isEmpty {
                Section {
                    ForEach(stops) { stop in
                        if let url = URL(string: "https://app.odjezdy.online/zastavka/\(stop.id)") {
                            Link(destination: url) {
                                HStack {
                                    Image(systemName: "bus.fill").foregroundStyle(Theme.tint).frame(width: 28)
                                    Text(stop.name).foregroundStyle(.primary)
                                    Spacer(minLength: 8)
                                    Text(formatDistance(Double(stop.distanceM)))
                                        .font(.subheadline.monospacedDigit())
                                        .foregroundStyle(.secondary)
                                    Image(systemName: "chevron.right")
                                        .font(.footnote.weight(.semibold))
                                        .foregroundStyle(.tertiary)
                                }
                            }
                            .accessibilityLabel("\(stop.name), \(stop.distanceM) metrů. Zobrazit odjezdy.")
                        }
                    }
                    if let all = URL(string: String(format: "https://app.odjezdy.online/blizko?lat=%.6f&lng=%.6f", place.latitude, place.longitude)) {
                        Link("Všechny zastávky v okolí", destination: all)
                    }
                } header: {
                    Text("Zastávky a odjezdy")
                }
            }

            if hasInfo {
                Section("Informace") {
                    if place.access == "eurokey" {
                        Label("Otevírá se Euroklíčem", systemImage: "key.fill")
                    }
                    if let text = wheelchairText { InfoRow(label: "Bezbariérovost budovy", value: text) }
                    if let hours = nonBlank(place.openingHours) { InfoRow(label: "Otevírací doba", value: hours) }
                    if let description = nonBlank(place.description) {
                        InfoRow(label: "Popis", value: prettifyDescription(description))
                    }
                    if let note = nonBlank(place.note) { InfoRow(label: "Poznámka", value: note) }
                }
            }

            if let accessibilityNote = nonBlank(place.accessibilityNote) {
                Section {
                    DisclosureGroup("Přístupnost stanice") {
                        Text(accessibilityNote).font(.callout).foregroundStyle(.secondary)
                    }
                }
            }

            if !place.isPickup {
                Section {
                    VoteRow(
                        likes: displayLikes,
                        dislikes: displayDislikes,
                        myVote: myVote,
                        voting: voting,
                        onVote: vote,
                    )
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                } header: {
                    Text("Funguje to?")
                } footer: {
                    if let voteMessage { Text(voteMessage) }
                }
            }

            if let comments, !comments.isEmpty {
                Section("Komentáře") {
                    ForEach(comments) { comment in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(alignment: .firstTextBaseline) {
                                Text(comment.authorName).font(.subheadline.weight(.semibold))
                                Spacer()
                                if let day = comment.dayLabel {
                                    Text(day).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Text(comment.text).font(.callout)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }

            Section {
                if let floorPlan = nonBlank(place.floorPlanUrl), let url = URL(string: floorPlan) {
                    LinkRow(title: "Plán stanice (ČD)", systemImage: "map", url: url)
                }
                if let url = webURL {
                    LinkRow(title: "Otevřít na webu", systemImage: "safari", url: url)
                }
                if !place.isPickup, let source = nonBlank(place.source) {
                    InfoRow(label: "Původní zdroj", value: originalSourceLabel(source))
                }
                if let lastVerified = nonBlank(place.lastVerified) {
                    InfoRow(label: "Naposledy ověřeno", value: String(lastVerified.prefix(10)))
                }
            } footer: {
                if !place.isPickup { Text("Fotku nebo komentář k místu přidáš na webu.") }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Detail")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    favorites.toggle(place)
                } label: {
                    Image(systemName: favorites.isFavorite(place) ? "bookmark.fill" : "bookmark")
                }
                .accessibilityLabel(favorites.isFavorite(place) ? "Odebrat z oblíbených" : "Přidat do oblíbených")
            }
        }
        .task {
            guard !place.isPickup else { return }
            loadingComments = true
            comments = (try? await EuroklicAPI().comments(locationId: place.id))?.comments ?? []
            loadingComments = false
        }
        .task(id: "\(place.latitude),\(place.longitude)") {
            stops = await NearbyStopsService.nearby(lat: place.latitude, lon: place.longitude)
        }
        .task(id: voteMessage) {
            if voteMessage != nil {
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                voteMessage = nil
            }
        }
    }

    // MARK: - Pieces

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(place.name)
                .font(.title2.bold())
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 4) {
                Label(place.sourceLabel, systemImage: place.isOfficialSource ? "checkmark.seal.fill" : "person.2.fill")
                    .foregroundStyle(.secondary)
                if let badge = currentBadge {
                    Text(badge)
                        .foregroundStyle(currentStatus == .reported ? Theme.accent : Theme.success)
                }
                if let country = place.countryLabel, country.hasPrefix("Zahraničí") {
                    Text(country).foregroundStyle(.secondary)
                }
            }
            .font(.subheadline)

            if let phone = place.phonePrimary, let url = URL(string: "tel:\(phone.replacingOccurrences(of: " ", with: ""))") {
                Link(destination: url) { Label(phone, systemImage: "phone.fill") }
                    .font(.subheadline)
            }

            Button(action: openNavigation) {
                Label("Navigovat", systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(Theme.brand)
            .controlSize(.large)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var currentStatus: PlaceStatus {
        placeStatus(source: place.source, likes: displayLikes, dislikes: displayDislikes, lastVerified: place.lastVerified)
    }

    private var currentBadge: String? {
        let badge = voteBadge(likes: displayLikes, dislikes: displayDislikes, isCd: place.isOfficialSource)
        return badge == .none ? nil : badge.label(likes: displayLikes, dislikes: displayDislikes)
    }

    private var wheelchairText: String? {
        switch place.wheelchair {
        case "yes": return "Ano"
        case "no": return "Ne"
        case "unknown": return "Nevíme"
        default: return nil
        }
    }

    private var hasInfo: Bool {
        place.access == "eurokey" || wheelchairText != nil || nonBlank(place.openingHours) != nil
            || nonBlank(place.description) != nil || nonBlank(place.note) != nil
    }

    private var webURL: URL? {
        guard let raw = nonBlank(place.webUrl) else { return nil }
        if raw.hasPrefix("http") { return URL(string: raw) }
        return URL(string: "https://euroklic.odjezdy.online" + (raw.hasPrefix("/") ? "" : "/") + raw)
    }

    private func distanceText(_ userLocation: CLLocation) -> String {
        let d = userLocation.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
        return "\(formatDistance(d)) · \(formatWalkingTime(d)) pěšky, vzdušnou čarou"
    }

    /// Hands off to the system Maps app (walking directions) — the native route.
    private func openNavigation() {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: place.coordinate))
        item.name = place.name
        item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeWalking])
    }

    // MARK: - Vote (CSRF + cookie flow, one 403 retry — mirrors Android)

    private func vote(_ like: Bool) {
        guard !voting, !place.isPickup else { return }
        voting = true
        let previous = myVote
        myVote = like
        Task {
            defer { voting = false }
            do {
                let api = EuroklicAPI()
                var csrf = try await api.csrf().csrf ?? ""
                var response: VoteResponse
                do {
                    response = try await api.vote(id: place.id, type: like ? "like" : "dislike", csrfToken: csrf)
                } catch APIError.http(let code) where code == 403 {
                    // Stale CSRF → refetch once (same contract as Android).
                    csrf = try await api.csrf().csrf ?? ""
                    response = try await api.vote(id: place.id, type: like ? "like" : "dislike", csrfToken: csrf)
                }
                if response.success {
                    if let likes = response.likes { displayLikes = likes }
                    if let dislikes = response.dislikes { displayDislikes = dislikes }
                    voteMessage = like ? "Díky, zaznamenáno jako funguje." : "Díky, zaznamenáno."
                } else if let message = [response.message, response.error].compactMap(\.self).first(where: { !$0.isEmpty }) {
                    // e.g. "Už jste takto hlasovali." — keep the highlight
                    voteMessage = message
                } else {
                    myVote = previous
                    voteMessage = "Hlas se teď nepodařilo odeslat. Zkuste to znovu."
                }
            } catch {
                myVote = previous
                voteMessage = (error as? LocalizedError)?.errorDescription ?? "Hlas se nepodařilo odeslat."
            }
        }
    }

    // MARK: - Helpers

    private func nonBlank(_ s: String?) -> String? {
        guard let s, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return s
    }

    /// OSM imports often dump `[Wheelchair: yes]` into description — make it readable.
    private func prettifyDescription(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let pattern = "^\\[?\\s*wheelchair\\s*[:=]\\s*(\\w+)\\s*]?$"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
              let range = Range(match.range(at: 1), in: trimmed)
        else { return raw }
        switch trimmed[range].lowercased() {
        case "yes": return "Bezbariérový přístup: ano"
        case "limited": return "Bezbariérový přístup: částečně"
        case "no": return "Bezbariérový přístup: ne"
        default: return raw
        }
    }

    private func originalSourceLabel(_ source: String) -> String {
        switch source.lowercased() {
        case "cd": return "ČD (České dráhy)"
        case "osm": return "OpenStreetMap"
        case "mapotic": return "WCkompas (Mapotic)"
        case "user": return "Uživatelé webu"
        default: return source
        }
    }
}

// MARK: - Small shared pieces

/// Label above value — the Contacts / Settings detail-row pattern.
struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.subheadline).foregroundStyle(.secondary)
            Text(value)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

/// Tappable row that leaves the app (browser): SF Symbol, title, trailing arrow.
struct LinkRow: View {
    let title: String
    let systemImage: String
    let url: URL

    var body: some View {
        Link(destination: url) {
            HStack {
                Label(title, systemImage: systemImage)
                Spacer()
                Image(systemName: "arrow.up.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
        }
    }
}

/// Non-interactive locator — eats gestures, shows the dot.
struct MiniMap: UIViewRepresentable {
    let lat: Double
    let lon: Double

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView(frame: .zero)
        map.isScrollEnabled = false
        map.isZoomEnabled = false
        map.isRotateEnabled = false
        map.showsUserLocation = false
        map.delegate = context.coordinator
        map.applyBasemap(MapProvider.stored)
        let pin = MKPointAnnotation()
        pin.coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lon)
        map.addAnnotation(pin)
        map.setRegion(MKCoordinateRegion(center: pin.coordinate, latitudinalMeters: 400, longitudinalMeters: 300), animated: false)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tiles = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tiles)
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}

struct VoteRow: View {
    let likes: Int
    let dislikes: Int
    let myVote: Bool?
    let voting: Bool
    let onVote: (Bool) -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button { onVote(true) } label: {
                Label("\(likes)", systemImage: myVote == true ? "hand.thumbsup.fill" : "hand.thumbsup")
                    .frame(maxWidth: .infinity)
            }
            .tint(myVote == true ? Theme.success : .secondary)
            .accessibilityLabel("Hlasovat: funguje. Zatím \(likes).")

            Button { onVote(false) } label: {
                Label("\(dislikes)", systemImage: myVote == false ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                    .frame(maxWidth: .infinity)
            }
            .tint(myVote == false ? Theme.accent : .secondary)
            .accessibilityLabel("Hlasovat: nefunguje. Zatím \(dislikes).")
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
        .disabled(voting)
    }
}
