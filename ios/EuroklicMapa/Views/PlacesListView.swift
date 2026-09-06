import CoreLocation
import SwiftUI

/// Seznam tab — merged, distance-sorted (alphabetical without a location fix).
struct PlacesListView: View {
    @EnvironmentObject private var places: PlacesStore
    @EnvironmentObject private var location: LocationService

    @State private var category: PlacesStore.Category = .all

    var body: some View {
        NavigationStack {
            List {
                if let error = places.lastError {
                    Section {
                        Text(error)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                Section {
                    ForEach(places.flattened(category: category, userLocation: location.lastLocation)) { place in
                        NavigationLink(value: place) {
                            PlaceRow(place: place, userLocation: location.lastLocation)
                        }
                    }
                } header: {
                    HStack {
                        Text(placesHeader)
                        Spacer()
                        Picker("Filtr", selection: $category) {
                            ForEach(PlacesStore.Category.allCases) { c in
                                Text(c.label).tag(c)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                } footer: {
                    if let synced = places.lastSync {
                        Text("Aktualizováno \(synced.formatted(date: .abbreviated, time: .shortened))")
                    }
                }
            }
            .navigationTitle("Seznam")
            .navigationDestination(for: PlaceItem.self) { place in
                DetailView(place: place)
            }
            .task {
                if places.wc.isEmpty {
                    location.requestAndRefresh()
                    await places.refreshAll(near: location.nearParameter)
                }
            }
            .refreshable {
                await places.refreshAll(near: location.nearParameter)
            }
        }
    }

    private var placesHeader: String {
        let count = places.flattened(category: category, userLocation: location.lastLocation).count
        return placesCount(count) + (location.lastLocation != nil ? " v okolí" : "")
    }
}

struct PlaceRow: View {
    let place: PlaceItem
    let userLocation: CLLocation?

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(place.isPickup ? Theme.pickup : (place.isOfficialSource ? Theme.official : Theme.community))
                .frame(width: 12, height: 12)
                .opacity(place.status == .reported ? 0.45 : 1)

            VStack(alignment: .leading, spacing: 3) {
                Text(place.name).font(.body.weight(.medium))
                HStack(spacing: 6) {
                    Text(place.sourceLabel)
                    if let badge = place.badgeLabel {
                        Text("·")
                        Text(badge)
                            .foregroundStyle(place.status == .reported ? Theme.accent : Theme.success)
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Spacer()

            if let userLocation {
                let d = userLocation.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
                Text(formatDistance(d))
                    .font(.callout.bold())
                    .foregroundStyle(Theme.brand)
            }
        }
        // 44pt minimum touch targets, WCAG.
        .padding(.vertical, 4)
    }
}
