import PhotosUI
import SwiftUI
import UIKit

/// Blok „Přispějte“ pod detailem místa: přidat fotku, přidat komentář, nahlásit problém.
/// Vše vyžaduje přihlášení (nepřihlášenému se nabídne přihlášení) a jde do moderace.
struct ContributeBlock: View {
    @EnvironmentObject private var auth: AuthSession
    let place: PlaceItem
    var onToast: (ToastItem) -> Void = { _ in }

    @State private var showPhotoSource = false
    @State private var showLibrary = false
    @State private var showCamera = false
    @State private var showFiles = false
    @State private var libraryItem: PhotosPickerItem?
    @State private var showComposer = false
    @State private var showReport = false
    @State private var showLoginPrompt = false
    @State private var uploading = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Přispějte").font(.title2.bold())

            VStack(spacing: 0) {
                row(L("Přidat fotku"), systemImage: "camera") { requireLogin { showPhotoSource = true } }
                Divider().padding(.leading, 14)
                row(L("Přidat komentář"), systemImage: "text.bubble") { requireLogin { showComposer = true } }
                Divider().padding(.leading, 14)
                row(L("Nahlásit problém"), systemImage: "exclamationmark.bubble") { requireLogin { showReport = true } }
            }
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(.secondarySystemBackground)))
            .disabled(uploading)

            if uploading {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Odesílám fotku…").font(.footnote).foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .combine)
            }
            Text("Fotky, komentáře i hlášení nejdřív schvaluje moderátor.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .confirmationDialog("Přidat fotku", isPresented: $showPhotoSource, titleVisibility: .visible) {
            if CameraPicker.isAvailable {
                Button("Vyfotit") { showCamera = true }
            }
            Button("Vybrat z fotek") { showLibrary = true }
            Button("Vybrat ze souborů") { showFiles = true }
            Button("Zrušit", role: .cancel) {}
        }
        .photosPicker(isPresented: $showLibrary, selection: $libraryItem, matching: .images)
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.image]) { result in
            guard case .success(let url) = result else { return }
            Task { await sendFile(url) }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker(isPresented: $showCamera) { image in
                Task { await sendCamera(image) }
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $showComposer) {
            CommentComposer(place: place) { show($0) }
        }
        .sheet(isPresented: $showReport) {
            ReportSheet(place: place) { show($0) }
        }
        .alert("Je potřeba přihlášení", isPresented: $showLoginPrompt) {
            Button("Přihlásit se") { auth.login() }
            Button("Zrušit", role: .cancel) {}
        } message: {
            Text("Fotky, komentáře i hlášení posíláme moderátorům s vaším jménem, proto se nejdřív přihlaste.")
        }
        .onChange(of: libraryItem) { item in
            guard let item else { return }
            Task { await sendLibrary(item) }
        }
    }

    private func row(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .foregroundStyle(Theme.tint)
                    .frame(width: 28)
                Text(title).foregroundStyle(.primary)
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func requireLogin(_ action: () -> Void) {
        if auth.isLoggedIn { action() } else { showLoginPrompt = true }
    }

    private func show(_ text: String, ok: Bool = true) {
        onToast(ToastItem(text: text, kind: ok ? .success : .failure))
    }

    // MARK: - Fotka

    private func sendLibrary(_ item: PhotosPickerItem) async {
        defer { libraryItem = nil }
        guard let data = try? await item.loadTransferable(type: Data.self) else {
            show(L("Fotku se nepodařilo načíst."), ok: false)
            return
        }
        let jpeg = await Task.detached(priority: .userInitiated) {
            ImageUtils.downscaleToJPEG(data: data)
        }.value
        await upload(jpeg)
    }

    /// Photo picked in the Files app (security-scoped URL).
    private func sendFile(_ url: URL) async {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            show(L("Fotku se nepodařilo načíst."), ok: false)
            return
        }
        let jpeg = await Task.detached(priority: .userInitiated) {
            ImageUtils.downscaleToJPEG(data: data)
        }.value
        await upload(jpeg)
    }

    private func sendCamera(_ image: UIImage) async {
        let jpeg = await Task.detached(priority: .userInitiated) {
            ImageUtils.downscaleToJPEG(image: image)
        }.value
        await upload(jpeg)
    }

    private func upload(_ jpeg: Data?) async {
        guard let jpeg else {
            show(L("Fotku se nepodařilo zpracovat."), ok: false)
            return
        }
        uploading = true
        let result = await EuroklicAPI().addPhoto(placeId: place.id, jpeg: jpeg)
        uploading = false
        show(result.message, ok: result.isOk)
    }
}

// MARK: - Komentář

struct CommentComposer: View {
    @Environment(\.dismiss) private var dismiss
    let place: PlaceItem
    let onResult: (String) -> Void

    @State private var text = ""
    @State private var sending = false
    @State private var error: String?

    private static let minLength = 3
    private static let maxLength = 2000

    private var trimmed: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Server povoluje nejvýš jeden odkaz.
    private var linkCount: Int {
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else { return 0 }
        return detector.numberOfMatches(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed))
    }

    private var validationHint: String? {
        if trimmed.count > Self.maxLength { return L("Komentář je příliš dlouhý (max \(Self.maxLength) znaků).") }
        if linkCount > 1 { return L("Komentář může obsahovat nejvýš jeden odkaz.") }
        return nil
    }

    private var canSend: Bool {
        !sending && trimmed.count >= Self.minLength && validationHint == nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextEditor(text: $text)
                        .frame(minHeight: 140)
                        .accessibilityLabel("Text komentáře")
                } header: {
                    Text(place.name)
                } footer: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(trimmed.count)/\(Self.maxLength) znaků (min \(Self.minLength)), nejvýš jeden odkaz. Komentář nejdřív schválí moderátor.")
                        if let hint = validationHint {
                            Text(hint).foregroundStyle(.red)
                        }
                    }
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red).font(.callout) }
                }
            }
            .navigationTitle("Nový komentář")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Zrušit") { dismiss() }.disabled(sending)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if sending {
                        ProgressView()
                    } else {
                        Button("Odeslat") { send() }.disabled(!canSend)
                    }
                }
            }
        }
        .interactiveDismissDisabled(sending)
    }

    private func send() {
        guard canSend else { return }
        sending = true
        error = nil
        Task {
            let result = await EuroklicAPI().postComment(locationId: place.id, text: trimmed)
            sending = false
            if result.isOk {
                Analytics.track(.commentAdd)
                onResult(result.message)
                dismiss()
            } else {
                error = result.message
            }
        }
    }
}

// MARK: - Nahlášení problému

struct ReportSheet: View {
    @Environment(\.dismiss) private var dismiss
    let place: PlaceItem
    let onResult: (String) -> Void

    @State private var reason: ReportReason?
    @State private var note = ""
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(ReportReason.allCases) { r in
                        Button {
                            reason = r
                        } label: {
                            HStack {
                                Text(r.label).foregroundStyle(.primary)
                                Spacer()
                                if reason == r {
                                    Image(systemName: "checkmark").foregroundStyle(Theme.tint)
                                }
                            }
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(reason == r ? .isSelected : [])
                    }
                } header: {
                    Text("Co je špatně u „\(place.name)“?")
                        .textCase(nil)
                }

                Section("Poznámka (nepovinné)") {
                    TextField("Upřesnění pro moderátora", text: $note, axis: .vertical)
                        .lineLimit(3...6)
                }

                if let error {
                    Section { Text(error).foregroundStyle(.red).font(.callout) }
                }
            }
            .navigationTitle("Nahlásit problém")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Zrušit") { dismiss() }.disabled(sending)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if sending {
                        ProgressView()
                    } else {
                        Button("Odeslat") { send() }.disabled(reason == nil)
                    }
                }
            }
        }
        .interactiveDismissDisabled(sending)
    }

    private func send() {
        guard let reason, !sending else { return }
        sending = true
        error = nil
        Task {
            let result = await EuroklicAPI().reportProblem(locationId: place.id, reason: reason, note: note)
            sending = false
            if result.isOk {
                Analytics.track(.report)
                onResult(result.message)
                dismiss()
            } else {
                error = result.message
            }
        }
    }
}
