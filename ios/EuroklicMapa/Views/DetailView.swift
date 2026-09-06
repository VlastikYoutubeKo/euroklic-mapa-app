import MapKit
import SwiftUI

/// Place detail — same sections/order as Android `DetailScreen.WcBody`.
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

    init(place: PlaceItem) {
        self.place = place
        _displayLikes = State(initialValue: place.likes)
        _displayDislikes = State(initialValue: place.dislikes)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                if place.access == "eurokey" { KeyRequiredCard() }
                wheelchairCard
                if let userLocation = location.lastLocation {
                    distanceCard(userLocation)
                }
                MiniMap(lat: place.latitude, lon: place.longitude)
                    .frame(height: 140)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                if let openingHours = nonBlank(place.openingHours) {
                    Section2(label: "Otevírací doba", value: openingHours)
                }
                if let description = nonBlank(place.description) {
                    Section2(label: "Popis", value: prettifyDescription(description))
                }
                if let note = nonBlank(place.note) {
                    Section2(label: "Poznámka", value: note)
                }
                if let accessibilityNote = nonBlank(place.accessibilityNote) {
                    Section2(label: "Přístupnost stanice", value: accessibilityNote)
                }
                if let floorPlan = nonBlank(place.floorPlanUrl) {
                    Link("Plán stanice (ČD)", destination: URL(string: floorPlan)!)
                        .font(.callout.weight(.semibold))
                }
                if !place.isPickup, let source = nonBlank(place.source) {
                    Section2(label: "Původní zdroj", value: originalSourceLabel(source))
                }
                if let lastVerified = nonBlank(place.lastVerified) {
                    Section2(label: "Naposledy ověřeno", value: String(lastVerified.prefix(10)))
                }

                if !place.isPickup {
                    AddPhotoHint()
                    VoteRow(
                        likes: displayLikes,
                        dislikes: displayDislikes,
                        myVote: myVote,
                        voting: voting,
                        message: voteMessage,
                        onVote: vote,
                    )
                    commentsSection
                }
            }
            .padding(16)
        }
        .navigationTitle(place.name)
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
        .task(id: voteMessage) {
            if voteMessage != nil {
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                voteMessage = nil
            }
        }
    }

    // MARK: - Pieces

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(place.sourceLabel)
                    .font(.caption)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(Capsule().fill(.quaternary.opacity(0.6)))
                if let badge = currentBadge {
                    Text(badge)
                        .font(.caption)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Capsule().fill(currentStatus == .reported ? Theme.accent.opacity(0.15) : Theme.success.opacity(0.15)))
                        .foregroundStyle(currentStatus == .reported ? Theme.accent : Theme.success)
                }
                if let country = place.countryLabel, country.hasPrefix("Zahraničí") {
                    Text(country)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            if let phone = place.phonePrimary, let url = URL(string: "tel:\(phone.replacingOccurrences(of: " ", with: ""))") {
                Link(destination: url) {
                    Label(phone, systemImage: "phone.fill")
                        .font(.callout)
                }
            }
        }
    }

    private var currentStatus: PlaceStatus {
        placeStatus(source: place.source, likes: displayLikes, dislikes: displayDislikes, lastVerified: place.lastVerified)
    }

    private var currentBadge: String? {
        let badge = voteBadge(likes: displayLikes, dislikes: displayDislikes, isCd: place.isOfficialSource)
        return badge == .none ? nil : badge.label(likes: displayLikes, dislikes: displayDislikes)
    }

    @ViewBuilder
    private var wheelchairCard: some View {
        if let w = place.wheelchair {
            let text: String? = switch w {
            case "yes": "Budova bezbariérová: ano"
            case "no": "Budova bezbariérová: ne"
            case "unknown": "Budova bezbariérová: nevíme"
            default: nil
            }
            if let text {
                Section2(label: "Bezbariérovost budovy", value: text)
            }
        }
    }

    private func distanceCard(_ userLocation: CLLocation) -> some View {
        let d = userLocation.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
        return HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(formatDistance(d)).font(.title2.bold()).foregroundStyle(Theme.brand)
            VStack(alignment: .leading) {
                Text(formatWalkingTime(d)).font(.subheadline)
                Text("vzdušnou čarou, přibližně").font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(Theme.brand.opacity(0.08)))
    }

    private var commentsSection: some View {
        Group {
            if let comments, !comments.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Komentáře").font(.headline)
                    ForEach(comments) { comment in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(comment.authorName).font(.subheadline.bold())
                                if let day = comment.dayLabel {
                                    Text(day).font(.caption2).foregroundStyle(.secondary)
                                }
                            }
                            Text(comment.text).font(.callout)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 14).fill(.quaternary.opacity(0.5)))
                    }
                    Text("Komentáře se přidávají na webu (odkaz níže).")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
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

struct Section2: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.caption.bold())
                .foregroundStyle(.secondary)
            Text(value).font(.callout)
        }
    }
}

struct KeyRequiredCard: View {
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "key.fill").foregroundStyle(Theme.brand)
            Text("Místo se otevírá Euroklíčem.")
                .font(.callout.weight(.medium))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(Theme.brand.opacity(0.08)))
    }
}

struct AddPhotoHint: View {
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "camera.fill").foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text("Fotku k místu přidáš na webu").font(.callout.weight(.medium))
                Text("Odkaz na web je dole na této stránce.").font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(.quaternary.opacity(0.5)))
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
        if !MapyTiles.urlTemplate.isEmpty {
            let tiles = MKTileOverlay(urlTemplate: MapyTiles.urlTemplate)
            tiles.canReplaceMapContent = true
            map.addOverlay(tiles)
        }
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
    let message: String?
    let onVote: (Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Funguje?").font(.headline)
            HStack(spacing: 10) {
                Button {
                    onVote(true)
                } label: {
                    Label("\(likes)", systemImage: "hand.thumbsup.fill")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(myVote == true ? Theme.success.opacity(0.2) : .quaternary.opacity(0.5)))
                        .foregroundStyle(myVote == true ? Theme.success : .primary)
                }
                .disabled(voting)
                .accessibilityLabel("Hlasovat: funguje")

                Button {
                    onVote(false)
                } label: {
                    Label("\(dislikes)", systemImage: "hand.thumbsdown.fill")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(myVote == false ? Theme.accent.opacity(0.2) : .quaternary.opacity(0.5)))
                        .foregroundStyle(myVote == false ? Theme.accent : .primary)
                }
                .disabled(voting)
                .accessibilityLabel("Hlasovat: nefunguje")
            }
            if voting { ProgressView() }
            if let message {
                Text(message).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}
