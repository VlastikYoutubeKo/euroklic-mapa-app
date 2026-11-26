import SwiftUI

/// Záložka Oblíbené: uložené zastávky (lokálně v zařízení, nejvýš 50).
struct ODFavoritesTab: View {
    @Environment(\.odColors) private var c
    @EnvironmentObject private var favorites: ODFavoritesStore
    @State private var sheetStop: ODStop?

    let onShowStopOnMap: (ODStop) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if favorites.stops.isEmpty {
                    ScrollView {
                        ODEmptyState(
                            symbol: "bookmark",
                            title: L("Zatím žádné oblíbené zastávky"),
                            message: L("Otevřete zastávku a klepněte na záložku. Najdete ji pak tady a odjezdy budou na jedno klepnutí.")
                        )
                        .padding(.top, 40)
                    }
                } else {
                    List {
                        ForEach(favorites.stops) { stop in
                            row(stop)
                        }
                        .onDelete { favorites.remove(atOffsets: $0) }
                        .onMove { favorites.move(from: $0, to: $1) }

                        Text("Uloženo \(favorites.stops.count) z \(ODFavoritesStore.maxCount) zastávek. Oblíbené zůstávají jen v tomto zařízení.")
                            .font(.footnote)
                            .foregroundStyle(c.muted)
                            .listRowBackground(Color.clear)
                    }
                    .listStyle(.plain)
                }
            }
            .scrollContentBackground(.hidden)
            .background(c.bg)
            .navigationTitle("Oblíbené")
            .toolbar {
                if !favorites.stops.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) {
                        EditButton()
                    }
                }
            }
        }
        .sheet(item: $sheetStop) { stop in
            ODStopSheet(stop: stop, onShowOnMap: { s in
                sheetStop = nil
                onShowStopOnMap(s)
            })
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    private func row(_ stop: ODStop) -> some View {
        Button {
            sheetStop = stop
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "bookmark.fill")
                    .font(.title3)
                    .foregroundStyle(c.mark)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(stop.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(c.text)
                        .multilineTextAlignment(.leading)
                    Text(ODProvider.label(stop.provider))
                        .font(.footnote)
                        .foregroundStyle(c.muted)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(c.muted)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .listRowBackground(c.surface)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Oblíbená zastávka \(stop.name)")
        .accessibilityHint("Zobrazí odjezdy")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: "Odebrat z oblíbených") {
            favorites.remove(stop)
        }
    }
}
