import Foundation
import SwiftUI

/// One released version from `CHANGELOG.md` (bundled into the app; the same text is the
/// GitHub Release body). Heading format: `## 1.0.0 — 2026-11-19`, items are `- ` bullets.
struct ChangelogEntry: Identifiable, Hashable {
    let version: String
    let date: String?
    let items: [String]
    var id: String { version }

    var title: String {
        if let date, !date.isEmpty { return L("Verze \(version) · \(date)") }
        return L("Verze \(version)")
    }
}

enum Changelog {
    /// Pure parser (unit-testable).
    static func parse(_ text: String) -> [ChangelogEntry] {
        var entries: [ChangelogEntry] = []
        var version: String?
        var date: String?
        var items: [String] = []

        func flush() {
            if let version { entries.append(ChangelogEntry(version: version, date: date, items: items)) }
            items = []
        }

        for raw in text.replacingOccurrences(of: "\r\n", with: "\n").split(separator: "\n", omittingEmptySubsequences: false) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.hasPrefix("## ") {
                flush()
                let heading = line.dropFirst(3).trimmingCharacters(in: .whitespaces)
                let parts = heading.split(maxSplits: 1, whereSeparator: { $0 == " " || $0 == "\t" })
                version = parts.first.map(String.init)
                var rest = parts.count > 1 ? String(parts[1]).trimmingCharacters(in: .whitespaces) : ""
                for dash in ["—", "–", "-"] where rest.hasPrefix(dash) {
                    rest = String(rest.dropFirst(dash.count)).trimmingCharacters(in: .whitespaces)
                    break
                }
                date = rest.isEmpty ? nil : rest
            } else if version != nil, line.hasPrefix("- ") || line.hasPrefix("* ") {
                let item = String(line.dropFirst(2)).trimmingCharacters(in: .whitespaces)
                // Shared changelog: `[Android]` bullets are hidden here, `[iOS]` keeps its text without the tag.
                if item.hasPrefix("[Android]") { continue }
                if item.hasPrefix("[iOS]") {
                    items.append(String(item.dropFirst("[iOS]".count)).trimmingCharacters(in: .whitespaces))
                } else {
                    items.append(item)
                }
            }
        }
        flush()
        return entries.filter { !$0.items.isEmpty }
    }

    static func load() -> [ChangelogEntry] {
        // Czech phones get CHANGELOG.cs.md, everyone else the English CHANGELOG.md.
        let url = (appIsCzech ? Bundle.main.url(forResource: "CHANGELOG.cs", withExtension: "md") : nil)
            ?? Bundle.main.url(forResource: "CHANGELOG", withExtension: "md")
        guard let url,
              let text = try? String(contentsOf: url, encoding: .utf8)
        else { return [] }
        return parse(text)
    }

    static var currentVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? ""
    }

    /// "1.0" and "1.0.0" are the same release.
    static func normalized(_ version: String) -> String {
        var parts = version.split(separator: ".").map(String.init)
        while parts.count > 1, parts.last == "0" { parts.removeLast() }
        return parts.joined(separator: ".")
    }

    static func entry(for version: String, in entries: [ChangelogEntry]) -> ChangelogEntry? {
        entries.first { normalized($0.version) == normalized(version) }
    }
}

/// Full history (Více → Co je nového) or a single release (the after-update sheet).
struct ChangelogView: View {
    let entries: [ChangelogEntry]

    var body: some View {
        List {
            if entries.isEmpty {
                Text("Seznam změn není v této verzi k dispozici.")
                    .foregroundStyle(.secondary)
            }
            ForEach(entries) { entry in
                Section(entry.title) {
                    ForEach(entry.items, id: \.self) { item in
                        Text(LocalizedStringKey(item))
                            .padding(.vertical, 2)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

/// Shown once after an update, with the notes of the version that was just installed.
struct WhatsNewSheet: View {
    let entry: ChangelogEntry
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ChangelogView(entries: [entry])
                .navigationTitle("Co je nového")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Hotovo") { dismiss() }
                    }
                }
        }
        .presentationDetents([.medium, .large])
    }
}
