import Foundation
import SwiftUI
import UniformTypeIdentifiers

/// Přenos dat mezi stabilní verzí a betou (jsou to dvě různé aplikace, nesdílí úložiště):
/// jedna exportuje soubor, druhá ho naimportuje. Přihlášení (token) se nepřenáší – v druhé appce se
/// stačí přihlásit znovu.
enum DataTransfer {
    static let didImport = Notification.Name("EKDataTransferDidImport")

    /// Nastavení, která se přenáší (texty z @AppStorage).
    private static let stringKeys = [AppMode.storageKey, ODTheme.paletteKey, MapProvider.storageKey]
    private static let boolKeys = [Analytics.enabledKey]
    private static let votesKey = "place_votes"

    struct Payload: Codable {
        var format = 1
        var appVersion: String
        var exportedAt: Date
        var favorites: [PlaceItem]
        var departureFavorites: [ODStop]
        var settings: [String: String]
        var flags: [String: Bool]
        var votes: [String: Bool]
    }

    struct Summary {
        let favorites: Int
        let departureFavorites: Int
        /// Zastávky, které se nevešly do limitu oblíbených (ODFavoritesStore.maxCount).
        let skippedStops: Int
    }

    enum TransferError: LocalizedError {
        case unreadable, wrongFormat, localDataDamaged, writeFailed, partiallyWritten
        var errorDescription: String? {
            switch self {
            case .unreadable: return L("Soubor se nepodařilo přečíst.")
            case .wrongFormat: return L("Tohle není soubor s daty z Euroklíč Mapy.")
            case .localDataDamaged: return L("Oblíbené v této aplikaci se nepodařilo přečíst, import jsem proto nespustil, aby se nic nepřepsalo.")
            case .writeFailed: return L("Data se nepodařilo uložit. Nic se nezměnilo.")
            case .partiallyWritten: return L("Import se nedokončil: oblíbená místa se doplnila, oblíbené zastávky a nastavení ne. Zkuste import znovu, nic se nezdvojí.")
            }
        }
    }

    // MARK: Export

    static func export() throws -> Data {
        let d = UserDefaults.standard
        var settings: [String: String] = [:]
        for k in stringKeys { if let v = d.string(forKey: k) { settings[k] = v } }
        var flags: [String: Bool] = [:]
        for k in boolKeys where d.object(forKey: k) != nil { flags[k] = d.bool(forKey: k) }
        let payload = Payload(
            appVersion: Changelog.currentVersion,
            exportedAt: Date(),
            favorites: try readList(FavoritesStore.fileURL),
            departureFavorites: try readList(ODFavoritesStore.defaultFileURL),
            settings: settings,
            flags: flags,
            votes: (d.dictionary(forKey: votesKey) as? [String: Bool]) ?? [:]
        )
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        enc.dateEncodingStrategy = .iso8601
        return try enc.encode(payload)
    }

    // MARK: Import

    /// Sloučí data se stávajícími: oblíbené se přidají (nic se nesmaže), nastavení se přepíše.
    @discardableResult
    static func importData(_ data: Data) throws -> Summary {
        let dec = JSONDecoder()
        dec.dateDecodingStrategy = .iso8601
        guard let p = try? dec.decode(Payload.self, from: data) else { throw TransferError.wrongFormat }
        guard p.format == 1 else { throw TransferError.wrongFormat }

        // Nejdřív přečíst a sloučit všechno, teprve potom zapisovat (poškozený místní soubor se nepřepíše).
        var favs: [PlaceItem] = try readList(FavoritesStore.fileURL)
        var added = 0
        for f in p.favorites where !favs.contains(where: { $0.favoriteKey == f.favoriteKey }) {
            favs.append(f)
            added += 1
        }

        var stops: [ODStop] = try readList(ODFavoritesStore.defaultFileURL)
        var addedStops = 0
        var skippedStops = 0
        for s in p.departureFavorites where !stops.contains(where: { $0.id == s.id }) {
            if stops.count < ODFavoritesStore.maxCount {
                stops.append(s)
                addedStops += 1
            } else {
                skippedStops += 1
            }
        }

        let favData: Data, stopData: Data
        do {
            favData = try JSONEncoder().encode(favs)
            stopData = try JSONEncoder().encode(stops)
        } catch {
            throw TransferError.writeFailed
        }
        let oldFavData = try? Data(contentsOf: FavoritesStore.fileURL)
        do {
            try favData.write(to: FavoritesStore.fileURL, options: .atomic)
        } catch {
            throw TransferError.writeFailed
        }
        do {
            try stopData.write(to: ODFavoritesStore.defaultFileURL, options: .atomic)
        } catch {
            // Vrátit první soubor, ať import neproběhne napůl.
            do {
                if let old = oldFavData {
                    try old.write(to: FavoritesStore.fileURL, options: .atomic)
                } else {
                    try FileManager.default.removeItem(at: FavoritesStore.fileURL)
                }
            } catch {
                // Oblíbená místa už jsou doplněná a vrátit to nejde: říct to po pravdě.
                NotificationCenter.default.post(name: didImport, object: nil)
                throw TransferError.partiallyWritten
            }
            throw TransferError.writeFailed
        }

        let d = UserDefaults.standard
        for k in stringKeys { if let v = p.settings[k] { d.set(v, forKey: k) } }
        for k in boolKeys { if let v = p.flags[k] { d.set(v, forKey: k) } }
        var votes = (d.dictionary(forKey: votesKey) as? [String: Bool]) ?? [:]
        votes.merge(p.votes) { mine, _ in mine }
        d.set(votes, forKey: votesKey)

        NotificationCenter.default.post(name: didImport, object: nil)
        return Summary(favorites: added, departureFavorites: addedStops, skippedStops: skippedStops)
    }

    static func importFile(at url: URL) throws -> Summary {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else { throw TransferError.unreadable }
        return try importData(data)
    }

    // MARK: Files

    /// Neexistující soubor = prázdný seznam; existující, ale nečitelný = chyba (nesmí se přepsat).
    private static func readList<T: Decodable>(_ url: URL) throws -> [T] {
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        guard let data = try? Data(contentsOf: url),
              let list = try? JSONDecoder().decode([T].self, from: data) else {
            throw TransferError.localDataDamaged
        }
        return list
    }
}

/// Soubor pro `fileExporter`.
struct DataTransferDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

/// Sekce ve Více: Exportovat / Importovat data.
struct DataTransferSection: View {
    @State private var exportDoc: DataTransferDocument?
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var message: String?

    var body: some View {
        Section {
            Button {
                do {
                    exportDoc = DataTransferDocument(data: try DataTransfer.export())
                    showExporter = true
                } catch {
                    message = (error as? DataTransfer.TransferError)?.errorDescription ?? L("Export se nepodařil.")
                }
            } label: {
                Label("Exportovat data do souboru", systemImage: "square.and.arrow.up")
            }
            Button {
                showImporter = true
            } label: {
                Label("Importovat data ze souboru", systemImage: "square.and.arrow.down")
            }
        } header: {
            Text("Přenos dat")
        } footer: {
            Text("Přenese oblíbená místa, oblíbené zastávky, hlasy a nastavení mezi stabilní verzí a betou. V jedné appce exportujte soubor, v druhé ho importujte. Nic se nesmaže, oblíbené se jen doplní. Přihlášení se nepřenáší.")
        }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDoc,
            contentType: .json,
            defaultFilename: "Euroklic-Mapa-data"
        ) { result in
            if case .success = result { message = L("Data jsou uložená. Teď je naimportujte v druhé aplikaci.") }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                do {
                    let s = try DataTransfer.importFile(at: url)
                    var text = L("Hotovo. Přidáno oblíbených míst: \(s.favorites), zastávek: \(s.departureFavorites).")
                    if s.skippedStops > 0 {
                        text += " " + L("\(s.skippedStops) zastávek se nevešlo, oblíbených zastávek může být nejvýš \(ODFavoritesStore.maxCount).")
                    }
                    message = text
                } catch {
                    message = error.localizedDescription
                }
            case .failure:
                break
            }
        }
        .alert("Přenos dat", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(message ?? "")
        }
    }
}
