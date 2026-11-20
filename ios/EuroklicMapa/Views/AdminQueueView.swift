import SwiftUI
import UIKit

/// Admin moderační fronta (jen `me.is_admin`): nová místa, návrhy fotek, návrhy komentářů
/// a nahlášené problémy. Fronty sdílí web i Discord — 404 = už vyřízeno, řádek zmizí.
struct AdminQueueView: View {
    @EnvironmentObject private var places: PlacesStore

    @State private var queue: AdminQueue?
    @State private var loadError: String?
    @State private var loading = false
    @State private var acting: String?
    @State private var toast: String?

    var body: some View {
        content
            .navigationTitle("Ke schválení")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                        .disabled(loading)
                        .accessibilityLabel("Načíst znovu")
                }
            }
            .safeAreaInset(edge: .bottom) {
                if let toast {
                    Text(toast)
                        .font(.footnote)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(.regularMaterial))
                        .shadow(radius: 4, y: 1)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                        .accessibilityAddTraits(.updatesFrequently)
                }
            }
            .task(id: toast) {
                guard toast != nil else { return }
                try? await Task.sleep(for: .seconds(4.5))
                toast = nil
            }
            .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if let queue {
            if queue.isEmpty {
                ContentUnavailableView("Nic ke schválení", systemImage: "checkmark.circle",
                                       description: Text("Všechny fronty jsou vyřízené."))
            } else {
                list(queue)
            }
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).multilineTextAlignment(.center)
                Button("Zkusit znovu") { Task { await load() } }
                    .buttonStyle(.bordered)
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ProgressView("Načítám…").frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    // MARK: - Seznamy

    private func list(_ queue: AdminQueue) -> some View {
        let headers = queue.showsSectionHeaders
        return List {
            if !queue.places.isEmpty {
                Section {
                    ForEach(queue.places) { place in placeRow(place) }
                } header: { if headers { Text("Nová místa") } }
            }
            if !queue.photos.isEmpty {
                Section {
                    ForEach(queue.photos) { photo in photoRow(photo) }
                } header: { if headers { Text("Návrhy fotek") } }
            }
            if !queue.comments.isEmpty {
                Section {
                    ForEach(queue.comments) { comment in commentRow(comment) }
                } header: { if headers { Text("Návrhy komentářů") } }
            }
            if !queue.reports.isEmpty {
                Section {
                    ForEach(queue.reports) { report in reportRow(report) }
                } header: { if headers { Text("Nahlášené problémy") } }
            }
        }
        .refreshable { await load() }
    }

    private func placeRow(_ place: AdminPlace) -> some View {
        let key = "place-\(place.id)"
        return VStack(alignment: .leading, spacing: 8) {
            Text(place.name ?? L("Bez názvu")).font(.headline)
            if let description = nonBlank(place.description) {
                Text(description).font(.callout).foregroundStyle(.secondary)
            }
            if let lat = place.lat, let lon = place.lon {
                Text(String(format: "%.5f, %.5f", lat, lon)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
            meta(author: place.author_name, date: place.created_at)
            if let raw = nonBlank(place.photo_url), let url = URL(string: raw) { thumbnail(url) }
            actionButtons(
                key: key,
                approveTitle: L("Schválit"), rejectTitle: L("Zamítnout"),
                approve: { review(key, "approve", "id", place.id, L("Schváleno.")) { queue?.places.removeAll { $0.id == place.id } } },
                reject: { review(key, "reject", "id", place.id, L("Zamítnuto.")) { queue?.places.removeAll { $0.id == place.id } } },
            )
        }
        .padding(.vertical, 4)
    }

    private func photoRow(_ photo: PhotoSuggestion) -> some View {
        let key = "photo-\(photo.id)"
        return VStack(alignment: .leading, spacing: 8) {
            Text(photo.location_name ?? L("Místo #\(photo.location_id ?? 0)")).font(.headline)
            meta(author: photo.author_name, date: nil)
            if let raw = nonBlank(photo.photo_url), let url = URL(string: raw) { thumbnail(url) }
            actionButtons(
                key: key,
                approveTitle: L("Schválit"), rejectTitle: L("Zamítnout"),
                approve: { review(key, "approve_photo", "photo_id", photo.id, L("Fotka schválena.")) { queue?.photos.removeAll { $0.id == photo.id } } },
                reject: { review(key, "reject_photo", "photo_id", photo.id, L("Fotka zamítnuta.")) { queue?.photos.removeAll { $0.id == photo.id } } },
            )
        }
        .padding(.vertical, 4)
    }

    private func commentRow(_ comment: CommentSuggestion) -> some View {
        let key = "comment-\(comment.id)"
        return VStack(alignment: .leading, spacing: 8) {
            Text(comment.location_name ?? L("Místo #\(comment.location_id ?? 0)")).font(.headline)
            Text(comment.text ?? "").font(.callout)
            meta(author: comment.displayAuthor, date: nil)
            actionButtons(
                key: key,
                approveTitle: L("Schválit"), rejectTitle: L("Zamítnout"),
                approve: { review(key, "approve_comment", "comment_id", comment.id, L("Komentář schválen.")) { queue?.comments.removeAll { $0.id == comment.id } } },
                reject: { review(key, "reject_comment", "comment_id", comment.id, L("Komentář zamítnut.")) { queue?.comments.removeAll { $0.id == comment.id } } },
            )
        }
        .padding(.vertical, 4)
    }

    private func reportRow(_ report: LocationReport) -> some View {
        let key = "report-\(report.id)"
        return VStack(alignment: .leading, spacing: 8) {
            Text(report.location_name ?? L("Místo #\(report.location_id)")).font(.headline)
            Text(ReportReason.label(forWire: report.reason ?? "other"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.warningText)
            if let note = nonBlank(report.note) { Text(note).font(.callout) }
            meta(author: report.author_name, date: report.created_at)
            if let item = places.wc.first(where: { $0.id == report.location_id }) {
                NavigationLink {
                    DetailView(place: item)
                } label: {
                    Label("Otevřít místo", systemImage: "arrow.up.right.square")
                        .frame(minHeight: 44, alignment: .leading)
                }
            }
            actionButtons(
                key: key,
                approveTitle: L("Vyřešeno"), rejectTitle: L("Zamítnout"),
                approve: { review(key, "resolve_report", "report_id", report.id, L("Nahlášení označeno jako vyřešené.")) { queue?.reports.removeAll { $0.id == report.id } } },
                reject: { review(key, "dismiss_report", "report_id", report.id, L("Nahlášení zamítnuto.")) { queue?.reports.removeAll { $0.id == report.id } } },
            )
        }
        .padding(.vertical, 4)
    }

    // MARK: - Pomocné prvky

    private func thumbnail(_ url: URL) -> some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image): image.resizable().scaledToFill()
            case .failure: Color(.secondarySystemBackground).overlay(Image(systemName: "photo").foregroundStyle(.secondary))
            default: Color(.secondarySystemBackground).overlay(ProgressView())
            }
        }
        .frame(height: 160)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityLabel("Náhled fotky")
    }

    @ViewBuilder
    private func meta(author: String?, date: String?) -> some View {
        let parts = [nonBlank(author), nonBlank(date).map { String($0.prefix(10)) }].compactMap { $0 }
        if !parts.isEmpty {
            Text(parts.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
        }
    }

    private func actionButtons(
        key: String,
        approveTitle: String,
        rejectTitle: String,
        approve: @escaping () -> Void,
        reject: @escaping () -> Void,
    ) -> some View {
        HStack(spacing: 12) {
            Button(action: reject) {
                Text(rejectTitle).frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            .tint(.red)

            Button(action: approve) {
                Group {
                    if acting == key {
                        ProgressView()
                    } else {
                        Text(approveTitle)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            .tint(Theme.brand)
        }
        .buttonBorderShape(.capsule)
        .disabled(acting != nil)
    }

    private func nonBlank(_ s: String?) -> String? {
        guard let s, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return s
    }

    // MARK: - Akce

    private func load() async {
        guard !loading else { return }
        loading = true
        defer { loading = false }
        switch await EuroklicAPI().adminList() {
        case .loaded(let loaded):
            queue = loaded
            loadError = nil
        case .failed(let message):
            // Při obnově zachovej starý seznam a chybu ukaž; při prvním načtení celou obrazovku.
            if queue == nil { loadError = message } else { announce(message) }
        }
    }

    private func review(
        _ key: String,
        _ action: String,
        _ idField: String,
        _ id: Int,
        _ successMessage: String,
        remove: @escaping () -> Void,
    ) {
        guard acting == nil else { return }
        acting = key
        Task {
            let result = await EuroklicAPI().adminReview(action: action, idField: idField, id: id, successMessage: successMessage)
            acting = nil
            switch result {
            case .ok(let message), .gone(let message):
                remove()
                announce(message)
            case .failed(let message):
                announce(message)
            }
        }
    }

    private func announce(_ message: String) {
        toast = message
        UIAccessibility.post(notification: .announcement, argument: message)
    }
}
